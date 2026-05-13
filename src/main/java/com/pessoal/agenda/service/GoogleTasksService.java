package com.pessoal.agenda.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Integração com o Google Tasks REST API v1.
 *
 * Referência: https://developers.google.com/tasks/reference/rest
 */
public class GoogleTasksService {

    private static final String BASE = "https://tasks.googleapis.com/tasks/v1";

    private final GoogleAuthService auth;
    private final HttpClient        http;

    public GoogleTasksService() {
        this.auth = GoogleAuthService.getInstance();
        this.http = HttpClient.newHttpClient();
    }

    // ── Modelos ──────────────────────────────────────────────────────────────

    public record TaskList(String id, String title) {
        @Override public String toString() { return title; }
    }

    public record GTask(String id, String title, String notes,
                        String due,   // ISO-8601 datetime ou null
                        boolean completed, String status) {
        /** Data de vencimento extraída ou null */
        public LocalDate dueDate() {
            if (due == null || due.isBlank()) return null;
            try { return LocalDate.parse(due.substring(0, 10)); } // "2024-05-10T00:00:00.000Z"
            catch (Exception e) { return null; }
        }
        @Override public String toString() { return title != null ? title : "(sem título)"; }
    }

    // ── Task Lists ───────────────────────────────────────────────────────────

    /** Lista todas as listas de tarefas do usuário. */
    public List<TaskList> listTaskLists() throws IOException, InterruptedException {
        String json = get("/users/@me/lists");
        List<String> items = SimpleJson.array(json, "items");
        List<TaskList> result = new ArrayList<>();
        for (String item : items) {
            String id    = SimpleJson.str(item, "id");
            String title = SimpleJson.str(item, "title");
            if (id != null) result.add(new TaskList(id, title != null ? title : "(sem nome)"));
        }
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
        String url = "/lists/" + encode(taskListId) + "/tasks"
                   + "?showCompleted=" + showCompleted
                   + "&maxResults=100";
        String json = get(url);
        List<String> items = SimpleJson.array(json, "items");
        List<GTask> result = new ArrayList<>();
        for (String item : items) {
            String id        = SimpleJson.str(item, "id");
            String title     = SimpleJson.str(item, "title");
            String notes     = SimpleJson.str(item, "notes");
            String due       = SimpleJson.str(item, "due");
            String status    = SimpleJson.str(item, "status");
            boolean done     = "completed".equalsIgnoreCase(status);
            if (id != null) result.add(new GTask(id, title, notes, due, done, status));
        }
        return result;
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
        return SimpleJson.str(response, "id");
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
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
        checkStatus(res);
        return res.body();
    }

    private String post(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
        checkStatus(res);
        return res.body();
    }

    private void patch(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
        checkStatus(res);
    }

    private void delete(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .DELETE()
                .build();
        HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
        if (res.statusCode() != 204 && res.statusCode() != 200) {
            throw new IOException("Google API DELETE falhou [" + res.statusCode() + "]: " + res.body());
        }
    }

    private static void checkStatus(HttpResponse<String> res) throws IOException {
        int code = res.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Google API error [" + code + "]: " + res.body());
        }
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

