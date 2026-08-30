package com.pessoal.agenda.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLParameters;

/**
 * Integração com o Google Tasks REST API v1.
 *
 * Referência: https://developers.google.com/tasks/reference/rest
 */
public class GoogleTasksService implements GoogleTasksGateway {

    private static final String BASE = "https://tasks.googleapis.com/tasks/v1";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ApiTransport transport;

    public GoogleTasksService() {
        GoogleAuthService auth = GoogleAuthService.getInstance();
        // Forçar TLS 1.2/1.3 — no Windows a JVM pode negociar TLS 1.0/1.1
        // que o Google rejeita com handshake_failure
        SSLParameters sslParams = new SSLParameters();
        sslParams.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        HttpClient http = HttpClient.newBuilder()
                .sslParameters(sslParams)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.transport = new JdkApiTransport(auth, http);
    }

    GoogleTasksService(ApiTransport transport) {
        this.transport = transport;
    }

    // ── Modelos ──────────────────────────────────────────────────────────────

    public record TaskList(String id, String title) {
        @Override public String toString() { return title; }
    }

    public record GTask(String id, String title, String notes,
                        String due,   // ISO-8601 datetime ou null
                        boolean completed, String status,
                        String updated, boolean deleted) {
        public GTask(String id, String title, String notes, String due,
                     boolean completed, String status) {
            this(id, title, notes, due, completed, status, null, false);
        }

        /** Data de vencimento extraída ou null */
        public LocalDate dueDate() {
            if (due == null || due.isBlank()) return null;
            try { return LocalDate.parse(due.substring(0, 10)); }
            catch (Exception e) { return null; }
        }
        @Override public String toString() { return title != null ? title : "(sem título)"; }
    }

    /**
     * Resultado de um ciclo de sincronização bidirecional.
     * @param createdLocal    tarefas criadas localmente (vieram do Google)
     * @param createdGoogle   tarefas criadas no Google (vieram do local)
     * @param statusChangedLocal  tarefas cujo status mudou localmente
     * @param statusChangedGoogle tarefas cujo status mudou no Google
     * @param updatedGoogle   tarefas cujo texto ou data foram atualizados no Google
     * @param updatedLocal    tarefas cujo texto ou data foram atualizados localmente
     * @param reviewRequired  conflitos ou exclusões preservados para revisão
     * @param processedGoogle itens lidos do Google no ciclo
     * @param processedLocal  itens locais abertos considerados no ciclo
     * @param errors          número de erros não fatais
     * @param log             mensagens de log legíveis
     */
    public record SyncResult(int createdLocal, int createdGoogle,
                              int statusChangedLocal, int statusChangedGoogle,
                              int updatedLocal, int updatedGoogle,
                              int reviewRequired, int processedGoogle,
                              int processedLocal, int errors, List<String> log) {
        public boolean hasChanges() {
            return createdLocal + createdGoogle + statusChangedLocal
                    + statusChangedGoogle + updatedLocal + updatedGoogle
                    + reviewRequired > 0;
        }
    }

    // ── Task Lists ───────────────────────────────────────────────────────────

    /** Lista todas as listas de tarefas do usuário. */
    public List<TaskList> listTaskLists() throws IOException, InterruptedException {
        List<TaskList> result = new ArrayList<>();
        Set<String> visitedTokens = new HashSet<>();
        String pageToken = null;
        do {
            String path = "/users/@me/lists?maxResults=100"
                    + pageTokenParameter(pageToken);
            String json = get(path);
            requireJsonObject(json);
            for (String item : SimpleJson.array(json, "items")) {
                String id = SimpleJson.str(item, "id");
                String title = SimpleJson.str(item, "title");
                if (id == null || id.isBlank()) throw GoogleSyncException.invalidResponse();
                result.add(new TaskList(id, title != null ? title : "(sem nome)"));
            }
            pageToken = SimpleJson.str(json, "nextPageToken");
            requireNewPageToken(pageToken, visitedTokens);
        } while (pageToken != null && !pageToken.isBlank());
        return result;
    }

    // ── Tasks ────────────────────────────────────────────────────────────────

