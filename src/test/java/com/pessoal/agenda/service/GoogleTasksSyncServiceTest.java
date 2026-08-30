package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository;
import com.pessoal.agenda.repository.GoogleTasksSyncRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.service.GoogleTasksService.GTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState.CONFLICT;
import static com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState.LOCAL_DELETED;
import static com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState.REMOTE_DELETED;

class GoogleTasksSyncServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);

    @TempDir
    Path tempDir;

    private Database database;
    private TaskRepository taskRepository;
    private GoogleTasksMappingRepository mappingRepository;
    private FakeGateway gateway;
    private GoogleTasksSyncService service;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("google-sync-service.db"));
        database.runMigrations();
        taskRepository = new TaskRepository(database);
        mappingRepository = new GoogleTasksMappingRepository(database);
        gateway = new FakeGateway();
        service = new GoogleTasksSyncService(
                gateway, taskRepository, mappingRepository,
                new GoogleTasksSyncRepository(database),
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sameTitlesUseGoogleIdentityAndRepeatedSyncIsIdempotent() throws Exception {
        gateway.tasks.add(task("google-1", "Mesmo título", "primeira", TODAY, false));
        gateway.tasks.add(task("google-2", "Mesmo título", "segunda", TODAY, false));

        var first = service.syncBidirectional("list-1");
        var repeated = service.syncBidirectional("list-1");

        assertEquals(2, first.createdLocal());
        assertFalse(repeated.hasChanges());
        assertEquals(0, repeated.errors());
        assertEquals(2, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertEquals(2, database.queryInt("SELECT COUNT(*) FROM google_tasks_mapping"));
        long firstLocal = mappingRepository.findByGoogleId("list-1", "google-1")
                .orElseThrow().localTaskId();
        long secondLocal = mappingRepository.findByGoogleId("list-1", "google-2")
                .orElseThrow().localTaskId();
        assertNotEquals(firstLocal, secondLocal);
        assertEquals("primeira", taskRepository.findById(firstLocal).orElseThrow().notes());
        assertEquals("segunda", taskRepository.findById(secondLocal).orElseThrow().notes());
        assertEquals(0, gateway.updateCalls.get());
    }

    @Test
    void previewDoesNotMutateAndCanBeAppliedOnlyOnce() throws Exception {
        gateway.tasks.add(task("google-1", "Somente na prévia", null, TODAY, false));

        var prepared = service.prepareSync("list-1");

        assertEquals(1, prepared.preview().createLocal());
        assertTrue(prepared.preview().hasActions());
        assertEquals(0, database.queryInt("SELECT COUNT(*) FROM tasks"));

        var result = service.applyPrepared(prepared);
        assertEquals(1, result.createdLocal());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertThrows(IllegalStateException.class, () -> service.applyPrepared(prepared));
    }

    @Test
    void stalePreviewIsRejectedBeforeAnyMutation() throws Exception {
        gateway.tasks.add(task("google-1", "Antes", null, TODAY, false));
        var prepared = service.prepareSync("list-1");
        gateway.tasks.set(0, task("google-1", "Mudou durante a prévia", null, TODAY, false));

        GoogleSyncException error = assertThrows(GoogleSyncException.class,
                () -> service.applyPrepared(prepared));

        assertEquals(GoogleSyncException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals(0, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertEquals(0, database.queryInt("SELECT COUNT(*) FROM google_tasks_mapping"));
    }

    @Test
    void syncProcessesAllOneHundredAndFiftyGatewayTasks() throws Exception {
        for (int index = 0; index < 150; index++) {
            gateway.tasks.add(task("google-" + index, "Tarefa " + index,
                    null, TODAY, false));
        }

        var result = service.syncBidirectional("list-1");

        assertEquals(150, result.createdLocal());
        assertEquals(150, result.processedGoogle());
        assertEquals(150, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertEquals(150, database.queryInt("SELECT COUNT(*) FROM google_tasks_mapping"));
    }

    @Test
    void localExportCreatesOnceAndKeepsMappingOnRepeatedSync() throws Exception {
        long localId = taskRepository.saveReturningId(
                "Enviar documento", "Notas", TODAY, "Geral");

        var first = service.syncBidirectional("list-1");
        var repeated = service.syncBidirectional("list-1");

        assertEquals(1, first.createdGoogle());
        assertFalse(repeated.hasChanges());
        assertEquals(1, gateway.createCalls.get());
        assertTrue(mappingRepository.findByLocalId(localId).isPresent());
    }

    @Test
    void remoteReadFailureAbortsBeforeChangingLocalState() {
        long localId = taskRepository.saveReturningId(
                "Preservar em falha", null, TODAY, "Geral");
        mappingRepository.upsert(localId, "list-1", "google-1");
        mappingRepository.updateSnapshot(localId, "Preservar em falha", null,
                TODAY, false, null);
        gateway.listFailure = GoogleSyncException.invalidResponse();

        assertThrows(GoogleSyncException.class,
                () -> service.syncBidirectional("list-1"));

        assertFalse(taskRepository.findById(localId).orElseThrow().done());
        assertEquals(com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState.ACTIVE,
                mappingRepository.findByLocalId(localId).orElseThrow().syncState());
    }

    @Test
    void manualImportCreatesMappingAndCannotDuplicate() {
        GTask googleTask = task("google-manual", "Importação manual", null, null, false);

        var first = service.importGoogleTask("list-1", googleTask);
        var repeated = service.importGoogleTask("list-1", googleTask);

        assertTrue(first.created());
        assertFalse(repeated.created());
        assertEquals(first.localTaskId(), repeated.localTaskId());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertTrue(mappingRepository.findByGoogleId("list-1", "google-manual").isPresent());
        assertEquals(TODAY, taskRepository.findById(first.localTaskId()).orElseThrow().dueDate());
    }

    @Test
    void onlyChangedMappedTextProducesOneRemoteUpdate() throws Exception {
        long localId = taskRepository.saveReturningId(
                "Título antigo", "Notas antigas", TODAY.minusDays(1), "Geral");
        mappingRepository.upsert(localId, "list-1", "google-1");
        mappingRepository.updateSnapshot(localId, "Título antigo", "Notas antigas",
                TODAY.minusDays(1), false, null);
        taskRepository.updateFromGoogle(localId, "Título local", "Notas locais", TODAY);
        gateway.tasks.add(task(
                "google-1", "Título antigo", "Notas antigas", TODAY.minusDays(1), false));

        var changed = service.syncBidirectional("list-1");
        var repeated = service.syncBidirectional("list-1");

        assertEquals(1, changed.updatedGoogle());
        assertTrue(changed.hasChanges());
        assertFalse(repeated.hasChanges());
        assertEquals(1, gateway.updateCalls.get());
    }

    @Test
    void remoteOnlyTextChangeUpdatesLocalAndThenIsIdempotent() throws Exception {
        gateway.tasks.add(task("google-1", "Original", "Notas", TODAY, false));
        service.syncBidirectional("list-1");
        long localId = mappingRepository.findByGoogleId("list-1", "google-1")
                .orElseThrow().localTaskId();
        gateway.tasks.set(0, task("google-1", "Alterado no Google", "Novas", TODAY, false));

        var changed = service.syncBidirectional("list-1");
        var repeated = service.syncBidirectional("list-1");

        assertEquals(1, changed.updatedLocal());
        assertEquals("Alterado no Google", taskRepository.findById(localId).orElseThrow().title());
        assertEquals(List.of("Texto atualizado localmente: Alterado no Google"), changed.log());
        assertFalse(repeated.hasChanges());
        assertEquals(0, gateway.updateCalls.get());
    }

    @Test
    void concurrentTextChangesArePreservedForReview() throws Exception {
        gateway.tasks.add(task("google-1", "Original", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        taskRepository.updateFromGoogle(mapping.localTaskId(), "Alterado local", null, TODAY);
        gateway.tasks.set(0, task("google-1", "Alterado Google", null, TODAY, false));

        var result = service.syncBidirectional("list-1");

        assertEquals(1, result.reviewRequired());
        assertEquals("Alterado local", taskRepository.findById(mapping.localTaskId())
                .orElseThrow().title());
        assertEquals("Alterado Google", gateway.tasks.get(0).title());
        assertEquals(0, gateway.updateCalls.get());
        assertEquals(CONFLICT, mappingRepository.findByLocalId(mapping.localTaskId())
                .orElseThrow().syncState());
    }

    @Test
    void reviewDetailsExposeBothVersionsBeforeResolution() throws Exception {
        createConflict("Alterado local", "Alterado Google");

        var details = service.loadReviewDetails("list-1").getFirst();

        assertEquals(CONFLICT, details.item().state());
        assertEquals("Alterado local", details.local().title());
        assertTrue(details.local().available());
        assertEquals("Alterado Google", details.google().title());
        assertTrue(details.google().available());
    }

    @Test
    void conflictCanBeResolvedUsingLocalVersion() throws Exception {
        long mappingId = createConflict("Local escolhido", "Google descartado");

        var reviews = service.listReviewItems("list-1");
        service.resolveReview(mappingId, GoogleTasksSyncService.Resolution.USE_LOCAL);

        assertEquals(1, reviews.size());
        assertEquals("Local escolhido", gateway.tasks.getFirst().title());
        assertEquals(com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState.ACTIVE,
                mappingRepository.findById(mappingId).orElseThrow().syncState());
    }

    @Test
    void conflictCanBeResolvedUsingGoogleVersion() throws Exception {
        long mappingId = createConflict("Local descartado", "Google escolhido");
        long localId = mappingRepository.findById(mappingId).orElseThrow().localTaskId();

        service.resolveReview(mappingId, GoogleTasksSyncService.Resolution.USE_GOOGLE);

        assertEquals("Google escolhido", taskRepository.findById(localId).orElseThrow().title());
        assertEquals(com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState.ACTIVE,
                mappingRepository.findById(mappingId).orElseThrow().syncState());
    }

    @Test
    void equalConcurrentChangesAdvanceSnapshotWithoutFalseFutureConflict() throws Exception {
        gateway.tasks.add(task("google-1", "Original", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        taskRepository.updateFromGoogle(mapping.localTaskId(), "Mesmo valor", null, TODAY);
        gateway.tasks.set(0, task("google-1", "Mesmo valor", null, TODAY, false));
        service.syncBidirectional("list-1");
        taskRepository.updateFromGoogle(mapping.localTaskId(), "Só local depois", null, TODAY);

        var result = service.syncBidirectional("list-1");

        assertEquals(0, result.reviewRequired());
        assertEquals(1, result.updatedGoogle());
        assertEquals("Só local depois", gateway.tasks.get(0).title());
    }

    @Test
    void remoteDeletionKeepsLocalTaskAndMarksMappingForReview() throws Exception {
        gateway.tasks.add(task("google-1", "Preservar", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        gateway.tasks.set(0, deletedTask("google-1", "Preservar"));

        var result = service.syncBidirectional("list-1");

        assertEquals(1, result.reviewRequired());
        assertTrue(taskRepository.findById(mapping.localTaskId()).isPresent());
        assertEquals(REMOTE_DELETED, mappingRepository.findByLocalId(mapping.localTaskId())
                .orElseThrow().syncState());
        var details = service.loadReviewDetails("list-1").getFirst();
        assertTrue(details.local().available());
        assertFalse(details.google().available());
    }

    @Test
    void remoteDeletionCanBeResolvedByRecreatingFromLocalOnNextApply() throws Exception {
        gateway.tasks.add(task("google-1", "Recriar", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        gateway.tasks.set(0, deletedTask("google-1", "Recriar"));
        service.syncBidirectional("list-1");

        service.resolveReview(mapping.id(), GoogleTasksSyncService.Resolution.USE_LOCAL);
        var preview = service.prepareSync("list-1").preview();

        assertTrue(taskRepository.findById(mapping.localTaskId()).isPresent());
        assertTrue(mappingRepository.findById(mapping.id()).isEmpty());
        assertEquals(1, preview.createGoogle());
    }

    @Test
    void localDeletionKeepsRemoteTaskAndMarksMappingForReview() throws Exception {
        gateway.tasks.add(task("google-1", "Preservar", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        taskRepository.deleteById(mapping.localTaskId());

        var result = service.syncBidirectional("list-1");

        assertEquals(1, result.reviewRequired());
        assertEquals(1, gateway.tasks.size());
        assertEquals(LOCAL_DELETED, mappingRepository.findByGoogleId("list-1", "google-1")
                .orElseThrow().syncState());
        var details = service.loadReviewDetails("list-1").getFirst();
        assertFalse(details.local().available());
        assertTrue(details.google().available());
    }

    @Test
    void localDeletionCanBeResolvedByRestoringGoogleVersion() throws Exception {
        gateway.tasks.add(task("google-1", "Restaurar", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        taskRepository.deleteById(mapping.localTaskId());
        service.syncBidirectional("list-1");

        service.resolveReview(mapping.id(), GoogleTasksSyncService.Resolution.USE_GOOGLE);

        var restored = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        assertNotEquals(mapping.localTaskId(), restored.localTaskId());
        assertEquals("Restaurar", taskRepository.findById(restored.localTaskId())
                .orElseThrow().title());
    }

    @Test
    void reopenPropagatesInBothDirections() throws Exception {
        gateway.tasks.add(task("google-1", "Reabrir remoto", null, TODAY, true));
        service.syncBidirectional("list-1");
        var firstMapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        taskRepository.reopen(firstMapping.localTaskId());

        var remoteResult = service.syncBidirectional("list-1");

        assertEquals(1, remoteResult.statusChangedGoogle());
        assertFalse(gateway.tasks.get(0).completed());
        assertEquals(1, gateway.reopenCalls.get());
        assertEquals(List.of("Status atualizado no Google: Reabrir remoto"),
                remoteResult.log());

        gateway.tasks.add(task("google-2", "Reabrir local", null, TODAY, true));
        service.syncBidirectional("list-1");
        var secondMapping = mappingRepository.findByGoogleId("list-1", "google-2").orElseThrow();
        gateway.tasks.set(1, task("google-2", "Reabrir local", null, TODAY, false));

        var localResult = service.syncBidirectional("list-1");

        assertEquals(1, localResult.statusChangedLocal());
        assertFalse(taskRepository.findById(secondMapping.localTaskId()).orElseThrow().done());
        assertEquals(List.of("Status atualizado localmente: Reabrir local"),
                localResult.log());
    }

    @Test
    void mappingFailureCompensatesRemoteCreationAndRetryLeavesOneTask() throws Exception {
        long localId = taskRepository.saveReturningId(
                "Exportação recuperável", null, TODAY, "Geral");
        Task localTask = taskRepository.findById(localId).orElseThrow();
        AtomicInteger attempts = new AtomicInteger();
        GoogleTasksMappingRepository failOnce = new GoogleTasksMappingRepository(database) {
            @Override
            public void upsert(long taskId, String listId, String googleTaskId) {
                if (attempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("falha simulada no mapeamento");
                }
                super.upsert(taskId, listId, googleTaskId);
            }
        };
        GoogleTasksSyncService recoverable = new GoogleTasksSyncService(
                gateway, taskRepository, failOnce,
                new GoogleTasksSyncRepository(database),
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalStateException.class,
                () -> recoverable.exportLocalTask("list-1", localTask));
        assertEquals(0, gateway.tasks.size());
        assertEquals(1, gateway.deleteCalls.get());

        assertTrue(recoverable.exportLocalTask("list-1", localTask));
        assertEquals(1, gateway.tasks.size());
        assertEquals(2, gateway.createCalls.get());
        assertTrue(failOnce.findByLocalId(localId).isPresent());
    }

    private static GTask task(String id, String title, String notes,
                              LocalDate dueDate, boolean completed) {
        String due = dueDate == null ? null : dueDate + "T00:00:00.000Z";
        return new GTask(id, title, notes, due, completed,
                completed ? "completed" : "needsAction");
    }

    private static GTask deletedTask(String id, String title) {
        return new GTask(id, title, null, null, false, "needsAction",
                "2026-08-27T12:00:00Z", true);
    }

    private long createConflict(String localTitle, String googleTitle) throws Exception {
        gateway.tasks.add(task("google-1", "Original", null, TODAY, false));
        service.syncBidirectional("list-1");
        var mapping = mappingRepository.findByGoogleId("list-1", "google-1").orElseThrow();
        taskRepository.updateFromGoogle(mapping.localTaskId(), localTitle, null, TODAY);
        gateway.tasks.set(0, task("google-1", googleTitle, null, TODAY, false));
        service.syncBidirectional("list-1");
        return mapping.id();
    }

    private static final class FakeGateway implements GoogleTasksGateway {
        private final List<GTask> tasks = new ArrayList<>();
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger updateCalls = new AtomicInteger();
        private final AtomicInteger deleteCalls = new AtomicInteger();
        private final AtomicInteger reopenCalls = new AtomicInteger();
        private IOException listFailure;

        @Override
        public List<GTask> listTasks(String taskListId, boolean showCompleted)
                throws IOException {
            if (listFailure != null) throw listFailure;
            return List.copyOf(tasks);
        }

        @Override
        public String createTask(String taskListId, String title, String notes, LocalDate dueDate) {
            createCalls.incrementAndGet();
            String id = "created-" + sequence.incrementAndGet();
            tasks.add(task(id, title, notes, dueDate, false));
            return id;
        }

        @Override
        public void completeTask(String taskListId, String taskId) throws IOException {
            int index = indexOf(taskId);
            GTask current = tasks.get(index);
            tasks.set(index, new GTask(current.id(), current.title(), current.notes(),
                    current.due(), true, "completed"));
        }

        @Override
        public void reopenTask(String taskListId, String taskId) throws IOException {
            reopenCalls.incrementAndGet();
            int index = indexOf(taskId);
            GTask current = tasks.get(index);
            tasks.set(index, new GTask(current.id(), current.title(), current.notes(),
                    current.due(), false, "needsAction"));
        }

        @Override
        public void updateTask(String taskListId, String taskId, String title,
                               String notes, LocalDate dueDate) throws IOException {
            updateCalls.incrementAndGet();
            int index = indexOf(taskId);
            tasks.set(index, task(taskId, title, notes, dueDate, false));
        }

        @Override
        public void deleteTask(String taskListId, String taskId) throws IOException {
            deleteCalls.incrementAndGet();
            tasks.remove(indexOf(taskId));
        }

        private int indexOf(String taskId) throws IOException {
            for (int index = 0; index < tasks.size(); index++) {
                if (tasks.get(index).id().equals(taskId)) return index;
            }
            throw new IOException("Tarefa Google não encontrada: " + taskId);
        }
    }
}
