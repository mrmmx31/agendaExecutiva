package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.FocusContext;
import com.pessoal.agenda.repository.FocusContextRepository;
import com.pessoal.agenda.repository.TaskRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusContextServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T14:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    @TempDir
    Path tempDir;

    private Database database;
    private TaskRepository taskRepository;
    private FocusContextRepository repository;
    private FocusContextService service;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        taskRepository = new TaskRepository(database);
        repository = new FocusContextRepository(database);
        service = new FocusContextService(repository, taskRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void migrationIsIdempotent() {
        database.runMigrations();

        assertEquals(1, database.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='focus_context'"));
    }

    @Test
    void preservesCurrentContextWhenServiceIsRecreated() {
        long taskId = createTask("Preparar apresentação");

        service.interrupt(taskId, "  Revisar o slide de resultados  ");
        FocusContext restored = new FocusContextService(
                new FocusContextRepository(database), new TaskRepository(database),
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC))
                .current().orElseThrow();

        assertEquals(taskId, restored.taskId());
        assertEquals("Revisar o slide de resultados", restored.resumeNote());
        assertEquals(NOW, restored.interruptedAt());
        assertEquals(NOW, restored.updatedAt());
    }

    @Test
    void replacesPreviousContextAndKeepsOnlyOneCurrentRow() {
        long firstTask = createTask("Primeira tarefa");
        long secondTask = createTask("Segunda tarefa");
        service.interrupt(firstTask, "Onde parei primeiro");

        FocusContext current = service.interrupt(secondTask, "Próximo passo atual");

        assertEquals(secondTask, current.taskId());
        assertEquals(secondTask, service.current().orElseThrow().taskId());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM focus_context"));
    }

    @Test
    void failedReplacementKeepsPreviousContextIntact() {
        long taskId = createTask("Contexto preservado");
        service.interrupt(taskId, "Continuar daqui");
        FocusContext invalidReplacement = new FocusContext(
                999_999L, "Não deve substituir", NOW, NOW);

        assertThrows(RuntimeException.class,
                () -> repository.replaceCurrent(invalidReplacement));

        FocusContext persisted = service.current().orElseThrow();
        assertEquals(taskId, persisted.taskId());
        assertEquals("Continuar daqui", persisted.resumeNote());
    }

    @Test
    void rejectsBlankMissingAndClosedTasksWithoutLosingCurrentContext() {
        long openTask = createTask("Tarefa aberta");
        long completedTask = createTask("Tarefa concluída");
        long cancelledTask = createTask("Tarefa cancelada");
        service.interrupt(openTask, "Contexto que deve permanecer");
        taskRepository.markDone(completedTask);
        database.execute("UPDATE tasks SET status='CANCELADA' WHERE id=?", cancelledTask);

        assertThrows(IllegalArgumentException.class,
                () -> service.interrupt(openTask, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> service.interrupt(999_999L, "Uma pista"));
        assertThrows(IllegalStateException.class,
                () -> service.interrupt(completedTask, "Uma pista"));
        assertThrows(IllegalStateException.class,
                () -> service.interrupt(cancelledTask, "Uma pista"));
        assertEquals(openTask, service.current().orElseThrow().taskId());
    }

    @Test
    void clearsOnlyTheContextForTheTaskBeingResumed() {
        long taskId = createTask("Retomar relatório");
        service.interrupt(taskId, "Conferir os totais");

        assertTrue(service.completeResume(taskId + 1).isEmpty());
        assertTrue(service.current().isPresent());
        assertEquals(taskId, service.completeResume(taskId).orElseThrow().taskId());
        assertFalse(service.current().isPresent());
    }

    @Test
    void deletingTaskRemovesItsContextByForeignKeyCascade() {
        long taskId = createTask("Tarefa descartada");
        service.interrupt(taskId, "Pista temporária");

        taskRepository.deleteById(taskId);

        assertTrue(service.current().isEmpty());
        assertEquals(0, database.queryInt("SELECT COUNT(*) FROM focus_context"));
    }

    private long createTask(String title) {
        return taskRepository.saveReturningId(title, "", TODAY, "Geral");
    }
}