    /**
     * Lista tarefas de uma lista específica.
     *
     * @param taskListId   ID da lista Google Tasks
     * @param showCompleted incluir tarefas concluídas
     */
    public List<GTask> listTasks(String taskListId, boolean showCompleted)
            throws IOException, InterruptedException {
        return listTasks(taskListId, showCompleted, false);
    }

    @Override
    public List<GTask> listTasksForSync(String taskListId)
            throws IOException, InterruptedException {
        return listTasks(taskListId, true, true);
    }

    private List<GTask> listTasks(String taskListId, boolean showCompleted,
                                  boolean showDeleted)
            throws IOException, InterruptedException {
        List<GTask> result = new ArrayList<>();
        Set<String> visitedTokens = new HashSet<>();
        String pageToken = null;
        do {
            String path = "/lists/" + encode(taskListId) + "/tasks"
                    + "?showCompleted=" + showCompleted
                    + "&showDeleted=" + showDeleted
                    + "&showHidden=true&maxResults=100"
                    + pageTokenParameter(pageToken);
            String json = get(path);
            requireJsonObject(json);
            for (String item : SimpleJson.array(json, "items")) {
                String id = SimpleJson.str(item, "id");
                String title = SimpleJson.str(item, "title");
                String notes = SimpleJson.str(item, "notes");
                String due = SimpleJson.str(item, "due");
                String status = SimpleJson.str(item, "status");
                String updated = SimpleJson.str(item, "updated");
                boolean done = "completed".equalsIgnoreCase(status);
                boolean deleted = SimpleJson.bool(item, "deleted");
                if (id == null || id.isBlank()) throw GoogleSyncException.invalidResponse();
                result.add(new GTask(
                        id, title, notes, due, done, status, updated, deleted));
            }
            pageToken = SimpleJson.str(json, "nextPageToken");
            requireNewPageToken(pageToken, visitedTokens);
        } while (pageToken != null && !pageToken.isBlank());
        return result;
    }

    private static String pageTokenParameter(String pageToken) {
        return pageToken == null || pageToken.isBlank()
                ? "" : "&pageToken=" + encode(pageToken);
    }

    /**
     * Cria uma nova tarefa em uma lista.
     *
     * @param taskListId ID da lista
     * @param title      título
     * @param notes      notas (pode ser null)
     * @param dueDate    data de vencimento (pode ser null)
     * @return ID da tarefa criada
     */
    public String createTask(String taskListId, String title, String notes, LocalDate dueDate)
            throws IOException, InterruptedException {
        String due = dueDate != null ? dueDate + "T00:00:00.000Z" : null;
        String body = buildTaskJson(title, notes, due, false);
        String response = post("/lists/" + encode(taskListId) + "/tasks", body);
        requireJsonObject(response);
        String id = SimpleJson.str(response, "id");
        if (id == null || id.isBlank()) throw GoogleSyncException.invalidResponse();
        return id;
    }

