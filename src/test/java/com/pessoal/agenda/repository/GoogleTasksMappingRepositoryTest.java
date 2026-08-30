package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTasksMappingRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void countsAndClearsOnlyMappings() {
        Database database = new Database(tempDir.resolve("google-mappings.db"));
        database.runMigrations();
        long localTaskId = new TaskRepository(database).saveReturningId(
                "Tarefa local", "", LocalDate.of(2026, 8, 30), "Geral");
        GoogleTasksMappingRepository repository = new GoogleTasksMappingRepository(database);
        repository.upsert(localTaskId, "list-1", "task-1");

        assertEquals(1, repository.count());
        repository.deleteAll();

        assertEquals(0, repository.count());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
    }
}
