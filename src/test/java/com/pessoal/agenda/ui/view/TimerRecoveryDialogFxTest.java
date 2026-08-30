package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TimerRecovery;
import com.pessoal.agenda.service.TaskTimerRecoveryService;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class TimerRecoveryDialogFxTest {
    private Stage primaryStage;
    private ThemeManager.Theme originalTheme;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
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
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(originalTheme);
            WindowManager.closeAll();
            primaryStage.close();
        });
    }

    @Test
    void showsExactCheckpointAndReturnsRecoverDecision() throws Exception {
        FxTestSupport.run(() -> {
            Dialog<TimerRecoveryDialog.Decision> dialog =
                    TimerRecoveryDialog.create(candidate(true));
            dialog.show();
            Scene scene = dialog.getDialogPane().getScene();

            assertEquals("Preparar relatório", text(scene, "#timer-recovery-task"));
            assertEquals("Tempo registrado: 01:01:07",
                    text(scene, "#timer-recovery-elapsed"));
            assertTrue(text(scene, "#timer-recovery-detail")
                    .startsWith("O contador estava rodando."));
            assertTrue(text(scene, "#timer-recovery-safety")
                    .contains("não será somado"));

            button(scene, "#timer-recovery-recover").fire();

            assertFalse(dialog.isShowing());
            assertEquals(TimerRecoveryDialog.Decision.RECOVER, dialog.getResult());
        });
    }

    @Test
    void requiresExplicitChoiceAndReturnsDiscardDecision() throws Exception {
        FxTestSupport.run(() -> {
            Dialog<TimerRecoveryDialog.Decision> dialog =
                    TimerRecoveryDialog.create(candidate(false));
            dialog.show();

            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            assertTrue(dialog.isShowing(),
                    "Fechar sem decisão não pode descartar nem recuperar silenciosamente");

            button(dialog.getDialogPane().getScene(), "#timer-recovery-discard").fire();
            assertFalse(dialog.isShowing());
            assertEquals(TimerRecoveryDialog.Decision.DISCARD, dialog.getResult());
        });
    }

    @Test
    void minimumDialogRemainsReadableInDarkTheme() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            Dialog<TimerRecoveryDialog.Decision> dialog =
                    TimerRecoveryDialog.create(candidate(true));
            dialog.show();
            Scene scene = dialog.getDialogPane().getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertInsideScene(scene, "#timer-recovery-task", "#timer-recovery-elapsed",
                    "#timer-recovery-detail", "#timer-recovery-safety",
                    "#timer-recovery-recover", "#timer-recovery-discard");
            for (Node node : scene.getRoot().lookupAll(".label")) {
                Color color = (Color) ((Label) node).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + ((Label) node).getText());
            }
            button(scene, "#timer-recovery-discard").fire();
        });
    }

    private static TaskTimerRecoveryService.Candidate candidate(boolean running) {
        Task task = new Task(41, "Preparar relatório", "", LocalDate.of(2026, 8, 28),
                false, "Trabalho");
        TimerRecovery recovery = new TimerRecovery(
                task.id(), 3_667, running, Instant.parse("2026-08-28T15:45:00Z"));
        return new TaskTimerRecoveryService.Candidate(task, recovery);
    }

    private static String text(Scene scene, String selector) {
        return ((Label) scene.lookup(selector)).getText();
    }

    private static Button button(Scene scene, String selector) {
        return (Button) scene.lookup(selector);
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
