package com.pessoal.agenda;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void deadlineAlertsKeepRecentAndCriticalTasksWithoutRepeatingOldNormalTasks() {
        Path databasePath = tempDir.resolve("agenda-test.db");
        Database database = new Database(databasePath);
        database.runMigrations();
        TaskRepository tasks = new TaskRepository(database);
        LocalDate today = LocalDate.now();
        tasks.saveReturningId("Pendente recente", "", today.minusDays(3), "Geral");
        tasks.saveReturningId("Pendente antiga normal", "", today.minusDays(20), "Geral");
        long criticalId = tasks.saveReturningId(
                "Pendente antiga crítica", "", today.minusDays(40), "Geral");
        database.execute("UPDATE tasks SET priority='CRITICA' WHERE id=?", criticalId);

        List<String> alerts = new DatabaseService(databasePath).listDeadlineAlerts();
        String text = String.join("\n", alerts);

        assertTrue(text.contains("Pendente recente"));
        assertTrue(text.contains("Pendente antiga crítica"));
        assertFalse(text.contains("Pendente antiga normal"));
        assertFalse(text.contains("atrasada"));
        assertTrue(text.contains("pendente há 3 dia(s)"));
    }

    @Test
    void legacyTaskLookupAndSessionRepositoryUseConfiguredDatabase() {
        Path databasePath = tempDir.resolve("legacy-task-lookup.db");
        Database database = new Database(databasePath);
        database.runMigrations();
        TaskRepository tasks = new TaskRepository(database);
        LocalDate start = LocalDate.of(2026, 8, 30);
        LocalDate end = start.plusDays(5);
        tasks.save("Executar protocolo", "Notas completas", start, "Pesquisa",
                ScheduleType.RANGE, end, "1,3,5", "09:15", "10:45",
                TaskPriority.ALTA, TaskStatus.EM_ANDAMENTO, 77L);
        long taskId = tasks.findOpenTasks().getFirst().id();
        DatabaseService service = new DatabaseService(databasePath);

        var task = service.findTaskById(taskId);

        assertEquals("Executar protocolo", task.title());
        assertEquals("Notas completas", task.notes());
        assertEquals(start, task.dueDate());
        assertEquals(end, task.endDate());
        assertEquals(ScheduleType.RANGE, task.scheduleType());
        assertEquals("1,3,5", task.recurrenceDays());
        assertEquals("09:15", task.startTime());
        assertEquals("10:45", task.endTime());
        assertEquals(TaskPriority.ALTA, task.priority());
        assertEquals(TaskStatus.EM_ANDAMENTO, task.status());
        assertEquals(77L, task.linkedProtocolId());
        assertNull(service.findTaskById(Long.MAX_VALUE));
        assertNull(service.findTaskById(null));

        service.getTaskSessionRepository().save(taskId, task.title(), start, 25, "Sessão isolada");
        var sessions = service.getTaskSessionRepository().findByTaskId(taskId);
        assertEquals(1, sessions.size());
        assertEquals(taskId, sessions.getFirst().taskId());
        assertEquals(25, sessions.getFirst().durationMinutes());
    }
}
