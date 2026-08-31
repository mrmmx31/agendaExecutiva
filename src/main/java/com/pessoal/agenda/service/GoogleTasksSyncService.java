package com.pessoal.agenda.service;

import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository.TaskMapping;
import com.pessoal.agenda.repository.GoogleTasksSyncRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.service.GoogleTasksService.GTask;
import com.pessoal.agenda.service.GoogleTasksService.SyncResult;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class GoogleTasksSyncService {
    private final GoogleTasksGateway gateway;
    private final TaskRepository taskRepository;
    private final GoogleTasksMappingRepository mappingRepository;
    private final GoogleTasksSyncRepository syncRepository;
    private final Clock clock;

    public GoogleTasksSyncService(GoogleTasksGateway gateway,
                                  TaskRepository taskRepository,
                                  GoogleTasksMappingRepository mappingRepository,
                                  GoogleTasksSyncRepository syncRepository) {
        this(gateway, taskRepository, mappingRepository, syncRepository,
                Clock.systemDefaultZone());
    }

    GoogleTasksSyncService(GoogleTasksGateway gateway,
                           TaskRepository taskRepository,
                           GoogleTasksMappingRepository mappingRepository,
                           GoogleTasksSyncRepository syncRepository,
                           Clock clock) {
        this.gateway = Objects.requireNonNull(gateway);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.mappingRepository = Objects.requireNonNull(mappingRepository);
        this.syncRepository = Objects.requireNonNull(syncRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public SyncResult syncBidirectional(String googleListId)
            throws IOException, InterruptedException {
        return applyPrepared(prepareSync(googleListId));
    }

    public PreparedSync prepareSync(String googleListId)
            throws IOException, InterruptedException {
        List<GTask> googleTasks = gateway.listTasksForSync(googleListId);
        SyncPlan plan = plan(googleListId, googleTasks, taskRepository.findOpenTasks());
        return new PreparedSync(googleListId, plan, summarize(plan));
    }

    public SyncResult applyPrepared(PreparedSync prepared)
            throws IOException, InterruptedException {
        Objects.requireNonNull(prepared);
        if (!prepared.markApplied()) {
            throw new IllegalStateException("Esta prévia já foi aplicada");
        }
        List<GTask> currentGoogle = gateway.listTasksForSync(prepared.googleListId());
        SyncPlan currentPlan = plan(prepared.googleListId(), currentGoogle,
                taskRepository.findOpenTasks());
        if (!prepared.plan().equals(currentPlan)) {
            throw GoogleSyncException.previewExpired();
        }
        return apply(prepared.googleListId(), prepared.plan());
    }

    SyncPlan plan(String googleListId, List<GTask> googleTasks, List<Task> openLocalTasks) {
        Map<String, TaskMapping> mappingsByGoogleId = new LinkedHashMap<>();
        for (TaskMapping mapping : mappingRepository.findByListId(googleListId)) {
            mappingsByGoogleId.put(mapping.googleTaskId(), mapping);
        }

        List<GTask> imports = new ArrayList<>();
        List<MappedChange> mappedChanges = new ArrayList<>();
        Set<String> seenGoogleIds = new HashSet<>();

        for (GTask googleTask : googleTasks) {
            seenGoogleIds.add(googleTask.id());
            TaskMapping mapping = mappingsByGoogleId.get(googleTask.id());
            if (mapping == null) {
                if (!googleTask.deleted()) imports.add(googleTask);
                continue;
            }
            if (mapping.syncState() != SyncState.ACTIVE) continue;

            Task localTask = taskRepository.findById(mapping.localTaskId()).orElse(null);
            if (localTask == null) {
                mappedChanges.add(MappedChange.review(mapping, null, googleTask,
                        SyncState.LOCAL_DELETED, "Excluída localmente"));
            } else if (googleTask.deleted()) {
                mappedChanges.add(MappedChange.review(mapping, localTask, googleTask,
                        SyncState.REMOTE_DELETED, "Excluída no Google"));
            } else {
                mappedChanges.add(compare(mapping, localTask, googleTask));
            }
        }

        for (TaskMapping mapping : mappingsByGoogleId.values()) {
            if (mapping.syncState() != SyncState.ACTIVE
                    || seenGoogleIds.contains(mapping.googleTaskId())) continue;
            Task localTask = taskRepository.findById(mapping.localTaskId()).orElse(null);
            SyncState state = localTask == null
                    ? SyncState.LOCAL_DELETED : SyncState.REMOTE_DELETED;
            String reason = localTask == null
                    ? "Excluída localmente" : "Ausente no Google";
            mappedChanges.add(MappedChange.review(
                    mapping, localTask, null, state, reason));
        }

        List<Task> exports = new ArrayList<>();
        for (Task localTask : openLocalTasks) {
            if (mappingRepository.findByLocalId(localTask.id()).isEmpty()) {
                exports.add(localTask);
            }
        }
        return new SyncPlan(imports, mappedChanges, exports,
                googleTasks.size(), openLocalTasks.size());
    }

    private MappedChange compare(TaskMapping mapping, Task local, GTask google) {
        if (mapping.syncedDone() == null) {
            if (sameText(local, google) && local.done() == google.completed()) {
                return MappedChange.snapshot(mapping, local, google);
            }
            return MappedChange.review(mapping, local, google, SyncState.CONFLICT,
                    "Mapeamento antigo divergiu nos dois lados");
        }

        boolean localTextChanged = !sameText(local, mapping);
        boolean googleTextChanged = !sameText(google, mapping);
        boolean currentTextEqual = sameText(local, google);
        boolean textConflict = localTextChanged && googleTextChanged && !currentTextEqual;

        boolean localStatusChanged = local.done() != mapping.syncedDone();
        boolean googleStatusChanged = google.completed() != mapping.syncedDone();
        boolean statusConflict = localStatusChanged && googleStatusChanged
                && local.done() != google.completed();

        if (textConflict || statusConflict) {
            return MappedChange.review(mapping, local, google, SyncState.CONFLICT,
                    "Alterações concorrentes");
        }

        return new MappedChange(mapping, local, google,
                googleTextChanged && !localTextChanged,
                localTextChanged && !googleTextChanged,
                googleStatusChanged && !localStatusChanged,
                localStatusChanged && !googleStatusChanged,
                (localTextChanged && googleTextChanged && currentTextEqual)
                        || (localStatusChanged && googleStatusChanged),
                null, null);
    }

    private SyncPreview summarize(SyncPlan plan) {
        int updateLocal = 0;
        int updateGoogle = 0;
        int statusLocal = 0;
        int statusGoogle = 0;
        int review = 0;
        List<String> details = new ArrayList<>();
        for (GTask task : plan.imports()) {
            details.add("Criar local: " + displayTitle(task.title()));
        }
        for (Task task : plan.exports()) {
            details.add("Criar Google: " + displayTitle(task.title()));
        }
        for (MappedChange change : plan.mappedChanges()) {
            if (change.reviewState() != null) {
                review++;
                details.add("Revisar: " + change.title() + " (" + change.reason() + ")");
                continue;
            }
            if (change.updateLocalText()) updateLocal++;
            if (change.updateRemoteText()) updateGoogle++;
            if (change.updateLocalStatus()) statusLocal++;
            if (change.updateRemoteStatus()) statusGoogle++;
        }
        return new SyncPreview(plan.imports().size(), plan.exports().size(),
                updateLocal, updateGoogle, statusLocal, statusGoogle, review,
                plan.processedGoogle(), plan.processedLocal(), List.copyOf(details));
    }

    public List<ReviewItem> listReviewItems(String googleListId) {
        List<ReviewItem> items = new ArrayList<>();
        for (TaskMapping mapping : mappingRepository.findByListId(googleListId)) {
            if (mapping.syncState() == SyncState.ACTIVE) continue;
            String title = taskRepository.findById(mapping.localTaskId())
                    .map(Task::title).orElse(mapping.syncedTitle());
            items.add(new ReviewItem(mapping.id(), mapping.syncState(),
                    displayTitle(title), mapping.googleTaskId()));
        }
        return List.copyOf(items);
    }

    public List<ReviewDetails> loadReviewDetails(String googleListId)
            throws IOException, InterruptedException {
        Map<String, GTask> googleById = new LinkedHashMap<>();
        for (GTask task : gateway.listTasksForSync(googleListId)) {
            googleById.put(task.id(), task);
        }

        List<ReviewDetails> details = new ArrayList<>();
        for (TaskMapping mapping : mappingRepository.findByListId(googleListId)) {
            if (mapping.syncState() == SyncState.ACTIVE) continue;
            Task local = taskRepository.findById(mapping.localTaskId()).orElse(null);
            GTask google = googleById.get(mapping.googleTaskId());
            String title = local != null ? local.title()
                    : google != null ? google.title() : mapping.syncedTitle();
            ReviewItem item = new ReviewItem(mapping.id(), mapping.syncState(),
                    displayTitle(title), mapping.googleTaskId());
            details.add(new ReviewDetails(item, reviewVersion(local), reviewVersion(google)));
        }
        return List.copyOf(details);
    }

    private static ReviewVersion reviewVersion(Task task) {
        return task == null
                ? ReviewVersion.unavailable()
                : new ReviewVersion(true, displayTitle(task.title()), blankToNull(task.notes()),
                        task.dueDate(), task.done(), localStatusLabel(task));
    }

    private static String localStatusLabel(Task task) {
        if (task.status() != null) return task.status().label();
        return task.done() ? "Concluída" : "Pendente";
    }

    private static ReviewVersion reviewVersion(GTask task) {
        return task == null || task.deleted()
                ? ReviewVersion.unavailable()
                : new ReviewVersion(true, displayTitle(task.title()), blankToNull(task.notes()),
                        task.dueDate(), task.completed(),
                        task.completed() ? "Concluída" : "Pendente");
    }

    public ResolutionResult resolveReview(long mappingId, Resolution resolution)
            throws IOException, InterruptedException {
        TaskMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Revisão não encontrada"));
        if (mapping.syncState() == SyncState.ACTIVE) {
            throw new IllegalStateException("Este item não exige mais revisão");
        }
        Task local = taskRepository.findById(mapping.localTaskId()).orElse(null);
        GTask google = gateway.listTasksForSync(mapping.googleListId()).stream()
                .filter(task -> task.id().equals(mapping.googleTaskId()))
                .findFirst().orElse(null);

        switch (mapping.syncState()) {
            case CONFLICT -> resolveConflict(mapping, local, google, resolution);
            case REMOTE_DELETED -> resolveRemoteDeletion(mapping, local, google, resolution);
            case LOCAL_DELETED -> resolveLocalDeletion(mapping, local, google, resolution);
            case ACTIVE -> throw new IllegalStateException("Item já sincronizado");
        }
        return new ResolutionResult(mappingId, resolution, mapping.syncState());
    }

    private void resolveConflict(TaskMapping mapping, Task local, GTask google,
                                 Resolution resolution)
            throws IOException, InterruptedException {
        if (local == null || google == null || google.deleted()) {
            throw new IllegalStateException("O estado mudou; sincronize novamente antes de resolver");
        }
        if (resolution == Resolution.USE_LOCAL) {
            gateway.updateTask(mapping.googleListId(), google.id(), local.title(),
                    local.notes(), local.dueDate());
            if (local.done()) gateway.completeTask(mapping.googleListId(), google.id());
            else gateway.reopenTask(mapping.googleListId(), google.id());
        } else {
            taskRepository.updateFromGoogle(local.id(), normalizedTitle(google.title()),
                    blankToNull(google.notes()), remoteDueDate(google));
            if (google.completed()) taskRepository.markDone(local.id());
            else taskRepository.reopen(local.id());
        }
        Task finalLocal = taskRepository.findById(local.id()).orElseThrow();
        mappingRepository.updateSnapshot(finalLocal.id(), finalLocal.title(),
                finalLocal.notes(), finalLocal.dueDate(), finalLocal.done(), google.updated());
    }

    private void resolveRemoteDeletion(TaskMapping mapping, Task local, GTask google,
                                       Resolution resolution) throws GoogleSyncException {
        if (google != null && !google.deleted()) throw GoogleSyncException.previewExpired();
        if (resolution == Resolution.USE_LOCAL) {
            if (local == null) throw new IllegalStateException("A tarefa local não existe mais");
            mappingRepository.deleteByLocalId(mapping.localTaskId());
        } else {
            if (local != null) taskRepository.deleteById(local.id());
            mappingRepository.deleteByGoogleId(mapping.googleListId(), mapping.googleTaskId());
        }
    }

    private void resolveLocalDeletion(TaskMapping mapping, Task local, GTask google,
                                      Resolution resolution)
            throws IOException, InterruptedException {
        if (local != null) throw GoogleSyncException.previewExpired();
        if (resolution == Resolution.USE_LOCAL) {
            if (google != null && !google.deleted()) {
                gateway.deleteTask(mapping.googleListId(), mapping.googleTaskId());
            }
            mappingRepository.deleteByGoogleId(mapping.googleListId(), mapping.googleTaskId());
        } else {
            if (google == null || google.deleted()) {
                throw new IllegalStateException("A tarefa Google não está mais disponível");
            }
            mappingRepository.deleteByGoogleId(mapping.googleListId(), mapping.googleTaskId());
            importGoogleTask(mapping.googleListId(), google);
        }
    }

    private SyncResult apply(String googleListId, SyncPlan plan) throws InterruptedException {
        Counters counters = new Counters();
        List<String> log = new ArrayList<>();

        for (GTask googleTask : plan.imports()) {
            try {
                var result = importGoogleTask(googleListId, googleTask);
                if (result.created()) {
                    counters.createdLocal++;
                    if (googleTask.completed()) counters.statusChangedLocal++;
                    log.add("Importada do Google: " + displayTitle(googleTask.title()));
                }
            } catch (RuntimeException error) {
                counters.errors++;
                log.add("Erro ao importar '" + displayTitle(googleTask.title())
                        + "': " + error.getMessage());
            }
        }

        for (MappedChange change : plan.mappedChanges()) {
            if (change.reviewState() != null) {
                mappingRepository.markState(change.mapping().id(), change.reviewState());
                counters.reviewRequired++;
                log.add("Revisão necessária: " + change.reason() + " - "
                        + change.title());
                continue;
            }
            try {
                applyMappedChange(googleListId, change, counters, log);
            } catch (InterruptedException error) {
                throw error;
            } catch (Exception error) {
                counters.errors++;
                log.add("Erro ao sincronizar '" + change.title()
                        + "': " + safeErrorMessage(error));
            }
        }

        for (Task localTask : plan.exports()) {
            try {
                if (exportLocalTask(googleListId, localTask)) {
                    counters.createdGoogle++;
                    log.add("Exportada para o Google: " + displayTitle(localTask.title()));
                }
            } catch (InterruptedException error) {
                throw error;
            } catch (Exception error) {
                counters.errors++;
                log.add("Erro ao exportar '" + displayTitle(localTask.title())
                        + "': " + safeErrorMessage(error));
            }
        }

        return new SyncResult(counters.createdLocal, counters.createdGoogle,
                counters.statusChangedLocal, counters.statusChangedGoogle,
                counters.updatedLocal, counters.updatedGoogle,
                counters.reviewRequired, plan.processedGoogle(), plan.processedLocal(),
                counters.errors, List.copyOf(log));
    }

    private void applyMappedChange(String googleListId, MappedChange change,
                                   Counters counters, List<String> log)
            throws IOException, InterruptedException {
        Task local = change.localTask();
        GTask google = change.googleTask();

        if (change.updateLocalText()) {
            taskRepository.updateFromGoogle(local.id(), normalizedTitle(google.title()),
                    blankToNull(google.notes()), remoteDueDate(google));
            counters.updatedLocal++;
            log.add("Texto atualizado localmente: " + displayTitle(google.title()));
        } else if (change.updateRemoteText()) {
            gateway.updateTask(googleListId, google.id(), local.title(),
                    local.notes(), local.dueDate());
            counters.updatedGoogle++;
            log.add("Texto atualizado no Google: " + displayTitle(local.title()));
        }

        if (change.updateLocalStatus()) {
            if (google.completed()) taskRepository.markDone(local.id());
            else taskRepository.reopen(local.id());
            counters.statusChangedLocal++;
            log.add("Status atualizado localmente: " + displayTitle(google.title()));
        } else if (change.updateRemoteStatus()) {
            if (local.done()) gateway.completeTask(googleListId, google.id());
            else gateway.reopenTask(googleListId, google.id());
            counters.statusChangedGoogle++;
            log.add("Status atualizado no Google: " + displayTitle(local.title()));
        }

        boolean changed = change.updateLocalText() || change.updateRemoteText()
                || change.updateLocalStatus() || change.updateRemoteStatus();
        if (changed || change.refreshSnapshot()) {
            Task finalLocal = taskRepository.findById(local.id()).orElseThrow();
            mappingRepository.updateSnapshot(finalLocal.id(), finalLocal.title(),
                    finalLocal.notes(), finalLocal.dueDate(), finalLocal.done(), google.updated());
        }
    }

    public GoogleTasksSyncRepository.ImportResult importGoogleTask(
            String googleListId, GTask googleTask) {
        return syncRepository.importTask(googleListId, googleTask.id(),
                normalizedTitle(googleTask.title()), blankToNull(googleTask.notes()),
                remoteDueDate(googleTask), googleTask.completed(), googleTask.updated());
    }

    public boolean exportLocalTask(String googleListId, Task localTask)
            throws IOException, InterruptedException {
        if (mappingRepository.findByLocalId(localTask.id()).isPresent()) return false;
        String googleTaskId = gateway.createTask(
                googleListId, localTask.title(), localTask.notes(), localTask.dueDate());
        if (googleTaskId == null || googleTaskId.isBlank()) {
            throw new IOException("Google não retornou o ID da tarefa criada");
        }
        try {
            mappingRepository.upsert(localTask.id(), googleListId, googleTaskId);
            mappingRepository.updateSnapshot(localTask.id(), localTask.title(),
                    localTask.notes(), localTask.dueDate(), localTask.done(), null);
        } catch (RuntimeException mappingError) {
            try {
                gateway.deleteTask(googleListId, googleTaskId);
            } catch (InterruptedException compensationError) {
                mappingError.addSuppressed(compensationError);
                throw compensationError;
            } catch (Exception compensationError) {
                mappingError.addSuppressed(compensationError);
            }
            mappingRepository.deleteByLocalId(localTask.id());
            throw mappingError;
        }
        return true;
    }

    private LocalDate remoteDueDate(GTask google) {
        return google.dueDate() != null ? google.dueDate() : LocalDate.now(clock);
    }

    private boolean sameText(Task local, GTask google) {
        return Objects.equals(local.title(), normalizedTitle(google.title()))
                && Objects.equals(blankToNull(local.notes()), blankToNull(google.notes()))
                && Objects.equals(local.dueDate(), remoteDueDate(google));
    }

    private static boolean sameText(Task local, TaskMapping mapping) {
        return Objects.equals(local.title(), mapping.syncedTitle())
                && Objects.equals(blankToNull(local.notes()), blankToNull(mapping.syncedNotes()))
                && Objects.equals(local.dueDate(), mapping.syncedDueDate());
    }

    private boolean sameText(GTask google, TaskMapping mapping) {
        return Objects.equals(normalizedTitle(google.title()), mapping.syncedTitle())
                && Objects.equals(blankToNull(google.notes()), blankToNull(mapping.syncedNotes()))
                && Objects.equals(remoteDueDate(google), mapping.syncedDueDate());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizedTitle(String title) {
        return title == null || title.isBlank() ? "(sem título)" : title;
    }

    private static String displayTitle(String title) {
        return title == null || title.isBlank() ? "(sem título)" : title;
    }

    private static String safeErrorMessage(Exception error) {
        return error instanceof IOException
                ? GoogleSyncErrorPresenter.userMessage(error)
                : error.getMessage();
    }

    record SyncPlan(List<GTask> imports, List<MappedChange> mappedChanges,
                    List<Task> exports, int processedGoogle, int processedLocal) {}

    public record SyncPreview(int createLocal, int createGoogle,
                              int updateLocal, int updateGoogle,
                              int statusLocal, int statusGoogle,
                              int reviewRequired, int processedGoogle,
                              int processedLocal, List<String> details) {
        public boolean hasActions() {
            return createLocal + createGoogle + updateLocal + updateGoogle
                    + statusLocal + statusGoogle + reviewRequired > 0;
        }
    }

    public static final class PreparedSync {
        private final String googleListId;
        private final SyncPlan plan;
        private final SyncPreview preview;
        private final AtomicBoolean applied = new AtomicBoolean();

        private PreparedSync(String googleListId, SyncPlan plan, SyncPreview preview) {
            this.googleListId = googleListId;
            this.plan = plan;
            this.preview = preview;
        }

        public String googleListId() { return googleListId; }
        public SyncPreview preview() { return preview; }
        private SyncPlan plan() { return plan; }
        private boolean markApplied() { return applied.compareAndSet(false, true); }
    }

    public enum Resolution { USE_LOCAL, USE_GOOGLE }

    public record ReviewItem(long mappingId, SyncState state,
                             String title, String googleTaskId) {}

    public record ReviewDetails(ReviewItem item, ReviewVersion local, ReviewVersion google) {}

    public record ReviewVersion(boolean available, String title, String notes,
                                LocalDate dueDate, boolean completed, String statusLabel) {
        public ReviewVersion(boolean available, String title, String notes,
                             LocalDate dueDate, boolean completed) {
            this(available, title, notes, dueDate, completed,
                    available ? defaultStatusLabel(completed) : null);
        }

        private static String defaultStatusLabel(boolean completed) {
            return completed ? "Concluída" : "Pendente";
        }

        static ReviewVersion unavailable() {
            return new ReviewVersion(false, null, null, null, false, null);
        }
    }

    public record ResolutionResult(long mappingId, Resolution resolution,
                                   SyncState previousState) {}

    record MappedChange(TaskMapping mapping, Task localTask, GTask googleTask,
                        boolean updateLocalText, boolean updateRemoteText,
                        boolean updateLocalStatus, boolean updateRemoteStatus,
                        boolean refreshSnapshot,
                        SyncState reviewState, String reason) {
        static MappedChange snapshot(TaskMapping mapping, Task local, GTask google) {
            return new MappedChange(mapping, local, google, false, false,
                    false, false, true, null, null);
        }

        static MappedChange review(TaskMapping mapping, Task local, GTask google,
                                   SyncState state, String reason) {
            return new MappedChange(mapping, local, google, false, false,
                    false, false, false, state, reason);
        }

        String title() {
            return displayTitle(localTask != null ? localTask.title()
                    : googleTask != null ? googleTask.title() : null);
        }
    }

    private static final class Counters {
        int createdLocal;
        int createdGoogle;
        int statusChangedLocal;
        int statusChangedGoogle;
        int updatedLocal;
        int updatedGoogle;
        int reviewRequired;
        int errors;
    }
}
