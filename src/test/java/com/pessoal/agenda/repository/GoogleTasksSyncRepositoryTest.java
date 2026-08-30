package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleTasksSyncRepositoryTest {
    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("google-sync-repository.db"));
        database.runMigrations();
    }

    @Test
    void rollsBackTaskWhenMappingStepFailsAndAllowsCleanRetry() {
        GoogleTasksSyncRepository failing = new GoogleTasksSyncRepository(
                database, () -> { throw new IllegalStateException("falha simulada"); });

        assertThrows(IllegalStateException.class, () -> failing.importTask(
                "list-1", "google-1", "Preparar relatório", null,
                LocalDate.of(2026, 8, 28), false));

        assertEquals(0, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertEquals(0, database.queryInt("SELECT COUNT(*) FROM google_tasks_mapping"));

        GoogleTasksSyncRepository.ImportResult retry = new GoogleTasksSyncRepository(database)
                .importTask("list-1", "google-1", "Preparar relatório", null,
                        LocalDate.of(2026, 8, 28), false);

        assertTrue(retry.created());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM google_tasks_mapping"));
    }

    @Test
    void repeatedImportReturnsOriginalIdentityWithoutWritingAgain() {
        GoogleTasksSyncRepository repository = new GoogleTasksSyncRepository(database);
        var first = repository.importTask(
                "list-1", "google-1", "Título", "Notas",
                LocalDate.of(2026, 8, 28), true);
        var repeated = repository.importTask(
                "list-1", "google-1", "Título alterado", null,
                LocalDate.of(2026, 8, 29), false);

        assertTrue(first.created());
        assertEquals(false, repeated.created());
        assertEquals(first.localTaskId(), repeated.localTaskId());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM google_tasks_mapping"));
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks WHERE done=1"));
    }
}
