package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.TaskSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskSessionRepositoryTest {

    @TempDir
    Path tempDir;

    private Database database;
    private TaskSessionRepository repository;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        repository = new TaskSessionRepository(database);
    }

    @Test
    void keepsTaskLinkWhenSubjectChanges() {
        LocalDate today = LocalDate.now();
        repository.save(42L, "Tarefa:#42 Texto inicial", today, 25, "nota");

        TaskSession saved = repository.findByTaskId(42L).getFirst();
        repository.update(saved.id(), "Título editado sem identificador", 30, "outra nota");

        List<TaskSession> sessions = repository.findByTaskId(42L);
        assertEquals(1, sessions.size());
        assertEquals(42L, sessions.getFirst().taskId());
        assertEquals("Título editado sem identificador", sessions.getFirst().subject());
    }

    @Test
    void appliesDateRangeToTaskHistory() {
        LocalDate today = LocalDate.now();
        repository.save(7L, "Tarefa:#7 Antiga", today.minusDays(10), 10, null);
        repository.save(7L, "Tarefa:#7 Atual", today, 20, null);

        List<TaskSession> sessions = repository.findByTaskId(7L, today.minusDays(1), today);

        assertEquals(1, sessions.size());
        assertEquals("Tarefa:#7 Atual", sessions.getFirst().subject());
    }

    @Test
    void findsLegacySessionBySubjectFallback() {
        LocalDate today = LocalDate.now();
        database.execute("""
                INSERT INTO study_sessions(subject, session_date, duration_minutes, notes)
                VALUES(?, ?, ?, ?)
                """, "Tarefa:#9 Sessão antiga", today.toString(), 15, null);

        List<TaskSession> sessions = repository.findByTaskId(9L);

        assertEquals(1, sessions.size());
        assertEquals(9L, sessions.getFirst().taskId());
    }
}
