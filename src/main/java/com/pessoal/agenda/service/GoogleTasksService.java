package com.pessoal.agenda.service;

import com.pessoal.agenda.repository.GoogleTasksMappingRepository;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository.TaskMapping;
import com.pessoal.agenda.repository.TaskRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
            try { return LocalDate.parse(due.substring(0, 10)); }
            catch (Exception e) { return null; }
        }
        @Override public String toString() { return title != null ? title : "(sem título)"; }
    }

    /**
     * Resultado de um ciclo de sincronização bidirecional.
     * @param createdLocal    tarefas criadas localmente (vieram do Google)
     * @param createdGoogle   tarefas criadas no Google (vieram do local)
     * @param completedLocal  tarefas marcadas como concluídas localmente
     * @param completedGoogle tarefas marcadas como concluídas no Google
     * @param errors          número de erros não fatais
     * @param log             mensagens de log legíveis
     */
    public record SyncResult(int createdLocal, int createdGoogle,
                              int completedLocal, int completedGoogle,
                              int errors, List<String> log) {
        public boolean hasChanges() {
            return createdLocal + createdGoogle + completedLocal + completedGoogle > 0;
        }
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

    // ── Sync Bidirecional ────────────────────────────────────────────────────

    /**
     * Executa sincronização bidirecional completa entre uma lista do Google Tasks
     * e as tarefas locais.
     *
     * Regras:
     *  - Google task sem mapeamento → cria localmente + salva mapeamento
     *  - Local task sem mapeamento  → cria no Google + salva mapeamento
     *  - Concluída no Google e não localmente → conclui localmente
     *  - Concluída localmente e não no Google → conclui no Google
     *  - Título/notas locais → atualiza no Google (local é fonte de verdade para texto)
     */
    public SyncResult syncBidirectional(String googleListId,
                                        TaskRepository taskRepo,
                                        GoogleTasksMappingRepository mappingRepo)
            throws IOException, InterruptedException {

        List<String> log = new ArrayList<>();
        int createdLocal = 0, createdGoogle = 0, completedLocal = 0, completedGoogle = 0, errors = 0;

        // Busca todos os dados
        List<GTask> googleTasks = listTasks(googleListId, true);
        List<com.pessoal.agenda.model.Task> localTasks = taskRepo.findOpenTasks();

        // Indexa as tarefas do Google por ID
        Map<String, GTask> googleById = googleTasks.stream()
                .collect(Collectors.toMap(GTask::id, t -> t));

        // ── PASSO 1: para cada tarefa do Google ──────────────────────────────
        for (GTask gt : googleTasks) {
            try {
                Optional<TaskMapping> mapping = mappingRepo.findByGoogleId(googleListId, gt.id());

                if (mapping.isEmpty()) {
                    // Nova no Google → cria localmente
                    LocalDate due = gt.dueDate() != null ? gt.dueDate() : LocalDate.now();
                    taskRepo.save(gt.title(),
                            gt.notes() != null && !gt.notes().isBlank() ? gt.notes() : null,
                            due, "Google Tasks");
                    // Recupera o ID recém-inserido
                    List<com.pessoal.agenda.model.Task> all = taskRepo.findOpenTasks();
                    com.pessoal.agenda.model.Task created = all.stream()
                            .filter(t -> t.title().equals(gt.title()))
                            .reduce((a, b) -> b) // pega o mais recente
                            .orElse(null);
                    if (created != null) {
                        mappingRepo.upsert(created.id(), googleListId, gt.id());
                        if (gt.completed()) {
                            taskRepo.markDone(created.id());
                            completedLocal++;
                        }
                    }
                    createdLocal++;
                    log.add("⬇ Criado local: " + gt.title());
                } else {
                    // Mapeamento existe → sincroniza status de conclusão
                    long localId = mapping.get().localTaskId();
                    com.pessoal.agenda.model.Task local = taskRepo.findById(localId).orElse(null);

                    if (local != null) {
                        // Google concluiu → conclui localmente
                        if (gt.completed() && !local.done()) {
                            taskRepo.markDone(localId);
                            completedLocal++;
                            log.add("✓ Concluído local (Google): " + gt.title());
                        }
                        // Local concluiu → conclui no Google
                        if (local.done() && !gt.completed()) {
                            completeTask(googleListId, gt.id());
                            completedGoogle++;
                            log.add("✓ Concluído Google (local): " + local.title());
                        }
                        // Atualiza texto no Google com dados locais (local é fonte de verdade)
                        if (!local.done() && !gt.completed()) {
                            updateTask(googleListId, gt.id(),
                                    local.title(), local.notes(), local.dueDate());
                        }
                        mappingRepo.upsert(localId, googleListId, gt.id());
                    }
                }
            } catch (Exception e) {
                errors++;
                log.add("✗ Erro em '" + gt.title() + "': " + e.getMessage());
            }
        }

        // ── PASSO 2: para cada tarefa local sem mapeamento → cria no Google ──
        for (com.pessoal.agenda.model.Task local : localTasks) {
            if (local.done()) continue; // não exporta tarefas já concluídas
            try {
                if (mappingRepo.findByLocalId(local.id()).isEmpty()) {
                    String gId = createTask(googleListId, local.title(),
                            local.notes(), local.dueDate());
                    if (gId != null) {
                        mappingRepo.upsert(local.id(), googleListId, gId);
                        createdGoogle++;
                        log.add("⬆ Criado Google: " + local.title());
                    }
                }
            } catch (Exception e) {
                errors++;
                log.add("✗ Erro ao exportar '" + local.title() + "': " + e.getMessage());
            }
        }

        return new SyncResult(createdLocal, createdGoogle, completedLocal, completedGoogle,
                errors, log);
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