    /**
     * Marca uma tarefa como concluída.
     */
    public void completeTask(String taskListId, String taskId)
            throws IOException, InterruptedException {
        String body = "{\"status\":\"completed\"}";
        patch("/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), body);
    }

    /**
     * Reabre uma tarefa concluída (marca como needsAction).
     */
    public void reopenTask(String taskListId, String taskId)
            throws IOException, InterruptedException {
        String body = "{\"status\":\"needsAction\",\"completed\":null}";
        patch("/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), body);
    }

    /**
     * Deleta uma tarefa.
     */
    public void deleteTask(String taskListId, String taskId)
            throws IOException, InterruptedException {
        delete("/lists/" + encode(taskListId) + "/tasks/" + encode(taskId));
    }

    /**
     * Atualiza o título e notas de uma tarefa.
     */
    public void updateTask(String taskListId, String taskId,
                           String title, String notes, LocalDate dueDate)
            throws IOException, InterruptedException {
        String due = dueDate != null ? dueDate + "T00:00:00.000Z" : null;
        String body = buildTaskJson(title, notes, due, false);
        patch("/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), body);
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private String get(String path) throws IOException, InterruptedException {
        return execute("GET", path, null);
    }

    private String post(String path, String jsonBody) throws IOException, InterruptedException {
        return execute("POST", path, jsonBody);
    }

    private void patch(String path, String jsonBody) throws IOException, InterruptedException {
        execute("PATCH", path, jsonBody);
    }

    private void delete(String path) throws IOException, InterruptedException {
        execute("DELETE", path, null);
    }

    private String execute(String method, String path, String body)
            throws IOException, InterruptedException {
        ApiRequest request = new ApiRequest(method, path, body);
        boolean retrySafe = !"POST".equals(method);
        for (int attempt = 0; ; attempt++) {
            ApiResponse response;
            try {
                response = transport.send(request);
            } catch (IOException error) {
                GoogleSyncException classified = GoogleSyncException.fromIOException(error);
                if (retrySafe && attempt == 0 && classified.retryable()) continue;
                throw classified;
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body() == null ? "" : response.body();
            }
            GoogleSyncException classified = GoogleSyncException.forStatus(response.statusCode());
            if (response.statusCode() == 401 && attempt == 0) {
                transport.refreshAuthentication();
                continue;
            }
            if (retrySafe && attempt == 0 && classified.retryable()
                    && classified.kind() != GoogleSyncException.Kind.RATE_LIMIT) {
                continue;
            }
            throw classified;
        }
    }

    private static void requireJsonObject(String json) throws GoogleSyncException {
        if (json == null || !json.stripLeading().startsWith("{")
                || !json.stripTrailing().endsWith("}")
                || !SimpleJson.isStructurallyValid(json)
                || !SimpleJson.isArrayField(json, "items")) {
            throw GoogleSyncException.invalidResponse();
        }
    }

    private static void requireNewPageToken(String pageToken, Set<String> visitedTokens)
            throws GoogleSyncException {
        if (pageToken != null && !pageToken.isBlank() && !visitedTokens.add(pageToken)) {
            throw GoogleSyncException.invalidResponse();
        }
    }

    record ApiRequest(String method, String path, String body) {}

    record ApiResponse(int statusCode, String body) {}

    @FunctionalInterface
    interface ApiTransport {
        ApiResponse send(ApiRequest request) throws IOException, InterruptedException;

        default void refreshAuthentication() throws IOException {}
    }

    private record JdkApiTransport(GoogleAuthService auth, HttpClient http)
            implements ApiTransport {
        @Override
        public ApiResponse send(ApiRequest apiRequest)
                throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + apiRequest.path()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + auth.getAccessToken());
            if (apiRequest.body() != null) {
                builder.header("Content-Type", "application/json; charset=UTF-8");
            }
            HttpRequest request = switch (apiRequest.method()) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(
                        apiRequest.body(), StandardCharsets.UTF_8)).build();
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(
                        apiRequest.body(), StandardCharsets.UTF_8)).build();
                case "DELETE" -> builder.DELETE().build();
                default -> throw new IllegalArgumentException("Método HTTP não suportado");
            };
            HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body());
        }

        @Override
        public void refreshAuthentication() throws IOException {
            auth.invalidateAccessToken();
        }
    }

    /** Agrupa tarefas não concluídas com o mesmo título (normalizado).
     *  Retorna apenas grupos com 2+ tarefas. O primeiro elemento é o mais antigo. */
    public List<List<GTask>> findGoogleDuplicateGroups(String taskListId) throws IOException, InterruptedException {
        List<GTask> all = listTasks(taskListId, false);
        Map<String, List<GTask>> byTitle = new java.util.LinkedHashMap<>();
        for (GTask t : all) {
            String key = t.title() == null ? "" : t.title().trim().toLowerCase();
            byTitle.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<List<GTask>> result = new ArrayList<>();
        for (List<GTask> group : byTitle.values()) {
            if (group.size() > 1) result.add(group);
        }
        return result;
    }

    private static String buildTaskJson(String title, String notes, String due, boolean completed) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"title\":\"").append(escapeJson(title != null ? title : "")).append("\"");
        if (notes != null && !notes.isBlank())
            sb.append(",\"notes\":\"").append(escapeJson(notes)).append("\"");
        if (due != null)
            sb.append(",\"due\":\"").append(due).append("\"");
        if (completed)
            sb.append(",\"status\":\"completed\"");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
