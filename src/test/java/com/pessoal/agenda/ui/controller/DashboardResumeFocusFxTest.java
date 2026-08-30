package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.DailyPlanRepository;
import com.pessoal.agenda.repository.FocusContextRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.LocalMetricsRepository;
import com.pessoal.agenda.service.DailyPlanService;
import com.pessoal.agenda.service.DayReviewService;
import com.pessoal.agenda.service.FocusContextService;
import com.pessoal.agenda.service.TaskTimerService;
import com.pessoal.agenda.service.LocalMetricsService;
import com.pessoal.agenda.ui.view.FxTestSupport;
import com.pessoal.agenda.ui.view.ThemeManager;
import com.pessoal.agenda.ui.view.WindowManager;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class DashboardResumeFocusFxTest {
    @TempDir
    Path tempDir;

    private Stage primaryStage;
    private ThemeManager.Theme originalTheme;
    private Database database;
    private TaskRepository taskRepository;
    private FocusContextService focusContextService;
    private DailyPlanService dailyPlanService;
    private DayReviewService dayReviewService;
    private Preferences preferences;
    private Preferences localMetricsPreferences;
    private LocalMetricsService localMetricsService;
    private Task task;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        taskRepository = new TaskRepository(database);
        long taskId = taskRepository.saveReturningId(
                "Validar retorno da integração", "", LocalDate.now(), "Trabalho");
        task = taskRepository.findById(taskId).orElseThrow();
        focusContextService = new FocusContextService(
                new FocusContextRepository(database), taskRepository);
        dailyPlanService = new DailyPlanService(
                new DailyPlanRepository(database), taskRepository);
        dayReviewService = new DayReviewService(
                new DailyPlanRepository(database), taskRepository,
                new com.pessoal.agenda.repository.TaskSessionRepository(database),
                new com.pessoal.agenda.repository.DayReviewRepository(database));
        preferences = Preferences.userRoot().node(
                "/agenda-tests/dashboard-resume-" + System.nanoTime());
        localMetricsPreferences = Preferences.userRoot().node(
                "/agenda-tests/dashboard-metrics-" + System.nanoTime());
        localMetricsService = new LocalMetricsService(
                new LocalMetricsRepository(database), localMetricsPreferences);

        FxTestSupport.run(() -> {
            originalTheme = ThemeManager.getInstance().getTheme();
            primaryStage = new Stage();
            primaryStage.setScene(new Scene(new StackPane(), 900, 650));
            primaryStage.show();
            WindowManager.initialize(primaryStage);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        TaskTimerService.get().stop();
        preferences.removeNode();
        localMetricsPreferences.removeNode();
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(originalTheme);
            WindowManager.closeAll();
            primaryStage.close();
        });
    }

    @Test
    void restoredDashboardShowsTaskClueAndOnlyTwoImmediateActions() throws Exception {
        focusContextService.interrupt(task.id(), "Validar o caso sem CNPJ");

        FxTestSupport.run(() -> {
            DashboardController controller = buildDashboard(ignored -> {});
            Scene scene = primaryStage.getScene();

            assertEquals("Retomada pendente", text(scene, "#dashboard-focus-mode"));
            assertEquals(task.title(), text(scene, "#dashboard-focus-title"));
            assertEquals("Onde você parou: Validar o caso sem CNPJ",
                    text(scene, "#dashboard-focus-detail"));
            assertEquals("Retomar", button(scene, "#dashboard-focus-start").getText());
            assertTrue(button(scene, "#dashboard-focus-start").isManaged());
            assertTrue(button(scene, "#dashboard-focus-open").isManaged());
            assertFalse(button(scene, "#dashboard-focus-choose").isManaged());
            assertFalse(button(scene, "#dashboard-focus-automatic").isManaged());
            assertTrue(scene.lookup("#dashboard-focus-now")
                    .getStyleClass().contains("resume-pending"));
        });
    }

    @Test
    void resumeStartsTimerOpensTaskAndClearsClueOnlyAfterAction() throws Exception {
        focusContextService.interrupt(task.id(), "Retomar pelos testes de paginação");
        AtomicReference<Task> opened = new AtomicReference<>();
        localMetricsService.setEnabled(true);
        localMetricsService.beginSession();

        FxTestSupport.run(() -> {
            DashboardController controller = buildDashboard(opened::set);

            button(primaryStage.getScene(), "#dashboard-focus-start").fire();

            assertEquals(task, opened.get());
            assertTrue(focusContextService.current().isEmpty());
            assertEquals(task.id(), TaskTimerService.get().getActiveTaskId());
            assertTrue(TaskTimerService.get().isRunning());
            assertEquals("Timer em andamento",
                    text(primaryStage.getScene(), "#dashboard-focus-mode"));
            assertEquals("Continuar foco",
                    button(primaryStage.getScene(), "#dashboard-focus-start").getText());
            assertFalse(primaryStage.getScene().lookup("#dashboard-focus-now")
                    .getStyleClass().contains("resume-pending"));
            assertEquals(1, localMetricsService.snapshot().focusStart().samples());
            assertEquals(1, localMetricsService.snapshot().interruptionResume().samples());
        });
    }

    @Test
    void longResumeClueFitsNarrowDashboardAndRemainsReadableInDarkTheme() throws Exception {
        focusContextService.interrupt(task.id(),
                "Conferir o retorno da API quando o cadastro não possui CNPJ e registrar o resultado");

        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            buildDashboard(ignored -> {});
            primaryStage.setWidth(520);
            primaryStage.setHeight(420);
            Scene scene = primaryStage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertInsideSceneHorizontally(scene, "#dashboard-focus-title",
                    "#dashboard-focus-detail", "#dashboard-focus-start",
                    "#dashboard-focus-open");
            Node focusCard = scene.lookup("#dashboard-focus-now");
            for (Node node : focusCard.lookupAll(".label")) {
                Color color = (Color) ((Label) node).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + ((Label) node).getText());
            }
        });
    }

    @Test
    void overdueReviewUsesTheThreeNeutralBoundaryBands() throws Exception {
        LocalDate today = LocalDate.now();
        List<Task> overdue = List.of(
                createTask("Pendente por um dia", today.minusDays(1)),
                createTask("Pendente por sete dias", today.minusDays(7)),
                createTask("Pendente por oito dias", today.minusDays(8)),
                createTask("Pendente por trinta dias", today.minusDays(30)),
                createTask("Pendente por trinta e um dias", today.minusDays(31)));

        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            DashboardController controller = buildDashboard(ignored -> {});
            controller.updateTaskReminderPanels(overdue, today);
            primaryStage.setWidth(520);
            primaryStage.setHeight(560);
            Scene scene = primaryStage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals(2, list(scene, "#dashboard-overdue-up-to-7").getItems().size());
            assertEquals(2, list(scene, "#dashboard-overdue-8-to-30").getItems().size());
            assertEquals(1, list(scene, "#dashboard-overdue-over-30").getItems().size());
            TabPane bands = (TabPane) scene.lookup("#dashboard-overdue-bands");
            assertEquals("Até 7 dias (2)", bands.getTabs().get(0).getText());
            assertEquals("8–30 dias (2)", bands.getTabs().get(1).getText());
            assertEquals("Mais de 30 dias (1)", bands.getTabs().get(2).getText());

            String visibleText = list(scene, "#dashboard-overdue-up-to-7").lookupAll(".label")
                    .stream().map(node -> ((Label) node).getText())
                    .filter(java.util.Objects::nonNull)
                    .reduce("", (left, right) -> left + " " + right);
            assertTrue(visibleText.contains("pendente há"));
            assertFalse(visibleText.contains("atrasada"));
            assertFalse(visibleText.contains("esquecida"));
            assertInsideSceneHorizontally(scene, "#dashboard-overdue-bands",
                    "#dashboard-overdue-up-to-7");
            for (Node node : bands.lookupAll(".label")) {
                Color color = (Color) ((Label) node).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro na revisão: " + ((Label) node).getText());
            }
        });
    }

    @Test
    void onlyCriticalOverdueTaskCanReturnToAutomaticFocus() throws Exception {
        LocalDate today = LocalDate.now();
        Task normal = createTask("Revisar quando eu escolher", today.minusDays(20));
        Task critical = createTask("Prazo crítico confirmado", today.minusDays(20));
        database.execute("UPDATE tasks SET priority='CRITICA' WHERE id=?", critical.id());
        critical = taskRepository.findById(critical.id()).orElseThrow();
        Task finalCritical = critical;
        java.util.ArrayList<Task> crowdedReview = new java.util.ArrayList<>();
        for (int index = 0; index < 51; index++) {
            crowdedReview.add(createTask("Revisão acumulada " + index, today.minusDays(2)));
        }
        Task future = createTask("Próxima tarefa programada", today.plusDays(2));
        crowdedReview.add(future);

        FxTestSupport.run(() -> {
            DashboardController controller = buildDashboard(ignored -> {});
            controller.updateTaskReminderPanels(List.of(normal), today);
            controller.refreshFocusNow();
            assertEquals("Tudo está em ordem agora",
                    text(primaryStage.getScene(), "#dashboard-focus-title"));

            controller.updateTaskReminderPanels(List.of(normal, finalCritical), today);
            controller.refreshFocusNow();
            assertEquals(finalCritical.title(),
                    text(primaryStage.getScene(), "#dashboard-focus-title"));

            controller.updateTaskReminderPanels(crowdedReview, today);
            controller.refreshFocusNow();
            assertEquals(future.title(),
                    text(primaryStage.getScene(), "#dashboard-focus-title"));
        });
    }

    private DashboardController buildDashboard(java.util.function.Consumer<Task> opener) {
        SharedContext context = new SharedContext(message -> {});
        DashboardController controller = new DashboardController(
                context, new DatabaseService(tempDir.resolve("agenda-test.db")),
                dailyPlanService, dayReviewService, localMetricsService,
                taskRepository, focusContextService, opener, preferences);
        controller.setTaskNavigator((date, taskId) -> {});
        Tab tab = controller.buildTab();
        Scene scene = new Scene(new StackPane(tab.getContent()), 900, 650);
        primaryStage.setScene(scene);
        ThemeManager.getInstance().applyTo(scene);
        controller.refreshFocusNow();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        return controller;
    }

    private Task createTask(String title, LocalDate date) {
        long id = taskRepository.saveReturningId(title, "", date, "Trabalho");
        return taskRepository.findById(id).orElseThrow();
    }

    private static ListView<?> list(Scene scene, String selector) {
        return (ListView<?>) scene.lookup(selector);
    }

    private static String text(Scene scene, String selector) {
        return ((Label) scene.lookup(selector)).getText();
    }

    private static Button button(Scene scene, String selector) {
        return (Button) scene.lookup(selector);
    }

    private static void assertInsideSceneHorizontally(Scene scene, String... selectors) {
        for (String selector : selectors) {
            Node node = scene.lookup(selector);
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0, selector + " ultrapassou a esquerda");
            assertTrue(bounds.getMaxX() <= scene.getWidth(), selector + " ultrapassou a direita");
        }
    }
}
