package com.pessoal.agenda.service;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.function.Consumer;

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

    private static final String SCOPE =
            "https://www.googleapis.com/auth/tasks";

    // Credenciais lidas do arquivo
    private String clientId;
    private String clientSecret;
    private String tokenUri;
    private String authUri;

    // Tokens em memória
    private String accessToken;
    private String refreshToken;
    private long   expiresAt; // epoch seconds

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

    public boolean hasValidCredentials() {
        return clientId != null && !clientId.isBlank();
    }

    /** Revoga a autorização e remove tokens locais. */
    public void revoke() throws IOException {
        accessToken  = null;
        refreshToken = null;
        expiresAt    = 0;
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
        long now = Instant.now().getEpochSecond();
        if (accessToken == null || now >= expiresAt - 60) {
            refreshAccessToken();
        }
        return accessToken;
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
        if (!hasValidCredentials()) {
            throw new IllegalStateException(
                "Credenciais não encontradas em: " + CREDENTIALS_PATH);
        }

        // Porta dinâmica
        int callbackPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            callbackPort = probe.getLocalPort();
        }
        String redirectUri = "http://localhost:" + callbackPort;

        String authUrl = authUri + "?"
                + "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&prompt=consent";

        if (progressCallback != null) progressCallback.accept("Abrindo navegador para autorização...");

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authUrl));
        } else {
            throw new UnsupportedOperationException(
                "Não foi possível abrir o navegador. Acesse manualmente:\n" + authUrl);
        }

        // Aguarda callback
        if (progressCallback != null) progressCallback.accept("Aguardando autorização do Google...");
        String code = waitForAuthCode(callbackPort, redirectUri);

        if (progressCallback != null) progressCallback.accept("Trocando código por tokens...");
        exchangeCodeForTokens(code, redirectUri);

        if (progressCallback != null) progressCallback.accept("Conectado com sucesso!");
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private String waitForAuthCode(int port, String redirectUri) throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            server.setSoTimeout(120_000); // 2 minutos
            try (Socket client = server.accept()) {
                // Lê a primeira linha do request: GET /?code=xxx&... HTTP/1.1
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = reader.readLine();

                // Responde com HTML de sucesso
                String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                        + "<title>Autorizado</title></head><body style='font-family:sans-serif;"
                        + "text-align:center;padding:60px;'>"
                        + "<h2 style='color:#1a73e8'>✓ Conexão realizada com sucesso!</h2>"
                        + "<p>Você pode fechar esta aba e voltar para a Agenda Científica.</p>"
                        + "</body></html>";
                String response = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/html; charset=UTF-8\r\n"
                        + "Content-Length: " + html.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                        + "Connection: close\r\n\r\n" + html;
                client.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));

                if (requestLine == null || !requestLine.contains("code=")) {
                    throw new IOException("Callback sem código de autorização: " + requestLine);
                }

                // Extrai o code da query string: /  ?code=XXXX&scope=...
                String query = requestLine.split(" ")[1];
                if (query.contains("?")) query = query.substring(query.indexOf('?') + 1);
                for (String param : query.split("&")) {
                    if (param.startsWith("code=")) {
                        return URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                    }
                }
                throw new IOException("Parâmetro 'code' não encontrado na resposta: " + requestLine);
            }
        }
    }

    private void exchangeCodeForTokens(String code, String redirectUri)
            throws IOException, InterruptedException {
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        String response = post(tokenUri, body);
        parseAndSaveTokens(response);
    }

    private synchronized void refreshAccessToken() throws IOException, InterruptedException {
        if (refreshToken == null) throw new IllegalStateException("Sem refresh token.");
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        String response = post(tokenUri, body);
        // Refresh response: access_token + expires_in (sem new refresh_token geralmente)
        String newAccess  = SimpleJson.str(response, "access_token");
        long   expiresIn  = SimpleJson.num(response, "expires_in");
        String newRefresh = SimpleJson.str(response, "refresh_token");
        if (newAccess == null) throw new IOException("Falha ao renovar token: " + response);
        accessToken = newAccess;
        expiresAt   = Instant.now().getEpochSecond() + (expiresIn > 0 ? expiresIn : 3600);
        if (newRefresh != null && !newRefresh.isBlank()) refreshToken = newRefresh;
        saveTokens();
    }

    private void parseAndSaveTokens(String response) throws IOException {
        accessToken  = SimpleJson.str(response, "access_token");
        refreshToken = SimpleJson.str(response, "refresh_token");
        long expiresIn = SimpleJson.num(response, "expires_in");
        if (accessToken == null || refreshToken == null) {
            throw new IOException("Resposta de token inválida: " + response);
        }
        expiresAt = Instant.now().getEpochSecond() + (expiresIn > 0 ? expiresIn : 3600);
        saveTokens();
    }

    private void saveTokens() throws IOException {
        Path path = Paths.get(TOKENS_PATH);
        String json = "{\"access_token\":\"" + accessToken + "\","
                    + "\"refresh_token\":\"" + refreshToken + "\","
                    + "\"expires_at\":" + expiresAt + "}";
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private void loadTokens() {
        try {
            Path path = Paths.get(TOKENS_PATH);
            if (!Files.exists(path)) return;
            String json = Files.readString(path, StandardCharsets.UTF_8);
            accessToken  = SimpleJson.str(json, "access_token");
            refreshToken = SimpleJson.str(json, "refresh_token");
            expiresAt    = SimpleJson.num(json, "expires_at");
        } catch (IOException e) {
            // tokens inexistentes ou corrompidos – ignorar
        }
    }

    private void loadCredentials() {
        try {
            Path path = Paths.get(CREDENTIALS_PATH);
            if (!Files.exists(path)) return;
            String json = Files.readString(path, StandardCharsets.UTF_8);
            // JSON: {"installed":{"client_id":"...","client_secret":"...","auth_uri":"...","token_uri":"..."}}
            String installed = extractObject(json, "installed");
            if (installed == null) installed = json; // fallback
            clientId     = SimpleJson.str(installed, "client_id");
            clientSecret = SimpleJson.str(installed, "client_secret");
            tokenUri     = SimpleJson.str(installed, "token_uri");
            authUri      = SimpleJson.str(installed, "auth_uri");
        } catch (IOException e) {
            System.err.println("[GoogleAuth] Erro ao ler credenciais: " + e.getMessage());
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
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        return response.body();
    }
}

