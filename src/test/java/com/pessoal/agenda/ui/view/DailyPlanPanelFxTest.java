package com.pessoal.agenda.ui.view;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class DailyPlanPanelFxTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void exposesOneExplicitStateAtATime() throws Exception {
        FxTestSupport.run(() -> {
            DailyPlanPanel panel = new DailyPlanPanel();

            assertOnlyStateVisible(panel, "#daily-plan-loading");
            panel.showEmpty();
            assertOnlyStateVisible(panel, "#daily-plan-empty");
            panel.showPlan("Ritmo normal", "Preparar proposta",
                    List.of("Responder mensagens", "Separar documentos"));
            assertOnlyStateVisible(panel, "#daily-plan-ready");
            panel.showError();
            assertOnlyStateVisible(panel, "#daily-plan-error");
        });
    }

    @Test
    void startActionIsKeyboardCompatibleButtonCommand() throws Exception {
        FxTestSupport.run(() -> {
            AtomicBoolean invoked = new AtomicBoolean();
            DailyPlanPanel panel = new DailyPlanPanel();
            panel.setStartAction(() -> invoked.set(true));
            panel.showEmpty();

            Button button = (Button) panel.lookup("#daily-plan-start");
            button.fire();

            assertTrue(invoked.get());
            assertTrue(button.isFocusTraversable());
        });
    }

    @Test
    void readyAndClosedPlansExposeTheCorrectDayReviewAction() throws Exception {
        FxTestSupport.run(() -> {
            AtomicBoolean reviewOpened = new AtomicBoolean();
            DailyPlanPanel panel = new DailyPlanPanel();
            panel.setCloseDayAction(() -> reviewOpened.set(true));

            panel.showPlan("Ritmo normal", "Preparar proposta", List.of());
            assertTrue(panel.lookup("#daily-plan-close-day").isManaged());
            assertTrue(!panel.lookup("#daily-plan-review-closed").isManaged());
            ((Button) panel.lookup("#daily-plan-close-day")).fire();
            assertTrue(reviewOpened.get());
            panel.setEssentialAvailable(false);
            assertTrue(!panel.lookup("#daily-plan-open-essential").isManaged());
            panel.setEssentialAvailable(true);

            reviewOpened.set(false);
            panel.showClosedPlan("Ritmo normal", "Preparar proposta", List.of());
            assertTrue(!panel.lookup("#daily-plan-close-day").isManaged());
            assertTrue(panel.lookup("#daily-plan-review-closed").isManaged());
            assertTrue(!panel.lookup("#daily-plan-edit").isManaged());
            ((Button) panel.lookup("#daily-plan-review-closed")).fire();
            assertTrue(reviewOpened.get());
        });
    }

    @Test
    void completesThreeStepsAndKeepsReorderedSupportIds() throws Exception {
        FxTestSupport.run(() -> {
            DailyPlanPanel.TaskOption essential = option(1, "Preparar proposta");
            DailyPlanPanel.TaskOption firstSupport = option(2, "Responder mensagens");
            DailyPlanPanel.TaskOption secondSupport = option(3, "Separar documentos");
            DailyPlanPanel panel = new DailyPlanPanel();
            AtomicReference<DailyPlanPanel.PlanSelection> saved = new AtomicReference<>();
            panel.setSaveAction(saved::set);
            panel.beginPlanning(new DailyPlanPanel.PlanningRequest(
                    List.of(essential, firstSupport, secondSupport),
                    List.of("09:00 · Preparar proposta"),
                    com.pessoal.agenda.model.DailyPlanCapacity.NORMAL, 1L, List.of(2L, 3L)));

            assertOnlyStateVisible(panel, "#daily-plan-review-step");
            ((Button) panel.lookup("#daily-plan-review-next")).fire();
            assertOnlyStateVisible(panel, "#daily-plan-selection-step");

            assertEquals(essential, taskCombo(panel, "#daily-plan-essential-choice").getValue());
            assertEquals(firstSupport, taskCombo(panel, "#daily-plan-support-one").getValue());
            assertEquals(secondSupport, taskCombo(panel, "#daily-plan-support-two").getValue());
            ((Button) panel.lookup("#daily-plan-support-down")).fire();
            assertEquals(secondSupport, taskCombo(panel, "#daily-plan-support-one").getValue());
            assertEquals(firstSupport, taskCombo(panel, "#daily-plan-support-two").getValue());

            ((Button) panel.lookup("#daily-plan-selection-next")).fire();
            assertOnlyStateVisible(panel, "#daily-plan-confirmation-step");
            ((RadioButton) panel.lookup("#daily-plan-stay-after-save")).setSelected(true);
            ((Button) panel.lookup("#daily-plan-save")).fire();

            assertEquals(1L, saved.get().essentialTaskId());
            assertEquals(List.of(3L, 2L), saved.get().supportTaskIds());
            assertTrue(!saved.get().openAfterSave());
        });
    }

    @Test
    void escapeCancelsEditingWithoutSaving() throws Exception {
        FxTestSupport.run(() -> {
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicBoolean saved = new AtomicBoolean();
            DailyPlanPanel panel = new DailyPlanPanel();
            panel.setCancelPlanningAction(() -> cancelled.set(true));
            panel.setSaveAction(selection -> saved.set(true));
            panel.beginPlanning(new DailyPlanPanel.PlanningRequest(
                    List.of(option(1, "Tarefa aberta")), List.of(),
                    com.pessoal.agenda.model.DailyPlanCapacity.NORMAL, null, List.of()));

            panel.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                    KeyCode.ESCAPE, false, false, false, false));

            assertTrue(cancelled.get());
            assertTrue(!saved.get());
        });
    }

    @Test
    void reducedCapacityRequestsOnlyEssentialTask() throws Exception {
        FxTestSupport.run(() -> {
            DailyPlanPanel.TaskOption essential = option(1, "Essencial possível");
            DailyPlanPanel.TaskOption support = option(2, "Apoio que será removido");
            DailyPlanPanel panel = new DailyPlanPanel();
            AtomicReference<com.pessoal.agenda.model.DailyPlanCapacity> capacity = new AtomicReference<>();
            AtomicReference<DailyPlanPanel.PlanSelection> saved = new AtomicReference<>();
            panel.setCapacityChangeAction(capacity::set);
            panel.setSaveAction(saved::set);
            panel.beginPlanning(new DailyPlanPanel.PlanningRequest(
                    List.of(essential, support), List.of(),
                    com.pessoal.agenda.model.DailyPlanCapacity.NORMAL, 1L, List.of(2L)));

            ((javafx.scene.control.ToggleButton) panel.lookup("#daily-plan-capacity-reduced")).fire();
            assertEquals(com.pessoal.agenda.model.DailyPlanCapacity.REDUCED, capacity.get());
            ((Button) panel.lookup("#daily-plan-review-next")).fire();

            assertTrue(!panel.lookup("#daily-plan-support-caption").isManaged());
            assertTrue(!panel.lookup("#daily-plan-support-one-row").isManaged());
            assertTrue(!panel.lookup("#daily-plan-support-two-row").isManaged());
            assertEquals(null, taskCombo(panel, "#daily-plan-support-one").getValue());
            assertEquals(null, taskCombo(panel, "#daily-plan-support-two").getValue());

            ((javafx.scene.control.ToggleButton) panel.lookup("#daily-plan-capacity-normal")).fire();
            assertTrue(panel.lookup("#daily-plan-support-one-row").isManaged());
            assertTrue(panel.lookup("#daily-plan-support-two-row").isManaged());
            ((javafx.scene.control.ToggleButton) panel.lookup("#daily-plan-capacity-reduced")).fire();

            ((Button) panel.lookup("#daily-plan-selection-next")).fire();
            ((RadioButton) panel.lookup("#daily-plan-stay-after-save")).setSelected(true);
            ((Button) panel.lookup("#daily-plan-save")).fire();

            assertEquals(com.pessoal.agenda.model.DailyPlanCapacity.REDUCED, saved.get().capacity());
            assertEquals(1L, saved.get().essentialTaskId());
            assertTrue(saved.get().supportTaskIds().isEmpty());
        });
    }

    @Test
    void readyStateFitsNarrowWidthAndKeepsReadableDarkThemeText() throws Exception {
        FxTestSupport.run(() -> {
            DailyPlanPanel panel = new DailyPlanPanel();
            panel.showPlan("Capacidade reduzida",
                    "Uma tarefa essencial deliberadamente longa para validar quebra de linha",
                    List.of());
            StackPane root = new StackPane(panel);
            Scene scene = new Scene(root, 340, 300);
            scene.getStylesheets().add(resource("app.css"));
            scene.getStylesheets().add(resource("theme-dark.css"));

            root.applyCss();
            root.layout();

            Bounds buttonBounds = panel.lookup("#daily-plan-open-essential")
                    .localToScene(panel.lookup("#daily-plan-open-essential").getBoundsInLocal());
            assertTrue(buttonBounds.getMaxX() <= scene.getWidth());
            for (Node node : panel.lookupAll(".label")) {
                Label label = (Label) node;
                Color color = (Color) label.getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + label.getText());
            }
        });
    }

    @Test
    void selectionAndConfirmationFitNarrowDarkThemeLayout() throws Exception {
        FxTestSupport.run(() -> {
            DailyPlanPanel.TaskOption essential = option(1, "Uma tarefa essencial com título extenso");
            DailyPlanPanel.TaskOption support = option(2, "Uma tarefa de apoio também extensa");
            DailyPlanPanel panel = new DailyPlanPanel();
            panel.beginPlanning(new DailyPlanPanel.PlanningRequest(
                    List.of(essential, support), List.of("10:30 · Compromisso de hoje"),
                    com.pessoal.agenda.model.DailyPlanCapacity.NORMAL, 1L, List.of(2L)));
            ((Button) panel.lookup("#daily-plan-review-next")).fire();
            StackPane root = new StackPane(panel);
            Scene scene = new Scene(root, 340, 520);
            scene.getStylesheets().add(resource("app.css"));
            scene.getStylesheets().add(resource("theme-dark.css"));

            root.applyCss();
            root.layout();
            assertInsideScene(scene, panel,
                    "#daily-plan-essential-choice", "#daily-plan-support-one", "#daily-plan-support-two",
                    "#daily-plan-support-down", "#daily-plan-support-up",
                    "#daily-plan-support-one-clear", "#daily-plan-support-two-clear",
                    "#daily-plan-selection-back", "#daily-plan-selection-next",
                    "#daily-plan-selection-cancel");

            ((Button) panel.lookup("#daily-plan-selection-next")).fire();
            root.applyCss();
            root.layout();
            assertInsideScene(scene, panel,
                    "#daily-plan-open-after-save", "#daily-plan-stay-after-save",
                    "#daily-plan-confirmation-back", "#daily-plan-save",
                    "#daily-plan-confirmation-cancel");
        });
    }

    private static void assertOnlyStateVisible(DailyPlanPanel panel, String visibleSelector) {
        List<String> selectors = List.of(
                "#daily-plan-loading", "#daily-plan-empty", "#daily-plan-ready", "#daily-plan-error",
                "#daily-plan-review-step", "#daily-plan-selection-step", "#daily-plan-confirmation-step");
        for (String selector : selectors) {
            Node state = panel.lookup(selector);
            assertEquals(selector.equals(visibleSelector), state.isVisible(), selector);
            assertEquals(selector.equals(visibleSelector), state.isManaged(), selector);
        }
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<DailyPlanPanel.TaskOption> taskCombo(DailyPlanPanel panel, String selector) {
        return (ComboBox<DailyPlanPanel.TaskOption>) panel.lookup(selector);
    }

    private static DailyPlanPanel.TaskOption option(long id, String title) {
        return new DailyPlanPanel.TaskOption(id, title, "Hoje · Normal · Trabalho");
    }

    private static void assertInsideScene(Scene scene, DailyPlanPanel panel, String... selectors) {
        for (String selector : selectors) {
            Node node = panel.lookup(selector);
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0, selector + " ultrapassou a esquerda");
            assertTrue(bounds.getMaxX() <= scene.getWidth(), selector + " ultrapassou a direita");
        }
    }

    private static String resource(String name) {
        return DailyPlanPanelFxTest.class.getResource("/com/pessoal/agenda/" + name).toExternalForm();
    }
}
