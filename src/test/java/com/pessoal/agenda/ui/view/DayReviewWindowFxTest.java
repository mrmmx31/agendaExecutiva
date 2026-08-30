package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.DailyPlanCapacity;
import com.pessoal.agenda.model.DayReviewDecision;
import com.pessoal.agenda.repository.DailyPlanRepository;
import com.pessoal.agenda.repository.DayReviewRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.TaskSessionRepository;
import com.pessoal.agenda.service.DailyPlanService;
import com.pessoal.agenda.service.DayReviewService;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class DayReviewWindowFxTest {
    @TempDir
    Path tempDir;

    private Stage primaryStage;
    private ThemeManager.Theme originalTheme;
    private Database database;
    private DailyPlanRepository dailyPlanRepository;
    private DayReviewService service;
    private TaskRepository tasks;
    private LocalDate today;
    private long essentialId;
    private long openId;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now();
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        dailyPlanRepository = new DailyPlanRepository(database);
        tasks = new TaskRepository(database);
        TaskSessionRepository sessions = new TaskSessionRepository(database);
        DailyPlanService plans = new DailyPlanService(dailyPlanRepository, tasks);
        service = new DayReviewService(
                dailyPlanRepository, tasks, sessions, new DayReviewRepository(database));

        essentialId = tasks.saveReturningId("Preparar proposta", "", today, "Trabalho");
        long completed = tasks.saveReturningId("Responder mensagens", "", today, "Trabalho");
        openId = tasks.saveReturningId("Separar documentos", "", today, "Trabalho");
        plans.savePlan(today, DailyPlanCapacity.NORMAL, essentialId, List.of(completed, openId));
        tasks.markDone(completed);
        sessions.save(essentialId, "Preparar proposta", today, 35, "Bloco da manhã");

        FxTestSupport.run(() -> {
            originalTheme = ThemeManager.getInstance().getTheme();
            primaryStage = new Stage();
            primaryStage.setScene(new Scene(new StackPane(), 1000, 700));
            primaryStage.show();
            WindowManager.initialize(primaryStage);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(originalTheme);
            WindowManager.closeAll();
            primaryStage.close();
        });
    }

    @Test
    void showsShortSummaryAndClosesDayWithOptionalNote() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        FxTestSupport.run(() -> {
            new DayReviewWindow(service, today, changes::incrementAndGet).show();
            Scene scene = reviewStage().getScene();

            assertEquals("Concluídas: 1 · Sessões: 1 · Em aberto: 2",
                    text(scene, "#day-review-summary"));
            assertEquals(1, list(scene, "#day-review-completed").getItems().size());
            assertEquals(1, list(scene, "#day-review-sessions").getItems().size());
            assertEquals(2, scene.getRoot().lookupAll(".day-review-decision").size());
            ((TextArea) scene.lookup("#day-review-note")).setText("  Avancei no essencial  ");

            button(scene, "#day-review-close-day").fire();

            assertEquals(1, changes.get());
            assertTrue(dailyPlanRepository.findByDate(today).orElseThrow().closedAt() != null);
            assertEquals("Avancei no essencial",
                    dailyPlanRepository.findByDate(today).orElseThrow().closingNote());
            assertFalse(button(scene, "#day-review-close-day").isManaged());
            assertTrue(button(scene, "#day-review-reopen").isManaged());
            assertTrue(text(scene, "#day-review-status").startsWith("Dia encerrado"));
        });
    }

    @Test
    void appliesItemDecisionsAndOptionalTomorrowStart() throws Exception {
        FxTestSupport.run(() -> {
            new DayReviewWindow(service, today, () -> {}).show();
            Scene scene = reviewStage().getScene();
            decision(scene, essentialId).setValue(DayReviewDecision.TOMORROW);
            decision(scene, openId).setValue(DayReviewDecision.COMPLETE);
            ComboBox<?> tomorrow = (ComboBox<?>) scene.lookup("#day-review-tomorrow-initial");

            assertEquals(1, tomorrow.getItems().size());
            tomorrow.getSelectionModel().selectFirst();
            button(scene, "#day-review-close-day").fire();

            assertEquals(today.plusDays(1), tasks.findById(essentialId).orElseThrow().dueDate());
            assertTrue(tasks.findById(openId).orElseThrow().done());
            assertEquals(essentialId, dailyPlanRepository.findByDate(today.plusDays(1))
                    .orElseThrow().essentialItem().orElseThrow().taskId());
            assertTrue(button(scene, "#day-review-reopen").isManaged());
        });
    }

    @Test
    void reopeningKeepsTasksAndSessionsUntouched() throws Exception {
        service.closeDay(today, "Fechamento inicial");
        AtomicInteger changes = new AtomicInteger();
        FxTestSupport.run(() -> {
            new DayReviewWindow(service, today, changes::incrementAndGet).show();
            Scene scene = reviewStage().getScene();

            button(scene, "#day-review-reopen").fire();

            assertEquals(1, changes.get());
            assertEquals(null, dailyPlanRepository.findByDate(today).orElseThrow().closedAt());
            assertEquals(3, dailyPlanRepository.findByDate(today).orElseThrow().items().size());
            assertEquals(1, list(scene, "#day-review-completed").getItems().size());
            assertEquals(1, list(scene, "#day-review-sessions").getItems().size());
            assertTrue(button(scene, "#day-review-close-day").isManaged());
        });
    }

    @Test
    void failedClosePreservesTypedNoteAndOffersRetry() throws Exception {
        FxTestSupport.run(() -> {
            new DayReviewWindow(service, today, () -> {}).show();
            Scene scene = reviewStage().getScene();
            ((TextArea) scene.lookup("#day-review-note")).setText("Não perder esta nota");
            decision(scene, essentialId).setValue(DayReviewDecision.TOMORROW);
        });
        database.execute("DROP TABLE daily_plan_items");

        FxTestSupport.run(() -> {
            Scene scene = reviewStage().getScene();
            button(scene, "#day-review-close-day").fire();

            assertEquals("Não perder esta nota",
                    ((TextArea) scene.lookup("#day-review-note")).getText());
            assertEquals(DayReviewDecision.TOMORROW, decision(scene, essentialId).getValue());
            assertEquals("Não foi possível encerrar. A nota e as escolhas foram preservadas.",
                    text(scene, "#day-review-status"));
            assertTrue(button(scene, "#day-review-close-day").isManaged());
        });
    }

    @Test
    void minimumWindowAndDarkThemeRemainReadable() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            new DayReviewWindow(service, today, () -> {}).show();
            Stage stage = reviewStage();
            stage.setWidth(stage.getMinWidth());
            stage.setHeight(stage.getMinHeight());
            Scene scene = stage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertInsideHorizontally(scene, "#day-review-summary", "#day-review-completed",
                    "#day-review-sessions", "#day-review-open-decisions",
                    "#day-review-tomorrow-initial", "#day-review-note",
                    "#day-review-close-day", "#day-review-close-window");
            for (Node node : scene.getRoot().lookupAll(".label")) {
                Color color = (Color) ((Label) node).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + ((Label) node).getText());
            }
            for (ComboBox<?> combo : List.of(
                    decision(scene, essentialId),
                    (ComboBox<?>) scene.lookup("#day-review-tomorrow-initial"))) {
                Color color = (Color) ((ListCell<?>) combo.lookup(".list-cell")).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        "Seletor com texto escuro no tema escuro");
            }
        });
    }

    private static Stage reviewStage() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage)
                .map(window -> (Stage) window)
                .filter(stage -> "Encerrar meu dia".equals(stage.getTitle()))
                .findFirst().orElseThrow();
    }

    private static String text(Scene scene, String selector) {
        return ((Label) scene.lookup(selector)).getText();
    }

    private static Button button(Scene scene, String selector) {
        return (Button) scene.lookup(selector);
    }

    private static ListView<?> list(Scene scene, String selector) {
        return (ListView<?>) scene.lookup(selector);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<DayReviewDecision> decision(Scene scene, long taskId) {
        return (ComboBox<DayReviewDecision>) scene.lookup("#day-review-decision-" + taskId);
    }

    private static void assertInsideHorizontally(Scene scene, String... selectors) {
        for (String selector : selectors) {
            Node node = scene.lookup(selector);
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0, selector + " ultrapassou a esquerda");
            assertTrue(bounds.getMaxX() <= scene.getWidth(), selector + " ultrapassou a direita");
        }
    }
}
