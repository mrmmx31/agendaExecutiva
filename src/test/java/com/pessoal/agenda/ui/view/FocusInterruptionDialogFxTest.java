package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.FocusContextRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.TaskSessionRepository;
import com.pessoal.agenda.service.FocusContextService;
import com.pessoal.agenda.service.TaskTimerService;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class FocusInterruptionDialogFxTest {
    @TempDir
    Path tempDir;

    private Stage primaryStage;
    private ThemeManager.Theme originalTheme;
    private Task task;
    private TaskSessionRepository sessionRepository;
    private FocusContextService focusContextService;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        TaskRepository taskRepository = new TaskRepository(database);
        long taskId = taskRepository.saveReturningId(
                "Validar integração", "", LocalDate.of(2026, 8, 28), "Trabalho");
        task = taskRepository.findById(taskId).orElseThrow();
        sessionRepository = new TaskSessionRepository(database);
        focusContextService = new FocusContextService(
                new FocusContextRepository(database), taskRepository);

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
        TaskTimerService.get().stop();
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(originalTheme);
            WindowManager.closeAll();
            primaryStage.close();
        });
    }

    @Test
    void timerActionPersistsNoteAndLeavesActiveTimerPaused() throws Exception {
        AtomicReference<String> dialogTitle = new AtomicReference<>();
        FxTestSupport.run(() -> {
            TaskTimerService.get().start(task.id());
            new TaskTimerWindow(task, sessionRepository, null, focusContextService).show();
            Stage timerStage = timerStage();
            Button interrupt = (Button) timerStage.getScene().lookup("#task-timer-interrupt");
            assertFalse(interrupt.isDisabled());

            Platform.runLater(() -> {
                DialogPane pane = interruptionPane();
                dialogTitle.set(((Stage) pane.getScene().getWindow()).getTitle());
                ((TextArea) pane.lookup("#focus-interruption-note"))
                        .setText("Validar o retorno sem CNPJ");
                ((Button) pane.lookup("#focus-interruption-save")).fire();
            });
            interrupt.fire();

            assertEquals("Registrar interrupção", dialogTitle.get());
            assertEquals("Validar o retorno sem CNPJ",
                    focusContextService.current().orElseThrow().resumeNote());
            assertEquals(task.id(), TaskTimerService.get().getActiveTaskId());
            assertFalse(TaskTimerService.get().isRunning());
        });
    }

    @Test
    void cancellingInterruptionResumesTimerThatWasRunning() throws Exception {
        FxTestSupport.run(() -> {
            TaskTimerService.get().start(task.id());
            new TaskTimerWindow(task, sessionRepository, null, focusContextService).show();
            Button interrupt = (Button) timerStage().getScene()
                    .lookup("#task-timer-interrupt");

            Platform.runLater(() -> {
                DialogPane pane = interruptionPane();
                ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
            });
            interrupt.fire();

            assertTrue(focusContextService.current().isEmpty());
            assertTrue(TaskTimerService.get().isRunning());
        });
    }

    @Test
    void failedSaveKeepsTextAndAllowsRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> saved = new AtomicReference<>();
        FxTestSupport.run(() -> {
            Dialog<ButtonType> dialog = FocusInterruptionDialog.create(task, "", note -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("Banco indisponível");
                }
                saved.set(note);
            });
            dialog.show();
            DialogPane pane = dialog.getDialogPane();
            TextArea note = (TextArea) pane.lookup("#focus-interruption-note");
            note.setText("Não perder este ponto");
            Button save = (Button) pane.lookup("#focus-interruption-save");

            save.fire();

            assertTrue(dialog.isShowing());
            assertEquals("Não perder este ponto", note.getText());
            assertEquals("Tentar novamente", save.getText());
            assertEquals("Não foi possível guardar a pista. Seu texto continua aqui.",
                    ((Label) pane.lookup("#focus-interruption-status")).getText());

            save.fire();
            assertFalse(dialog.isShowing());
            assertEquals("Não perder este ponto", saved.get());
            assertEquals(2, attempts.get());
        });
    }

    @Test
    void blankNoteDoesNotCloseDialogOrInvokePersistence() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FxTestSupport.run(() -> {
            Dialog<ButtonType> dialog = FocusInterruptionDialog.create(
                    task, "", note -> calls.incrementAndGet());
            dialog.show();
            DialogPane pane = dialog.getDialogPane();

            ((Button) pane.lookup("#focus-interruption-save")).fire();

            assertTrue(dialog.isShowing());
            assertEquals(0, calls.get());
            assertEquals("Escreva onde parou ou qual é o próximo passo.",
                    ((Label) pane.lookup("#focus-interruption-status")).getText());
            dialog.close();
        });
    }

    @Test
    void compactTimerExposesInterruptionActionAndDarkDialogRemainsReadable() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            TaskTimerService.get().start(task.id());
            new TaskTimerWindow(task, sessionRepository, null, focusContextService).show();
            ((Button) timerStage().getScene().lookup("#task-timer-compact")).fire();
            Stage compact = compactTimerStage();
            Button compactInterrupt = (Button) compact.getScene()
                    .lookup("#compact-task-timer-interrupt");

            assertNotNull(compactInterrupt);
            assertFalse(compactInterrupt.isDisabled());
            assertEquals("Fui interrompido", compactInterrupt.getTooltip().getText());

            Dialog<ButtonType> dialog = FocusInterruptionDialog.create(
                    task, "Próximo passo", note -> {});
            dialog.show();
            Scene scene = dialog.getDialogPane().getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            assertInsideScene(scene, "#focus-interruption-note", "#focus-interruption-save");
            for (Node node : scene.getRoot().lookupAll(".label")) {
                Color textColor = (Color) ((Label) node).getTextFill();
                assertTrue(textColor.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + ((Label) node).getText());
            }
            dialog.close();
        });
    }

    private static Stage timerStage() {
        return stageWithTitle("Timer — ");
    }

    private static Stage compactTimerStage() {
        return stageWithTitle("Timer mini — ");
    }

    private static Stage stageWithTitle(String prefix) {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage)
                .map(window -> (Stage) window)
                .filter(stage -> stage.getTitle() != null && stage.getTitle().startsWith(prefix))
                .findFirst().orElseThrow();
    }

    private static DialogPane interruptionPane() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage)
                .map(window -> (Stage) window)
                .filter(stage -> "Registrar interrupção".equals(stage.getTitle()))
                .map(stage -> (DialogPane) stage.getScene().getRoot())
                .findFirst().orElseThrow();
    }

    private static void assertInsideScene(Scene scene, String... selectors) {
        for (String selector : selectors) {
            Node node = scene.lookup(selector);
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0, selector + " ultrapassou a esquerda");
            assertTrue(bounds.getMaxX() <= scene.getWidth(), selector + " ultrapassou a direita");
            assertTrue(bounds.getMinY() >= 0, selector + " ultrapassou o topo");
            assertTrue(bounds.getMaxY() <= scene.getHeight(), selector + " ultrapassou a base");
        }
    }
}
