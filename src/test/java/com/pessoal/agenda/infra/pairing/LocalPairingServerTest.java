package com.pessoal.agenda.infra.pairing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.DesktopSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPairingServerTest {
    private static final String DEVICE_ID = "10000000-0000-4000-8000-000000000050";

    @TempDir
    Path tempDir;

    private DesktopSyncRepository repository;
    private Database database;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("pairing-test.db"));
        database.runMigrations();
        repository = new DesktopSyncRepository(database);
    }

    @Test
    void credentialIsIssuedOnlyAfterDesktopApproval() throws Exception {
        try (var server = server()) {
            PairingSession session = server.start();
            Map<String, String> invitation = invitation(session.invitation());
            KeyPair keys = rsaKeys();

            JsonObject pending = createRequest(session, invitation, keys, session.oneTimeCode());
            assertEquals("PENDING", pending.get("status").getAsString());
            assertTrue(pending.has("encrypted_credential"));
            assertTrue(pending.get("encrypted_credential").isJsonNull());
            assertNull(repository.findDevice(DEVICE_ID));
            assertNotNull(server.pendingRequest());

            String requestId = pending.get("request_id").getAsString();
            server.approve(requestId, Set.of("TASKS_READ", "CAPTURES_WRITE"));
            JsonObject approved = complete(
                    invitation.get("endpoint"), invitation.get("fingerprint"), requestId,
                    pending.get("completion_token").getAsString(), 200);
            JsonObject repeated = complete(
                    invitation.get("endpoint"), invitation.get("fingerprint"), requestId,
                    pending.get("completion_token").getAsString(), 200);

            assertEquals("APPROVED", approved.get("status").getAsString());
            assertEquals(approved, repeated);
            assertTrue(approved.has("completion_token"));
            assertTrue(approved.get("completion_token").isJsonNull());
            assertEquals(32, decryptCredential(
                    approved.get("encrypted_credential").getAsString(), keys).length);
            assertEquals("ACTIVE", repository.findDevice(DEVICE_ID).status());
            assertEquals(Set.of("TASKS_READ", "CAPTURES_WRITE"),
                    repository.findDevice(DEVICE_ID).roles());
        }
    }

    @Test
    void rejectedRequestNeverRegistersDevice() throws Exception {
        try (var server = server()) {
            PairingSession session = server.start();
            Map<String, String> invitation = invitation(session.invitation());
            JsonObject pending = createRequest(session, invitation, rsaKeys(), session.oneTimeCode());
            String requestId = pending.get("request_id").getAsString();

            server.reject(requestId);
            complete(invitation.get("endpoint"), invitation.get("fingerprint"), requestId,
                    pending.get("completion_token").getAsString(), 409);

            assertNull(repository.findDevice(DEVICE_ID));
        }
    }

    @Test
    void incorrectCodeReturnsGenericFailure() throws Exception {
        try (var server = server()) {
            PairingSession session = server.start();
            Map<String, String> invitation = invitation(session.invitation());
            String wrong = session.oneTimeCode().equals("000000") ? "999999" : "000000";

            JsonObject error = createRequest(session, invitation, rsaKeys(), wrong);

            assertEquals("Pareamento recusado.", error.get("error").getAsString());
            assertNull(server.pendingRequest());
            assertNull(repository.findDevice(DEVICE_ID));
        }
    }

    @Test
    void incorrectFingerprintStopsTlsHandshake() throws Exception {
        try (var server = server()) {
            PairingSession session = server.start();
            String endpoint = invitation(session.invitation()).get("endpoint");
            HttpsURLConnection connection = connection(endpoint, "00".repeat(32));

            assertThrows(javax.net.ssl.SSLHandshakeException.class, connection::getResponseCode);
            assertNull(server.pendingRequest());
        }
    }

    @Test
    void approvedCredentialPushesIdempotentlyAndReadsPinnedSnapshot() throws Exception {
        insertFictitiousTasks(201);
        try (var server = server()) {
            PairingSession session = server.start();
            Map<String, String> invitation = invitation(session.invitation());
            KeyPair keys = rsaKeys();
            JsonObject pending = createRequest(session, invitation, keys, session.oneTimeCode());
            server.approve(pending.get("request_id").getAsString(),
                    Set.of("TASKS_READ", "CAPTURES_WRITE"));
            JsonObject approved = complete(
                    invitation.get("endpoint"), invitation.get("fingerprint"),
                    pending.get("request_id").getAsString(),
                    pending.get("completion_token").getAsString(), 200);
            byte[] credential = decryptCredential(
                    approved.get("encrypted_credential").getAsString(), keys);
            String syncBase = invitation.get("endpoint").replace("/pair/requests", "/sync");

            JsonObject first = authenticatedJson(
                    syncBase + "/batches", invitation.get("fingerprint"), credential,
                    syncBatch(), "POST", 200);
            JsonObject replay = authenticatedJson(
                    syncBase + "/batches", invitation.get("fingerprint"), credential,
                    syncBatch(), "POST", 200);
            JsonObject snapshot = authenticatedJson(
                    syncBase + "/snapshot", invitation.get("fingerprint"), credential,
                    null, "GET", 200);
            JsonObject secondPage = authenticatedJson(
                    syncBase + "/snapshot?page_token="
                            + snapshot.get("next_page_token").getAsString(),
                    invitation.get("fingerprint"), credential, null, "GET", 200);
            JsonObject repeatedSecondPage = authenticatedJson(
                    syncBase + "/snapshot?page_token="
                            + snapshot.get("next_page_token").getAsString(),
                    invitation.get("fingerprint"), credential, null, "GET", 200);

            assertEquals("APPLIED", first.getAsJsonArray("results").get(0)
                    .getAsJsonObject().get("status").getAsString());
            assertEquals(first, replay);
            assertEquals(1, database.queryInt("SELECT COUNT(*) FROM inbox_captures"));
            assertEquals(200, snapshot.getAsJsonArray("tasks").size());
            assertTrue(snapshot.get("has_more").getAsBoolean());
            assertEquals(1, secondPage.getAsJsonArray("tasks").size());
            assertFalse(secondPage.get("has_more").getAsBoolean());
            assertEquals(snapshot.get("snapshot_id"), secondPage.get("snapshot_id"));
            assertEquals(secondPage, repeatedSecondPage);
            java.util.Arrays.fill(credential, (byte) 0);
        }
    }

    @Test
    void expiredSessionCannotCompletePendingRequest() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
        try (var server = server(clock)) {
            PairingSession session = server.start();
            Map<String, String> invitation = invitation(session.invitation());
            JsonObject pending = createRequest(session, invitation, rsaKeys(), session.oneTimeCode());

            clock.advanceSeconds(301);

            complete(invitation.get("endpoint"), invitation.get("fingerprint"),
                    pending.get("request_id").getAsString(),
                    pending.get("completion_token").getAsString(), 409);
            assertNull(server.pendingRequest());
            assertNull(repository.findDevice(DEVICE_ID));
        }
    }

    @Test
    void newSessionInvalidatesOldTokenAndAllowsReconnect() throws Exception {
        try (var server = server()) {
            PairingSession firstSession = server.start();
            Map<String, String> firstInvitation = invitation(firstSession.invitation());
            JsonObject firstPending = createRequest(
                    firstSession, firstInvitation, rsaKeys(), firstSession.oneTimeCode());

            PairingSession secondSession = server.start();
            Map<String, String> secondInvitation = invitation(secondSession.invitation());

            assertFalse(firstInvitation.get("session_id").equals(secondInvitation.get("session_id")));
            assertFalse(firstInvitation.get("fingerprint").equals(secondInvitation.get("fingerprint")));
            complete(secondInvitation.get("endpoint"), secondInvitation.get("fingerprint"),
                    firstPending.get("request_id").getAsString(),
                    firstPending.get("completion_token").getAsString(), 409);

            JsonObject secondPending = createRequest(
                    secondSession, secondInvitation, rsaKeys(), secondSession.oneTimeCode());
            server.approve(secondPending.get("request_id").getAsString(), Set.of("TASKS_READ"));
            JsonObject approved = complete(
                    secondInvitation.get("endpoint"), secondInvitation.get("fingerprint"),
                    secondPending.get("request_id").getAsString(),
                    secondPending.get("completion_token").getAsString(), 200);

            assertEquals("APPROVED", approved.get("status").getAsString());
            assertEquals("ACTIVE", repository.findDevice(DEVICE_ID).status());
        }
    }

    private LocalPairingServer server() throws Exception {
        return new LocalPairingServer(repository, InetAddress.getByName("127.0.0.1"), 0);
    }

    private LocalPairingServer server(Clock clock) throws Exception {
        return new LocalPairingServer(repository, clock, InetAddress.getByName("127.0.0.1"), 0);
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private JsonObject createRequest(PairingSession session, Map<String, String> invitation,
                                     KeyPair keys, String code) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("contract_version", 1);
        request.put("session_id", invitation.get("session_id"));
        request.put("desktop_id", invitation.get("desktop_id"));
        request.put("device_id", DEVICE_ID);
        request.put("device_name", "Android fictício");
        request.put("one_time_code", code);
        request.put("device_public_key", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(keys.getPublic().getEncoded()));
        request.put("invitation_nonce", invitation.get("nonce"));
        request.put("requested_roles", Set.of("TASKS_READ", "CAPTURES_WRITE"));

        HttpsURLConnection connection = connection(
                invitation.get("endpoint"), invitation.get("fingerprint"));
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.getOutputStream().write(new Gson().toJson(request).getBytes(StandardCharsets.UTF_8));
        int status = connection.getResponseCode();
        byte[] body = (status < 400 ? connection.getInputStream() : connection.getErrorStream()).readAllBytes();
        return new Gson().fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
    }

    private JsonObject complete(String endpoint, String fingerprint, String requestId,
                                String token, int expectedStatus) throws Exception {
        HttpsURLConnection connection = connection(
                endpoint + "/" + requestId + "/complete", fingerprint);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(0);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Pairing " + token);
        int status = connection.getResponseCode();
        assertEquals(expectedStatus, status);
        byte[] body = (status < 400 ? connection.getInputStream() : connection.getErrorStream()).readAllBytes();
        return new Gson().fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static KeyPair rsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] decryptCredential(String encoded, KeyPair keys) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keys.getPrivate(), new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
        return cipher.doFinal(Base64.getUrlDecoder().decode(encoded));
    }

    private JsonObject syncBatch() throws Exception {
        String operationId = "10000000-0000-4000-8000-000000000051";
        String captureId = "10000000-0000-4000-8000-000000000052";
        JsonObject payload = new JsonObject();
        payload.addProperty("capture_id", captureId);
        payload.addProperty("text", "Captura móvel fictícia");
        payload.addProperty("created_at", "2026-09-01T12:00:00Z");
        String payloadJson = new Gson().toJson(payload);
        String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payloadJson.getBytes(StandardCharsets.UTF_8)));

        JsonObject operation = new JsonObject();
        operation.addProperty("operation_id", operationId);
        operation.addProperty("device_id", DEVICE_ID);
        operation.addProperty("sequence", 1);
        operation.addProperty("contract_version", 1);
        operation.addProperty("entity_type", "capture");
        operation.addProperty("entity_id", captureId);
        operation.addProperty("command_type", "CAPTURE_CREATED");
        operation.addProperty("occurred_at", "2026-09-01T12:00:00Z");
        operation.addProperty("time_zone", "America/Manaus");
        operation.add("payload", payload);
        operation.addProperty("payload_hash", hash);
        operation.add("base_revision", com.google.gson.JsonNull.INSTANCE);
        JsonObject batch = new JsonObject();
        batch.addProperty("contract_version", 1);
        batch.addProperty("device_id", DEVICE_ID);
        batch.addProperty("last_server_cursor", 0);
        com.google.gson.JsonArray operations = new com.google.gson.JsonArray();
        operations.add(operation);
        batch.add("operations", operations);
        return batch;
    }

    private void insertFictitiousTasks(int count) throws Exception {
        try (var connection = database.connect();
             var statement = connection.prepareStatement(
                     "INSERT INTO tasks(title, due_date) VALUES(?, '2026-09-01')")) {
            for (int index = 1; index <= count; index++) {
                statement.setString(1, "Tarefa desktop fictícia " + index);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private JsonObject authenticatedJson(String endpoint, String fingerprint, byte[] credential,
                                         JsonObject body, String method, int expectedStatus)
            throws Exception {
        HttpsURLConnection connection = connection(endpoint, fingerprint);
        connection.setRequestMethod(method);
        connection.setRequestProperty("X-Agenda-Device", DEVICE_ID);
        connection.setRequestProperty("Authorization", "AgendaCredential "
                + Base64.getUrlEncoder().withoutPadding().encodeToString(credential));
        if (body != null) {
            byte[] bytes = new Gson().toJson(body).getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.getOutputStream().write(bytes);
        }
        int status = connection.getResponseCode();
        assertEquals(expectedStatus, status);
        byte[] bytes = (status < 400 ? connection.getInputStream() : connection.getErrorStream())
                .readAllBytes();
        return new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static HttpsURLConnection connection(String endpoint, String fingerprint) throws Exception {
        byte[] expected = java.util.HexFormat.of().parseHex(fingerprint);
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                try {
                    byte[] actual = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                    if (!MessageDigest.isEqual(expected, actual)) {
                        throw new java.security.cert.CertificateException("fingerprint mismatch");
                    }
                } catch (java.security.cert.CertificateException error) {
                    throw error;
                } catch (Exception error) {
                    throw new java.security.cert.CertificateException(error);
                }
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trust}, null);
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setSSLSocketFactory(context.getSocketFactory());
        connection.setHostnameVerifier((host, session) -> true);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        return connection;
    }

    private static Map<String, String> invitation(String raw) {
        Map<String, String> values = new HashMap<>();
        for (String pair : URI.create(raw).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }
}
