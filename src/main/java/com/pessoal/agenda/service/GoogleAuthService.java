package com.pessoal.agenda.service;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.Duration;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Gerencia a autenticação OAuth 2.0 com o Google (fluxo Desktop App).
 *
 * Credenciais lidas de: ~/.agenda/google-credentials.json
 * Tokens armazenados em: ~/.agenda/google-tokens.json
 *
 * Fluxo:
 *   1. Gera URL de autorização
 *   2. Abre o navegador
 *   3. Escuta em porta local (ServerSocket) para capturar o callback
 *   4. Troca o código por access_token + refresh_token
 *   5. Salva tokens localmente
 */
public class GoogleAuthService {

    private static final String CREDENTIALS_PATH =
            System.getProperty("user.home") + "/.agenda/google-credentials.json";
    private static final String TOKENS_PATH =
            System.getProperty("user.home") + "/.agenda/google-tokens.json";

    public static final String TASKS_SCOPE =
            "https://www.googleapis.com/auth/tasks";
    public static final String DRIVE_APPDATA_SCOPE =
            "https://www.googleapis.com/auth/drive.appdata";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    // Credenciais lidas do arquivo
    private String clientId;
    private String clientSecret;
    private String tokenUri;
    private String authUri;

    // Tokens em memória
    private String accessToken;
    private String refreshToken;
    private long   expiresAt; // epoch seconds
    private final Set<String> grantedScopes = new LinkedHashSet<>();

    private static GoogleAuthService INSTANCE;

    private GoogleAuthService() {
        loadCredentials();
        loadTokens();
    }

    public static synchronized GoogleAuthService getInstance() {
        if (INSTANCE == null) INSTANCE = new GoogleAuthService();
        return INSTANCE;
    }

    // ── Estado ──────────────────────────────────────────────────────────────

    public boolean isAuthorized() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public synchronized boolean hasDriveAppDataScope() {
        return isAuthorized() && grantedScopes.contains(DRIVE_APPDATA_SCOPE);
    }

    public boolean hasValidCredentials() {
        return notBlank(clientId) && notBlank(clientSecret)
                && validHttpUri(tokenUri) && validHttpUri(authUri);
    }

    /** Revoga a autorização e remove tokens locais. */
    public void revoke() throws IOException {
        accessToken  = null;
        refreshToken = null;
        expiresAt    = 0;
        grantedScopes.clear();
        Files.deleteIfExists(Paths.get(TOKENS_PATH));
    }

    // ── Token de acesso ──────────────────────────────────────────────────────

    /**
     * Retorna um access token válido.
     * Refresca automaticamente se expirado.
     * @throws IllegalStateException se não autorizado
     */
    public synchronized String getAccessToken() throws IOException, InterruptedException {
        if (!isAuthorized()) throw new IllegalStateException("Não autorizado. Realize a conexão primeiro.");
        if (!hasValidCredentials()) {
            throw GoogleSyncException.configuration(
                    "As credenciais Google estão ausentes ou inválidas.");
        }
        long now = Instant.now().getEpochSecond();
        if (accessToken == null || now >= expiresAt - 60) {
            refreshAccessToken();
        }
        return accessToken;
    }

    public synchronized void invalidateAccessToken() {
        accessToken = null;
        expiresAt = 0;
    }

    // ── Fluxo de autorização ────────────────────────────────────────────────

    /**
     * Inicia o fluxo de autorização OAuth 2.0.
     * Abre o navegador e aguarda o callback.
     *
     * @param progressCallback callback chamado com mensagens de progresso (para UI)
     * @throws Exception em caso de erro
     */
    public void authorize(Consumer<String> progressCallback) throws Exception {
        authorize(progressCallback, null, true);
    }

    /**
     * Inicia o OAuth permitindo que a interface exponha a URL e decida se deve
     * abrir o navegador padrão. A URL sempre solicita ao Google a escolha da conta.
     */
    public void authorize(Consumer<String> progressCallback,
                          Consumer<String> authorizationUrlCallback,
                          boolean openBrowser) throws Exception {
        newAuthorizationSession().authorize(
                progressCallback, authorizationUrlCallback, openBrowser);
    }

    public AuthorizationSession newAuthorizationSession() {
        return new AuthorizationSession(Set.of(TASKS_SCOPE));
    }

