package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.DailyPlanCapacity;
import com.pessoal.agenda.model.DayReviewDecision;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.DailyPlanRepository;
import com.pessoal.agenda.repository.DayReviewRepository;
import com.pessoal.agenda.repository.InboxCaptureRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.TaskSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayReviewServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);
    private static final Instant NOW = Instant.parse("2026-08-28T22:15:00Z");

    @TempDir
    Path tempDir;

    private DailyPlanRepository dailyPlanRepository;
    private Database database;
    private TaskRepository taskRepository;
    private TaskSessionRepository sessionRepository;
    private DailyPlanService dailyPlanService;
    private DayReviewService service;
    private long essentialId;
    private long completedSupportId;
    private long openSupportId;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        dailyPlanRepository = new DailyPlanRepository(database);
        taskRepository = new TaskRepository(database);
        sessionRepository = new TaskSessionRepository(database);
        dailyPlanService = new DailyPlanService(dailyPlanRepository, taskRepository,
                Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC));
        service = new DayReviewService(
                dailyPlanRepository, taskRepository, sessionRepository,
                new DayReviewRepository(database),
                Clock.fixed(NOW, ZoneOffset.UTC));

        essentialId = createTask("Preparar proposta");
        completedSupportId = createTask("Responder mensagens");
        openSupportId = createTask("Separar documentos");
        dailyPlanService.savePlan(TODAY, DailyPlanCapacity.NORMAL,
                essentialId, List.of(completedSupportId, openSupportId));
        taskRepository.markDone(completedSupportId);
        sessionRepository.save(essentialId, "Tarefa:#" + essentialId + " — Preparar proposta",
                TODAY, 42, "Primeiro bloco");
    }

    @Test
    void summarizesCompletedTasksSessionsAndOpenPlanItems() {
        var summary = service.summary(TODAY).orElseThrow();

        assertEquals(List.of("Responder mensagens"),
                summary.completedTasks().stream().map(Task::title).toList());
        assertEquals(List.of("Preparar proposta", "Separar documentos"),
                summary.openTasks().stream().map(Task::title).toList());
        assertEquals(1, summary.sessions().size());
        assertEquals(42, summary.sessions().getFirst().durationMinutes());
        assertEquals(3, summary.plan().items().size());
    }

    @Test
    void closingPersistsTimestampAndNormalizedOptionalNote() {
        var closed = service.closeDay(TODAY, "  Avancei no essencial  ");

        assertEquals(NOW, closed.plan().closedAt());
        assertEquals("Avancei no essencial", closed.plan().closingNote());
        assertEquals(3, closed.plan().items().size());
        assertTrue(taskRepository.findById(essentialId).orElseThrow().done() == false);
    }

    @Test
    void repeatedCloseKeepsOriginalTimestampAndNote() {
        service.closeDay(TODAY, "Primeira nota");
        DayReviewService laterService = new DayReviewService(
                dailyPlanRepository, taskRepository, sessionRepository,
                new DayReviewRepository(database),
                Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));

        var repeated = laterService.closeDay(TODAY, "Não deve substituir");

        assertEquals(NOW, repeated.plan().closedAt());
        assertEquals("Primeira nota", repeated.plan().closingNote());
    }

    @Test
    void reopeningOnlyClearsClosingMetadata() {
        service.closeDay(TODAY, "Nota preservada até reabrir");
        List<Task> tasksBefore = List.of(
                taskRepository.findById(essentialId).orElseThrow(),
                taskRepository.findById(completedSupportId).orElseThrow(),
                taskRepository.findById(openSupportId).orElseThrow());

        var reopened = service.reopenDay(TODAY);

        assertNull(reopened.plan().closedAt());
        assertNull(reopened.plan().closingNote());
        assertEquals(3, reopened.plan().items().size());
        assertEquals(tasksBefore, List.of(
                taskRepository.findById(essentialId).orElseThrow(),
                taskRepository.findById(completedSupportId).orElseThrow(),
                taskRepository.findById(openSupportId).orElseThrow()));
        assertEquals(1, reopened.sessions().size());
    }

    @Test
    void blankClosingNoteIsStoredAsNull() {
        assertNull(service.closeDay(TODAY, "   ").plan().closingNote());
    }

    @Test
    void missingPlanCannotBeClosedOrReopened() {
        LocalDate missing = TODAY.plusDays(1);

        assertTrue(service.summary(missing).isEmpty());
        assertThrows(IllegalStateException.class, () -> service.closeDay(missing, null));
        assertThrows(IllegalStateException.class, () -> service.reopenDay(missing));
    }

    @Test
    void appliesTomorrowAndKeepDateAndPreparesOptionalFirstTask() {
        var closed = service.closeDay(TODAY, "Dia revisado", Map.of(
                essentialId, DayReviewDecision.TOMORROW,
                openSupportId, DayReviewDecision.KEEP_DATE), essentialId);

        assertEquals(NOW, closed.plan().closedAt());
        assertEquals(TODAY.plusDays(1),
                taskRepository.findById(essentialId).orElseThrow().dueDate());
        assertEquals(TODAY,
                taskRepository.findById(openSupportId).orElseThrow().dueDate());
        var tomorrowPlan = dailyPlanRepository.findByDate(TODAY.plusDays(1)).orElseThrow();
        assertEquals(DailyPlanCapacity.REDUCED, tomorrowPlan.capacity());
        assertEquals(essentialId, tomorrowPlan.essentialItem().orElseThrow().taskId());
        assertEquals(1, tomorrowPlan.items().size());
    }

    @Test
    void returnsTaskToInboxAndCompletesAnotherInOneReview() {
        service.closeDay(TODAY, null, Map.of(
                essentialId, DayReviewDecision.RETURN_TO_INBOX,
                openSupportId, DayReviewDecision.COMPLETE), null);

        assertEquals(TaskStatus.CANCELADA,
                taskRepository.findById(essentialId).orElseThrow().status());
        assertTrue(taskRepository.findById(openSupportId).orElseThrow().done());
        var captures = new InboxCaptureRepository(database).findUnclassified(10);
        assertEquals(1, captures.size());
        assertEquals("Preparar proposta", captures.getFirst().rawText());
    }

    @Test
    void requiresOneDecisionPerOpenItemAndTomorrowInitialAmongPostponedTasks() {
        assertThrows(IllegalArgumentException.class, () -> service.closeDay(
                TODAY, null, Map.of(essentialId, DayReviewDecision.KEEP_DATE), null));
        assertThrows(IllegalArgumentException.class, () -> service.closeDay(
                TODAY, null, Map.of(
                        essentialId, DayReviewDecision.KEEP_DATE,
                        openSupportId, DayReviewDecision.TOMORROW), essentialId));

        assertNull(dailyPlanRepository.findByDate(TODAY).orElseThrow().closedAt());
    }

    @Test
    void existingTomorrowPlanRejectsReplacementAndRollsBackEveryDecision() {
        long existingTomorrowTask = taskRepository.saveReturningId(
                "Compromisso já escolhido", "", TODAY.plusDays(1), "Geral");
        dailyPlanService.savePlan(TODAY.plusDays(1), DailyPlanCapacity.REDUCED,
                existingTomorrowTask, List.of());

        assertThrows(IllegalStateException.class, () -> service.closeDay(
                TODAY, null, Map.of(
                        essentialId, DayReviewDecision.TOMORROW,
                        openSupportId, DayReviewDecision.COMPLETE), essentialId));

        assertEquals(TODAY, taskRepository.findById(essentialId).orElseThrow().dueDate());
        assertTrue(!taskRepository.findById(openSupportId).orElseThrow().done());
        assertNull(dailyPlanRepository.findByDate(TODAY).orElseThrow().closedAt());
        assertEquals(existingTomorrowTask, dailyPlanRepository.findByDate(TODAY.plusDays(1))
                .orElseThrow().essentialItem().orElseThrow().taskId());
    }

    private long createTask(String title) {
        return taskRepository.saveReturningId(title, "", TODAY, "Geral");
    }
}
