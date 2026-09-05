package com.pessoal.agenda.infra.pairing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.pessoal.agenda.repository.DesktopSyncRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class LocalPairingServer implements AutoCloseable {
    private static final int CONTRACT_VERSION = 2;
    private static final int CONTRACT_MIN_VERSION = 1;
    private static final int MAX_BODY_BYTES = 32 * 1024;
    private static final int MAX_SYNC_BODY_BYTES = 256 * 1024;
    private static final Duration SESSION_DURATION = Duration.ofMinutes(5);
    private static final Set<String> REQUEST_FIELDS = Set.of(
            "contract_version", "session_id", "desktop_id", "device_id", "device_name",
            "one_time_code", "device_public_key", "invitation_nonce", "requested_roles");
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "TASKS_READ", "TASKS_WRITE", "CAPTURES_WRITE", "PROTOCOLS_EXECUTE");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DesktopSyncRepository repository;
    private final Clock clock;
    private final InetAddress bindAddress;
    private final int port;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final SyncBatchProcessor syncProcessor;
    private final Consumer<String> diagnostic;
    private final LocalSyncTlsIdentityStore.TlsIdentity configuredIdentity;
    private LocalSyncTlsIdentityStore.TlsIdentity runtimeIdentity;

    private HttpsServer server;
    private ExecutorService executor;
    private Thread expirationThread;
    private long sessionGeneration;
    private String sessionId;
    private String desktopId;
    private String nonce;
    private String code;
    private Instant expiresAt;
    private PendingState pending;
    private final Map<String, SnapshotPagePointer> snapshotTokens = new HashMap<>();

    public LocalPairingServer(DesktopSyncRepository repository, InetAddress bindAddress, int port) {
        this(repository, Clock.systemUTC(), bindAddress, port, ignored -> {}, null);
    }

    public LocalPairingServer(DesktopSyncRepository repository, InetAddress bindAddress, int port,
                              LocalSyncTlsIdentityStore.TlsIdentity identity) {
        this(repository, Clock.systemUTC(), bindAddress, port, ignored -> {}, identity);
    }

    LocalPairingServer(DesktopSyncRepository repository, Clock clock,
                       InetAddress bindAddress, int port) {
        this(repository, clock, bindAddress, port, ignored -> {}, null);
    }

    LocalPairingServer(DesktopSyncRepository repository, Clock clock,
                       InetAddress bindAddress, int port, Consumer<String> diagnostic) {
        this(repository, clock, bindAddress, port, diagnostic, null);
    }

    LocalPairingServer(DesktopSyncRepository repository, Clock clock,
                       InetAddress bindAddress, int port, Consumer<String> diagnostic,
                       LocalSyncTlsIdentityStore.TlsIdentity identity) {
        this.repository = repository;
        this.clock = clock;
        this.bindAddress = bindAddress;
        this.port = port;
        this.syncProcessor = new SyncBatchProcessor(repository);
        this.diagnostic = diagnostic;
        this.configuredIdentity = identity;
    }

    public synchronized PairingSession start() {
        try {
            ensureRunning();
            Instant now = clock.instant();
            expiresAt = now.plus(SESSION_DURATION);
            sessionId = UUID.randomUUID().toString();
            desktopId = repository.desktopId();
            nonce = randomBase64(32);
            code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            pending = null;
            long generation = ++sessionGeneration;
            if (expirationThread != null) expirationThread.interrupt();
            expirationThread = Thread.startVirtualThread(() -> closeAfterExpiration(generation));

            String endpoint = "https://" + bindAddress.getHostAddress() + ":"
                    + server.getAddress().getPort() + "/api/v1/pair/requests";
            String invitation = "agenda://pair?v=" + CONTRACT_VERSION
                    + "&session_id=" + encode(sessionId)
                    + "&desktop_id=" + encode(desktopId)
                    + "&endpoint=" + encode(endpoint)
                    + "&expires_at=" + encode(expiresAt.toString())
                    + "&nonce=" + encode(nonce)
                    + "&fingerprint=" + fingerprint(activeIdentity().certificate());
            return new PairingSession(invitation, code, expiresAt);
        } catch (Exception error) {
            endPairingSession();
            throw new RuntimeException("Não foi possível abrir o pareamento local.", error);
        }
    }

    public synchronized void ensureRunning() {
        if (server != null) return;
        try {
            LocalSyncTlsIdentityStore.TlsIdentity identity = activeIdentity();
            server = HttpsServer.create(new InetSocketAddress(bindAddress, port), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(
                    sslContext(identity.keys(), identity.certificate())));
            server.createContext("/api/v1/pair/requests", this::handleRequest);
            server.createContext("/api/v1/sync/batches", this::handleRequest);
            server.createContext("/api/v1/sync/snapshot", this::handleRequest);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();
        } catch (Exception error) {
            close();
            throw new IllegalStateException("Não foi possível iniciar o sync local.", error);
        }
    }

    public synchronized PendingPairingRequest pendingRequest() {
        if (pending == null || pending.decision != Decision.PENDING || expired()) return null;
        return new PendingPairingRequest(
                pending.requestId, pending.request.deviceId, normalizeName(pending.request.deviceName),
                Set.copyOf(pending.request.requestedRoles), pending.receivedAt);
    }

    public synchronized void approve(String requestId, Set<String> grantedRoles) {
        PendingState state = requirePending(requestId);
        if (grantedRoles == null || grantedRoles.isEmpty()
                || !state.request.requestedRoles.containsAll(grantedRoles)
                || !ALLOWED_ROLES.containsAll(grantedRoles)) {
            throw new IllegalArgumentException("Papéis concedidos inválidos.");
        }
        try {
            byte[] credential = new byte[32];
            RANDOM.nextBytes(credential);
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(credential));
            String encrypted = encryptCredential(credential, state.request.devicePublicKey);
            repository.approveDevice(
                    state.request.deviceId, state.request.deviceName, hash,
                    CONTRACT_MIN_VERSION, state.request.contractVersion, grantedRoles);
            java.util.Arrays.fill(credential, (byte) 0);
            state.response = PairResponse.approved(
                    state.requestId, state.request.deviceId, encrypted, grantedRoles,
                    state.request.contractVersion);
            state.decision = Decision.APPROVED;
        } catch (Exception error) {
            throw new RuntimeException("Não foi possível aprovar o dispositivo.", error);
        }
    }

    public synchronized void reject(String requestId) {
        PendingState state = requirePending(requestId);
        state.decision = Decision.REJECTED;
    }

    private PendingState requirePending(String requestId) {
        if (expired() || pending == null || !constantEquals(pending.requestId, requestId)
                || pending.decision != Decision.PENDING) {
            throw new IllegalStateException("Solicitação de pareamento indisponível.");
        }
        return pending;
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/v1/pair/requests")) handleCreate(exchange);
        else if (path.matches("/api/v1/pair/requests/[0-9a-fA-F-]{36}/complete")) handleComplete(exchange);
        else if (path.equals("/api/v1/sync/batches")) handleSyncBatch(exchange);
        else if (path.equals("/api/v1/sync/snapshot")) handleSnapshot(exchange);
        else respond(exchange, 404, new ErrorResponse("Pareamento recusado."));
    }

    private void handleSyncBatch(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 409, new ErrorResponse("Sincronização indisponível."));
            return;
        }
        String deviceId = authenticate(exchange);
        if (deviceId == null) {
            diagnostic.accept("sync authentication rejected");
            respond(exchange, 401, new ErrorResponse("Sincronização recusada."));
            return;
        }
        try {
            byte[] body = limitedBody(exchange, MAX_SYNC_BODY_BYTES);
            respond(exchange, 200, syncProcessor.process(deviceId, body));
        } catch (Exception error) {
            diagnostic.accept("sync batch rejected: " + error.getClass().getSimpleName()
                    + " at "
                    + java.util.Arrays.stream(error.getStackTrace()).limit(4).toList());
            respond(exchange, 400, new ErrorResponse("Lote de sincronização inválido."));
        }
    }

    private synchronized void handleSnapshot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 409, new ErrorResponse("Sincronização indisponível."));
            return;
        }
        String deviceId = authenticate(exchange);
        if (deviceId == null || !repository.hasRole(deviceId, "TASKS_READ")) {
            respond(exchange, 401, new ErrorResponse("Sincronização recusada."));
            return;
        }
        try {
            String token = queryParameter(exchange, "page_token");
            SnapshotPagePointer pointer;
            if (token == null) {
                DesktopSyncRepository.SnapshotRecord snapshot = repository.refreshSnapshot();
                DesktopSyncRepository.DeviceRecord device = repository.findDevice(deviceId);
                pointer = new SnapshotPagePointer(
                        deviceId, UUID.randomUUID().toString(), snapshot, 0,
                        device == null ? 1 : device.contractMax());
            } else {
                pointer = snapshotTokens.get(token);
                if (pointer == null || !pointer.deviceId.equals(deviceId)) {
                    throw new IllegalArgumentException();
                }
            }
            respond(exchange, 200, snapshotPage(pointer));
        } catch (Exception error) {
            respond(exchange, 400, new ErrorResponse("Snapshot inválido."));
        }
    }

    private synchronized void handleCreate(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod()) || expired() || pending != null) {
            respond(exchange, 409, new ErrorResponse("Pareamento recusado."));
            return;
        }
        try {
            byte[] body = limitedBody(exchange, MAX_BODY_BYTES);
            JsonObject object = gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
            if (object == null || !object.keySet().equals(REQUEST_FIELDS)) throw new IllegalArgumentException();
            PairRequest request = gson.fromJson(object, PairRequest.class);
            validate(request);
            pending = new PendingState(
                    UUID.randomUUID().toString(), randomBase64(32), request, clock.instant());
            respond(exchange, 202, PairResponse.pending(pending.requestId, pending.completionToken));
        } catch (Exception error) {
            respond(exchange, 400, new ErrorResponse("Pareamento recusado."));
        }
    }

    private synchronized void handleComplete(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod()) || expired() || pending == null) {
            respond(exchange, 409, new ErrorResponse("Pareamento recusado."));
            return;
        }
        String requestId = exchange.getRequestURI().getPath().split("/")[5];
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!constantEquals(pending.requestId, requestId)
                || !constantEquals("Pairing " + pending.completionToken, authorization)) {
            respond(exchange, 400, new ErrorResponse("Pareamento recusado."));
            return;
        }
        switch (pending.decision) {
            case PENDING -> respond(exchange, 202,
                    PairResponse.pending(pending.requestId, pending.completionToken));
            case APPROVED -> respond(exchange, 200, pending.response);
            case REJECTED -> respond(exchange, 409, new ErrorResponse("Pareamento recusado."));
        }
    }

    private void validate(PairRequest request) {
        if (request == null || request.contractVersion < CONTRACT_MIN_VERSION
                || request.contractVersion > CONTRACT_VERSION
                || !constantEquals(sessionId, request.sessionId)
                || !constantEquals(desktopId, request.desktopId)
                || !constantEquals(code, request.oneTimeCode)
                || !constantEquals(nonce, request.invitationNonce)
                || request.requestedRoles == null || request.requestedRoles.isEmpty()
                || !ALLOWED_ROLES.containsAll(request.requestedRoles)) throw new IllegalArgumentException();
        UUID.fromString(request.deviceId);
        normalizeName(request.deviceName);
        byte[] publicKey = Base64.getUrlDecoder().decode(request.devicePublicKey);
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException();
            }
        } catch (Exception error) {
            throw new IllegalArgumentException(error);
        }
    }

    private byte[] limitedBody(HttpExchange exchange, int maximum) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(maximum + 1);
        if (body.length > maximum) throw new IllegalArgumentException();
        return body;
    }

    private String authenticate(HttpExchange exchange) {
        String deviceId = exchange.getRequestHeaders().getFirst("X-Agenda-Device");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (deviceId == null || authorization == null
                || !authorization.startsWith("AgendaCredential ")) return null;
        byte[] credential = null;
        try {
            UUID.fromString(deviceId);
            credential = Base64.getUrlDecoder().decode(
                    authorization.substring("AgendaCredential ".length()));
            return repository.credentialsMatch(deviceId, credential) ? deviceId : null;
        } catch (RuntimeException error) {
            return null;
        } finally {
            if (credential != null) java.util.Arrays.fill(credential, (byte) 0);
        }
    }

    private JsonObject snapshotPage(SnapshotPagePointer pointer) {
        List<String> allTasks = pointer.snapshot.taskJson();
        List<String> allProtocols = pointer.snapshot.protocolJson();
        int taskStart = pointer.page * 200;
        int protocolStart = pointer.page * 50;
        int taskEnd = Math.min(taskStart + 200, allTasks.size());
        int protocolEnd = Math.min(protocolStart + 50, allProtocols.size());
        boolean hasMore = taskEnd < allTasks.size() || protocolEnd < allProtocols.size();

        JsonArray tasks = new JsonArray();
        if (taskStart < allTasks.size()) {
            allTasks.subList(taskStart, taskEnd).forEach(value ->
                    tasks.add(gson.fromJson(value, JsonObject.class)));
        }
        JsonArray protocols = new JsonArray();
        if (protocolStart < allProtocols.size()) {
            allProtocols.subList(protocolStart, protocolEnd).forEach(value ->
                    protocols.add(gson.fromJson(value, JsonObject.class)));
        }
        String nextToken = null;
        if (hasMore) {
            nextToken = randomBase64(32);
            snapshotTokens.put(nextToken, new SnapshotPagePointer(
                    pointer.deviceId, pointer.snapshotId, pointer.snapshot, pointer.page + 1,
                    pointer.contractVersion));
        }
        JsonObject result = new JsonObject();
        result.addProperty("snapshot_id", pointer.snapshotId);
        result.addProperty("server_cursor", pointer.snapshot.serverCursor());
        result.addProperty("page", pointer.page + 1);
        result.addProperty("has_more", hasMore);
        if (nextToken == null) result.add("next_page_token", com.google.gson.JsonNull.INSTANCE);
        else result.addProperty("next_page_token", nextToken);
        if (pointer.contractVersion == 1) {
            for (JsonElement element : tasks) {
                JsonObject task = element.getAsJsonObject();
                task.remove("notes"); task.remove("due_date"); task.remove("priority"); task.remove("checklist");
            }
        }
        result.add("tasks", tasks);
        result.add("protocols", protocols);
        return result;
    }

    private static String queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name)) {
                return parts.length == 2
                        ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = gson.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private boolean expired() { return expiresAt == null || !clock.instant().isBefore(expiresAt); }

    public synchronized void endPairingSession() {
        sessionGeneration++;
        if (expirationThread != null && expirationThread != Thread.currentThread()) {
            expirationThread.interrupt();
        }
        expirationThread = null;
        sessionId = null;
        nonce = null;
        code = null;
        expiresAt = null;
        pending = null;
    }

    @Override
    public synchronized void close() {
        endPairingSession();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
        snapshotTokens.clear();
    }

    private void closeAfterExpiration(long generation) {
        try {
            Thread.sleep(SESSION_DURATION);
            synchronized (this) {
                if (generation == sessionGeneration) endPairingSession();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private LocalSyncTlsIdentityStore.TlsIdentity activeIdentity() throws Exception {
        if (configuredIdentity != null) return configuredIdentity;
        if (runtimeIdentity != null) return runtimeIdentity;
        Instant now = clock.instant();
        KeyPair keys = ecKeyPair();
        runtimeIdentity = new LocalSyncTlsIdentityStore.TlsIdentity(
                keys, certificate(keys, bindAddress, now, now.plus(Duration.ofDays(365))));
        return runtimeIdentity;
    }

    private static String encryptCredential(byte[] credential, String encodedPublicKey) throws Exception {
        var key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getUrlDecoder().decode(encodedPublicKey)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(credential));
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(KeyPair keys, InetAddress address,
                                               Instant from, Instant until) throws Exception {
        X500Name name = new X500Name("CN=Agenda Pairing");
        var builder = new JcaX509v3CertificateBuilder(
                name, new BigInteger(128, RANDOM), java.util.Date.from(from.minusSeconds(30)),
                java.util.Date.from(until.plusSeconds(30)), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.iPAddress, address.getHostAddress())));
        var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static SSLContext sslContext(KeyPair keys, X509Certificate certificate) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        char[] password = randomBase64(24).toCharArray();
        keyStore.setKeyEntry("session", keys.getPrivate(), password,
                new java.security.cert.Certificate[]{certificate});
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keyStore, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, RANDOM);
        return context;
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static String randomBase64(int byteCount) {
        byte[] value = new byte[byteCount];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static boolean constantEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeName(String value) {
        if (value == null) throw new IllegalArgumentException();
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > 100) throw new IllegalArgumentException();
        return normalized;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private enum Decision { PENDING, APPROVED, REJECTED }

    private static final class PendingState {
        final String requestId;
        final String completionToken;
        final PairRequest request;
        final Instant receivedAt;
        Decision decision = Decision.PENDING;
        PairResponse response;
        PendingState(String requestId, String completionToken, PairRequest request, Instant receivedAt) {
            this.requestId = requestId;
            this.completionToken = completionToken;
            this.request = request;
            this.receivedAt = receivedAt;
        }
    }

    private record SnapshotPagePointer(
            String deviceId, String snapshotId,
            DesktopSyncRepository.SnapshotRecord snapshot, int page, int contractVersion) {}

    private static final class PairRequest {
        @SerializedName("contract_version") int contractVersion;
        @SerializedName("session_id") String sessionId;
        @SerializedName("desktop_id") String desktopId;
        @SerializedName("device_id") String deviceId;
        @SerializedName("device_name") String deviceName;
        @SerializedName("one_time_code") String oneTimeCode;
        @SerializedName("device_public_key") String devicePublicKey;
        @SerializedName("invitation_nonce") String invitationNonce;
        @SerializedName("requested_roles") Set<String> requestedRoles;
    }

    private record PairResponse(
            @SerializedName("request_id") String requestId,
            @SerializedName("status") String status,
            @SerializedName("retry_after_seconds") Integer retryAfterSeconds,
            @SerializedName("completion_token") String completionToken,
            @SerializedName("device_id") String deviceId,
            @SerializedName("contract_min") Integer contractMin,
            @SerializedName("contract_max") Integer contractMax,
            @SerializedName("encrypted_credential") String encryptedCredential,
            @SerializedName("granted_roles") Set<String> grantedRoles) {
        static PairResponse pending(String requestId, String token) {
            return new PairResponse(requestId, "PENDING", 2, token,
                    null, null, null, null, Set.of());
        }
        static PairResponse approved(String requestId, String deviceId,
                                     String credential, Set<String> roles, int contractMax) {
            return new PairResponse(requestId, "APPROVED", null, null,
                    deviceId, CONTRACT_MIN_VERSION, contractMax, credential, Set.copyOf(roles));
        }
    }

    private record ErrorResponse(@SerializedName("error") String error) {}
}