    public AuthorizationSession newAuthorizationSession(Set<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um escopo OAuth.");
        }
        return new AuthorizationSession(Set.copyOf(requestedScopes));
    }

    private void authorize(Consumer<String> progressCallback,
                           Consumer<String> authorizationUrlCallback,
                           boolean openBrowser,
                           AuthorizationSession session) throws Exception {
        if (!hasValidCredentials()) {
            throw new IllegalStateException(
                "Credenciais não encontradas em: " + CREDENTIALS_PATH);
        }

        try (ServerSocket callbackServer = new ServerSocket(0)) {
            session.attach(callbackServer);
            String redirectUri = "http://localhost:" + callbackServer.getLocalPort();
            String state = newAuthorizationState();
            String authUrl = buildAuthorizationUrl(
                    authUri, clientId, redirectUri, state, session.requestedScopes);

            if (authorizationUrlCallback != null) {
                authorizationUrlCallback.accept(authUrl);
            }

            if (openBrowser) {
                if (progressCallback != null) {
                    progressCallback.accept("Abrindo navegador; o link também está disponível para copiar...");
                }
                try {
                    if (!Desktop.isDesktopSupported()) {
                        throw new UnsupportedOperationException("Desktop não suportado");
                    }
                    Desktop.getDesktop().browse(new URI(authUrl));
                } catch (Exception browserError) {
                    if (authorizationUrlCallback == null) {
                        throw new UnsupportedOperationException(
                                "Não foi possível abrir o navegador para autorização.", browserError);
                    }
                    if (progressCallback != null) {
                        progressCallback.accept("Navegador não abriu. Cole o link copiado em outro navegador.");
                    }
                }
            } else if (progressCallback != null) {
                progressCallback.accept("Link copiado. Cole-o no navegador da conta Google correta.");
            }

            if (progressCallback != null) progressCallback.accept("Aguardando autorização do Google...");
            String code = waitForAuthCode(callbackServer, state, session);

            session.throwIfCancelled();
            if (progressCallback != null) progressCallback.accept("Trocando código por tokens...");
            exchangeCodeForTokens(code, redirectUri, session.requestedScopes);
        } finally {
            session.detach();
        }

        if (progressCallback != null) progressCallback.accept("Conectado com sucesso!");
    }

    static String buildAuthorizationUrl(String authUri, String clientId,
                                        String redirectUri, String state) {
        return buildAuthorizationUrl(authUri, clientId, redirectUri, state,
                Set.of(TASKS_SCOPE));
    }

    static String buildAuthorizationUrl(String authUri, String clientId,
                                        String redirectUri, String state,
                                        Set<String> scopes) {
        String scope = String.join(" ", new TreeSet<>(scopes));
        return authUri + "?"
                + "client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(scope)
                + "&access_type=offline"
                + "&prompt=" + encode("select_account consent")
                + "&include_granted_scopes=true"
                + "&state=" + encode(state);
    }

    private static String newAuthorizationState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private String waitForAuthCode(ServerSocket server, String expectedState,
                                   AuthorizationSession session) throws Exception {
        server.setSoTimeout(120_000); // 2 minutos
        try (Socket client = server.accept()) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String code = extractAuthorizationCode(reader.readLine(), expectedState);
            writeAuthorizationSuccess(client);
            return code;
        } catch (SocketException error) {
            if (session.isCancelled()) {
                throw new CancellationException("Autorização cancelada pelo usuário.");
            }
            throw error;
        }
    }

    public final class AuthorizationSession {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<ServerSocket> callbackServer = new AtomicReference<>();
        private final Set<String> requestedScopes;

        private AuthorizationSession(Set<String> requestedScopes) {
            this.requestedScopes = requestedScopes;
        }

        public void authorize(Consumer<String> progressCallback,
                              Consumer<String> authorizationUrlCallback,
                              boolean openBrowser) throws Exception {
            GoogleAuthService.this.authorize(
                    progressCallback, authorizationUrlCallback, openBrowser, this);
        }

        public void cancel() {
            cancelled.set(true);
            closeQuietly(callbackServer.getAndSet(null));
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        void attach(ServerSocket server) {
            if (!callbackServer.compareAndSet(null, server)) {
                closeQuietly(server);
                throw new IllegalStateException("Sessão de autorização já iniciada.");
            }
            if (cancelled.get()) {
                closeQuietly(callbackServer.getAndSet(null));
                throw new CancellationException("Autorização cancelada pelo usuário.");
            }
        }

        void throwIfCancelled() {
            if (cancelled.get()) {
                throw new CancellationException("Autorização cancelada pelo usuário.");
            }
        }

        private void detach() {
            callbackServer.set(null);
        }

        private void closeQuietly(ServerSocket server) {
            if (server == null) return;
            try {
                server.close();
            } catch (IOException ignored) {
                // O fechamento é apenas o sinal cooperativo de cancelamento.
            }
        }
    }

    static String extractAuthorizationCode(String requestLine, String expectedState) throws IOException {
        if (requestLine == null) {
            throw new IOException("O Google não retornou um código de autorização.");
        }
        String[] requestParts = requestLine.split(" ", 3);
        if (requestParts.length < 2 || !requestParts[1].contains("?")) {
            throw new IOException("O Google não retornou um código de autorização.");
        }

        String code = null;
        String returnedState = null;
        String query = requestParts[1].substring(requestParts[1].indexOf('?') + 1);
        for (String param : query.split("&")) {
            if (param.startsWith("code=")) {
                code = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
            } else if (param.startsWith("state=")) {
                returnedState = URLDecoder.decode(param.substring(6), StandardCharsets.UTF_8);
            }
        }
        if (!expectedState.equals(returnedState)) {
            throw new IOException("Retorno de autorização inválido. Inicie a conexão novamente.");
        }
        if (code == null || code.isBlank()) {
            throw new IOException("O Google não retornou um código de autorização.");
        }
        return code;
    }

    private static void writeAuthorizationSuccess(Socket client) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<title>Autorizado</title></head><body style='font-family:sans-serif;"
                + "text-align:center;padding:60px;'>"
                + "<h2 style='color:#1a73e8'>Conexão realizada com sucesso!</h2>"
                + "<p>Você pode fechar esta aba e voltar para a Agenda Científica.</p>"
                + "</body></html>";
        String response = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + html.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "Connection: close\r\n\r\n" + html;
        client.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
    }

    private void exchangeCodeForTokens(String code, String redirectUri,
                                       Set<String> requestedScopes)
            throws IOException, InterruptedException {
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        String response = post(tokenUri, body);
        parseAndSaveTokens(response, requestedScopes);
    }

    private synchronized void refreshAccessToken() throws IOException, InterruptedException {
        if (refreshToken == null) throw new IllegalStateException("Sem refresh token.");
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        String response;
        try {
            response = post(tokenUri, body);
        } catch (GoogleSyncException error) {
            if (error.kind() == GoogleSyncException.Kind.AUTHENTICATION) {
                clearTokens();
            }
            throw error;
        }
        requireTokenResponse(response);
        // Refresh response: access_token + expires_in (sem new refresh_token geralmente)
        String newAccess  = SimpleJson.str(response, "access_token");
        long   expiresIn  = SimpleJson.num(response, "expires_in");
        String newRefresh = SimpleJson.str(response, "refresh_token");
        if (newAccess == null) throw GoogleSyncException.invalidResponse();
        accessToken = newAccess;
        expiresAt   = Instant.now().getEpochSecond() + (expiresIn > 0 ? expiresIn : 3600);
        if (newRefresh != null && !newRefresh.isBlank()) refreshToken = newRefresh;
        saveTokens();
    }

    private synchronized void parseAndSaveTokens(String response,
                                                 Set<String> requestedScopes) throws IOException {
        requireTokenResponse(response);
        String newAccess = SimpleJson.str(response, "access_token");
        String newRefresh = SimpleJson.str(response, "refresh_token");
        long expiresIn = SimpleJson.num(response, "expires_in");
        if (newAccess == null || (!notBlank(newRefresh) && !notBlank(refreshToken))) {
            throw GoogleSyncException.invalidResponse();
        }
        accessToken = newAccess;
        if (notBlank(newRefresh)) refreshToken = newRefresh;
        Set<String> returnedScopes = parseScopes(SimpleJson.str(response, "scope"));
        grantedScopes.addAll(returnedScopes.isEmpty() ? requestedScopes : returnedScopes);
        expiresAt = Instant.now().getEpochSecond() + (expiresIn > 0 ? expiresIn : 3600);
        saveTokens();
    }

    private void saveTokens() throws IOException {
        Path path = Paths.get(TOKENS_PATH);
        String json = "{\"access_token\":\"" + accessToken + "\","
                    + "\"refresh_token\":\"" + refreshToken + "\","
                    + "\"expires_at\":" + expiresAt + ","
                    + "\"scopes\":\"" + String.join(" ", new TreeSet<>(grantedScopes)) + "\"}";
        writePrivateFile(path, json);
    }

    private void loadTokens() {
        try {
            Path path = Paths.get(TOKENS_PATH);
            if (!Files.exists(path)) return;
            restrictPrivateFile(path);
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (!SimpleJson.isStructurallyValid(json)) return;
            accessToken  = SimpleJson.str(json, "access_token");
            refreshToken = SimpleJson.str(json, "refresh_token");
            expiresAt    = SimpleJson.num(json, "expires_at");
            grantedScopes.clear();
            String persistedScopes = SimpleJson.str(json, "scopes");
            if (persistedScopes == null && notBlank(refreshToken)) {
                // Tokens anteriores à integração Drive autorizavam somente o Tasks.
                grantedScopes.add(TASKS_SCOPE);
            } else {
                grantedScopes.addAll(parseScopes(persistedScopes));
            }
        } catch (IOException e) {
            // tokens inexistentes ou corrompidos – ignorar
        }
    }

    private void loadCredentials() {
        try {
            Path path = Paths.get(CREDENTIALS_PATH);
            if (!Files.exists(path)) return;
            restrictPrivateFile(path);
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (!SimpleJson.isStructurallyValid(json)) return;
            // JSON: {"installed":{"client_id":"...","client_secret":"...","auth_uri":"...","token_uri":"..."}}
            String installed = extractObject(json, "installed");
            if (installed == null) installed = json; // fallback
            clientId     = SimpleJson.str(installed, "client_id");
            clientSecret = SimpleJson.str(installed, "client_secret");
            tokenUri     = SimpleJson.str(installed, "token_uri");
            authUri      = SimpleJson.str(installed, "auth_uri");
        } catch (IOException e) {
            System.err.println("[GoogleAuth] Não foi possível ler as credenciais locais.");
        }
    }

    /** Extrai o conteúdo do objeto para "key": {...} */
    private static String extractObject(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        idx = json.indexOf('{', idx);
        if (idx < 0) return null;
        int depth = 0, start = idx;
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if      (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return json.substring(start, idx + 1); }
            idx++;
        }
        return null;
    }

    /** HTTP POST com Content-Type: application/x-www-form-urlencoded */
    private static String post(String url, String body) throws IOException, InterruptedException {
        // Forçar TLS 1.2/1.3 explicitamente — no Windows a JVM pode negociar TLS 1.0/1.1
        // que o Google rejeita com handshake_failure. No Linux funciona por padrão.
        SSLParameters sslParams = new SSLParameters();
        sslParams.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

        HttpClient client = HttpClient.newBuilder()
                .sslParameters(sslParams)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, BodyHandlers.ofString());
        } catch (IOException error) {
            throw GoogleSyncException.fromIOException(error);
        }
        requireOAuthSuccess(response.statusCode());
        return response.body();
    }

    static void requireOAuthSuccess(int statusCode) throws GoogleSyncException {
        if (statusCode >= 200 && statusCode < 300) return;
        if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
            throw GoogleSyncException.oauthRejected(statusCode);
        }
        throw GoogleSyncException.forStatus(statusCode);
    }

    private void clearTokens() throws IOException {
        accessToken = null;
        refreshToken = null;
        expiresAt = 0;
        grantedScopes.clear();
        Files.deleteIfExists(Paths.get(TOKENS_PATH));
    }

    static Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(value.trim().split("\\s+")));
    }

    static void writePrivateFile(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        restrictPrivateFile(path);
    }

    static void restrictPrivateFile(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows e outros sistemas sem permissões POSIX.
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validHttpUri(String value) {
        if (!notBlank(value)) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static void requireTokenResponse(String response) throws GoogleSyncException {
        if (response == null || !response.stripLeading().startsWith("{")
                || !response.stripTrailing().endsWith("}")
                || !SimpleJson.isStructurallyValid(response)) {
            throw GoogleSyncException.invalidResponse();
        }
    }
}
