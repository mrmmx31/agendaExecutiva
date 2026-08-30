package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.TimerRecovery;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.TimerRecoveryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTimerRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T16:00:00Z");

    @TempDir
    Path tempDir;

    private Database database;
    private TaskRepository taskRepository;
    private TimerRecoveryRepository repository;
    private TaskTimerService timerService;
    private TaskTimerRecoveryService service;
    private long taskId;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        taskRepository = new TaskRepository(database);
        repository = new TimerRecoveryRepository(database);
        timerService = TaskTimerService.get();
        timerService.stop();
        taskId = taskRepository.saveReturningId(
                "Sessão recuperável", "", LocalDate.of(2026, 8, 28), "Trabalho");
        service = recoveryService(NOW);
    }

    @AfterEach
    void tearDown() {
        service.stopTracking();
        timerService.stop();
    }

    @Test
    void migrationIsIdempotent() {
        database.runMigrations();

        assertEquals(1, database.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='timer_recovery'"));
    }

    @Test
    void checkpointSurvivesRecreatedRecoveryService() {
        service.startTracking();
        timerService.restore(taskId, 91, true);
        service.stopTracking();
        timerService.stop();

        TaskTimerRecoveryService recreated = recoveryService(NOW.plusSeconds(600));
        var candidate = recreated.pending().orElseThrow();

        assertEquals(taskId, candidate.task().id());
        assertEquals(91, candidate.recovery().elapsedSeconds());
        assertTrue(candidate.recovery().wasRunning());
        assertEquals(NOW, candidate.recovery().updatedAt());
    }

    @Test
    void runningTimerCreatesCheckpointWithoutWaitingForShutdown() throws Exception {
        service.startTracking();
        timerService.start(taskId);

        long deadline = System.currentTimeMillis() + 3_000;
        while (repository.findPending().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        TimerRecovery checkpoint = repository.findPending().orElseThrow();
        assertEquals(taskId, checkpoint.taskId());
        assertTrue(checkpoint.elapsedSeconds() >= 1);
        assertTrue(checkpoint.wasRunning());
    }

    @Test
    void recoverRestoresExactElapsedTimeButAlwaysPaused() {
        repository.save(new TimerRecovery(taskId, 367, true, NOW.minusSeconds(900)));

        var recovered = service.recover();

        assertEquals(taskId, recovered.task().id());
        assertEquals(367, timerService.getElapsedSeconds());
        assertEquals(taskId, timerService.getActiveTaskId());
        assertFalse(timerService.isRunning());
        TimerRecovery persisted = repository.findPending().orElseThrow();
        assertEquals(367, persisted.elapsedSeconds());
        assertFalse(persisted.wasRunning());
        assertEquals(NOW, persisted.updatedAt());
    }

    @Test
    void discardRemovesCheckpointWithoutCreatingElapsedState() {
        repository.save(new TimerRecovery(taskId, 125, false, NOW));

        service.discard();

        assertTrue(repository.findPending().isEmpty());
        assertEquals(null, timerService.getActiveTaskId());
        assertEquals(0, timerService.getElapsedSeconds());
    }

    @Test
    void stoppingTrackedTimerClearsCheckpoint() {
        service.startTracking();
        timerService.restore(taskId, 48, false);
        assertTrue(repository.findPending().isPresent());

        timerService.stop();

        assertTrue(repository.findPending().isEmpty());
    }

    @Test
    void deletedOrCompletedTaskInvalidatesStoredCheckpoint() {
        repository.save(new TimerRecovery(taskId, 60, true, NOW));
        taskRepository.markDone(taskId);

        assertTrue(service.pending().isEmpty());
        assertTrue(repository.findPending().isEmpty());

        long deletedTaskId = taskRepository.saveReturningId(
                "Tarefa removida", "", LocalDate.of(2026, 8, 28), "Trabalho");
        repository.save(new TimerRecovery(deletedTaskId, 30, false, NOW));
        taskRepository.deleteById(deletedTaskId);

        assertTrue(service.pending().isEmpty());
        assertTrue(repository.findPending().isEmpty());
    }

    private TaskTimerRecoveryService recoveryService(Instant instant) {
        return new TaskTimerRecoveryService(repository, taskRepository, timerService,
                Clock.fixed(instant, ZoneOffset.UTC));
    }
}
