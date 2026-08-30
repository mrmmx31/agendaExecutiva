package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.DailyPlan;
import com.pessoal.agenda.model.DailyPlanCapacity;
import com.pessoal.agenda.model.DailyPlanItem;
import com.pessoal.agenda.model.DailyPlanRole;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.DailyPlanRepository;
import com.pessoal.agenda.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyPlanServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);

    @TempDir
    Path tempDir;

    private Database database;
    private TaskRepository taskRepository;
    private DailyPlanRepository dailyPlanRepository;
    private DailyPlanService service;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        taskRepository = new TaskRepository(database);
        dailyPlanRepository = new DailyPlanRepository(database);
        service = new DailyPlanService(
                dailyPlanRepository, taskRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void migrationIsIdempotent() {
        database.runMigrations();

        assertEquals(1, database.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='daily_plans'"));
        assertEquals(1, database.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='daily_plan_items'"));
    }

    @Test
    void savesAndReloadsEssentialAndSupportTasksInOrder() {
        long essential = createTask("Preparar proposta");
        long supportOne = createTask("Responder mensagens");
        long supportTwo = createTask("Separar documentos");

        DailyPlan saved = service.savePlan(
                TODAY, DailyPlanCapacity.NORMAL, essential, List.of(supportOne, supportTwo));

        assertEquals(NOW, saved.createdAt());
        assertEquals(essential, saved.essentialItem().orElseThrow().taskId());
        assertEquals(List.of(supportOne, supportTwo),
                saved.supportItems().stream().map(item -> item.taskId()).toList());
        assertEquals(List.of(DailyPlanRole.ESSENTIAL, DailyPlanRole.SUPPORT, DailyPlanRole.SUPPORT),
                saved.items().stream().map(item -> item.role()).toList());
    }

    @Test
    void editsExistingPlanWithoutChangingCreationTime() {
        long first = createTask("Primeira essencial");
        long replacement = createTask("Nova essencial");
        long support = createTask("Apoio");
        service.savePlan(TODAY, DailyPlanCapacity.NORMAL, first, List.of(support));

        DailyPlan edited = service.savePlan(
                TODAY, DailyPlanCapacity.REDUCED, replacement, List.of());

        assertEquals(NOW, edited.createdAt());
        assertEquals(DailyPlanCapacity.REDUCED, edited.capacity());
        assertEquals(replacement, edited.essentialItem().orElseThrow().taskId());
        assertTrue(edited.supportItems().isEmpty());
    }

    @Test
    void reordersSupportsWithoutModifyingOriginalTasks() {
        long essential = createTask("Essencial");
        long firstSupport = createTask("Primeiro apoio");
        long secondSupport = createTask("Segundo apoio");
        Task essentialBefore = taskRepository.findById(essential).orElseThrow();
        Task firstBefore = taskRepository.findById(firstSupport).orElseThrow();
        Task secondBefore = taskRepository.findById(secondSupport).orElseThrow();
        service.savePlan(TODAY, DailyPlanCapacity.NORMAL,
                essential, List.of(firstSupport, secondSupport));

        DailyPlan reordered = service.savePlan(TODAY, DailyPlanCapacity.NORMAL,
                essential, List.of(secondSupport, firstSupport));

        assertEquals(List.of(secondSupport, firstSupport),
                reordered.supportItems().stream().map(DailyPlanItem::taskId).toList());
        assertEquals(essentialBefore, taskRepository.findById(essential).orElseThrow());
        assertEquals(firstBefore, taskRepository.findById(firstSupport).orElseThrow());
        assertEquals(secondBefore, taskRepository.findById(secondSupport).orElseThrow());
    }

    @Test
    void rejectsInvalidSelections() {
        long essential = createTask("Essencial");
        long supportOne = createTask("Apoio 1");
        long supportTwo = createTask("Apoio 2");
        long supportThree = createTask("Apoio 3");

        assertThrows(IllegalArgumentException.class, () -> service.savePlan(
                TODAY, DailyPlanCapacity.REDUCED, essential, List.of(supportOne)));
        assertThrows(IllegalArgumentException.class, () -> service.savePlan(
                TODAY, DailyPlanCapacity.NORMAL, essential,
                List.of(supportOne, supportTwo, supportThree)));
        assertThrows(IllegalArgumentException.class, () -> service.savePlan(
                TODAY, DailyPlanCapacity.NORMAL, essential, List.of(essential)));
    }

    @Test
    void hidesActivePlanWhenEssentialTaskIsDeletedButPreservesPlanRecord() {
        long essential = createTask("Tarefa removida");
        service.savePlan(TODAY, DailyPlanCapacity.NORMAL, essential, List.of());

        taskRepository.deleteById(essential);

        assertTrue(service.findByDate(TODAY).isEmpty());
        assertTrue(dailyPlanRepository.findByDate(TODAY).isPresent());
        assertTrue(dailyPlanRepository.findByDate(TODAY).orElseThrow().items().isEmpty());
    }

    @Test
    void rejectsCompletedTask() {
        long essential = createTask("Tarefa concluida");
        taskRepository.markDone(essential);

        assertThrows(IllegalArgumentException.class, () -> service.savePlan(
                TODAY, DailyPlanCapacity.NORMAL, essential, List.of()));
        assertFalse(dailyPlanRepository.findByDate(TODAY).isPresent());
    }

    @Test
    void hidesActivePlanWhenEssentialTaskIsCompletedButPreservesReviewHistory() {
        long essential = createTask("Tarefa concluída depois do planejamento");
        service.savePlan(TODAY, DailyPlanCapacity.NORMAL, essential, List.of());

        taskRepository.markDone(essential);

        assertTrue(service.findByDate(TODAY).isEmpty());
        assertEquals(essential, dailyPlanRepository.findByDate(TODAY).orElseThrow()
                .essentialItem().orElseThrow().taskId());
    }

    @Test
    void removesCompletedSupportWithoutInvalidatingPlan() {
        long essential = createTask("Essencial aberta");
        long completedSupport = createTask("Apoio concluído");
        long openSupport = createTask("Apoio aberto");
        service.savePlan(TODAY, DailyPlanCapacity.NORMAL,
                essential, List.of(completedSupport, openSupport));

        taskRepository.markDone(completedSupport);
        DailyPlan restored = service.findByDate(TODAY).orElseThrow();

        assertEquals(essential, restored.essentialItem().orElseThrow().taskId());
        assertEquals(List.of(openSupport),
                restored.supportItems().stream().map(DailyPlanItem::taskId).toList());
        assertEquals(0, restored.supportItems().getFirst().position());
        assertEquals(List.of(completedSupport, openSupport),
                dailyPlanRepository.findByDate(TODAY).orElseThrow().supportItems().stream()
                        .map(DailyPlanItem::taskId).toList());
    }

    @Test
    void failedReplacementKeepsPreviousPlanIntact() {
        long essential = createTask("Plano preservado");
        DailyPlan original = service.savePlan(
                TODAY, DailyPlanCapacity.NORMAL, essential, List.of());
        DailyPlan invalidReplacement = new DailyPlan(
                TODAY, DailyPlanCapacity.NORMAL, original.createdAt(), null, null,
                List.of(new DailyPlanItem(
                        0, TODAY, 999_999L, DailyPlanRole.ESSENTIAL, 0)));

        assertThrows(RuntimeException.class, () -> dailyPlanRepository.save(invalidReplacement));

        DailyPlan persisted = dailyPlanRepository.findByDate(TODAY).orElseThrow();
        assertEquals(essential, persisted.essentialItem().orElseThrow().taskId());
    }

    private long createTask(String title) {
        taskRepository.save(title, "", TODAY, "Geral");
        return database.queryInt("SELECT MAX(id) FROM tasks");
    }
}
