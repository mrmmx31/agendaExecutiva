package com.pessoal.agenda.ui.view;

import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ResponsiveWindowLayoutFxTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void actionFlowWrapsWithoutClippingControls() throws Exception {
        FxTestSupport.run(() -> {
            List<Button> actions = List.of(
                    new Button("Marcar Todos"),
                    new Button("Desmarcar Todos"),
                    new Button("Limpar Concluídos"),
                    new Button("Imprimir"),
                    new Button("Novo Item"));
            FlowPane flow = ResponsiveWindowLayout.actionFlow(actions.toArray(Button[]::new));
            StackPane root = new StackPane(flow);
            Scene scene = new Scene(root, 360, 160);

            root.applyCss();
            root.layout();

            long rows = actions.stream()
                    .map(button -> Math.round(button.localToScene(button.getBoundsInLocal()).getMinY()))
                    .distinct()
                    .count();
            assertTrue(rows > 1, "actions should wrap at the minimum test width");
            for (Button action : actions) {
                Bounds bounds = action.localToScene(action.getBoundsInLocal());
                assertTrue(bounds.getMinX() >= 0);
                assertTrue(bounds.getMaxX() <= scene.getWidth());
            }
        });
    }

    @Test
    void flexibleLabelYieldsSpaceAndWrapsLongText() throws Exception {
        FxTestSupport.run(() -> {
            Label label = new Label("Título deliberadamente extenso para validar a quebra responsiva em uma janela estreita");
            ResponsiveWindowLayout.makeFlexible(label);
            Button action = new Button("Fechar");
            HBox row = new HBox(8, label, action);
            HBox.setHgrow(label, Priority.ALWAYS);
            Scene scene = new Scene(row, 280, 100);

            row.applyCss();
            row.layout();

            Bounds labelBounds = label.localToScene(label.getBoundsInLocal());
            Bounds actionBounds = action.localToScene(action.getBoundsInLocal());
            assertTrue(labelBounds.getHeight() > label.getFont().getSize());
            assertTrue(actionBounds.getMaxX() <= scene.getWidth());
        });
    }

    @Test
    void longStatusYieldsSpaceWithoutTruncatingActions() throws Exception {
        FxTestSupport.run(() -> {
            Label status = new Label(
                    "Sincronização concluída com um resumo deliberadamente longo para o rodapé");
            status.setMinWidth(0);
            status.setMaxWidth(Double.MAX_VALUE);
            status.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox.setHgrow(status, Priority.ALWAYS);
            Tooltip tooltip = new Tooltip();
            tooltip.textProperty().bind(status.textProperty());
            status.setTooltip(tooltip);

            Button refresh = new Button("Atualizar");
            Button close = new Button("Fechar");
            ResponsiveWindowLayout.preserveButtonText(refresh, close);
            HBox bar = new HBox(10, status, refresh, close);
            Scene scene = new Scene(bar, 310, 60);

            bar.applyCss();
            bar.layout();

            Bounds refreshBounds = refresh.localToScene(refresh.getBoundsInLocal());
            Bounds closeBounds = close.localToScene(close.getBoundsInLocal());
            Bounds statusBounds = status.localToScene(status.getBoundsInLocal());
            assertTrue(refreshBounds.getWidth() >= refresh.prefWidth(-1));
            assertTrue(closeBounds.getWidth() >= close.prefWidth(-1));
            assertTrue(statusBounds.getMaxX() <= refreshBounds.getMinX());
            assertTrue(closeBounds.getMaxX() <= scene.getWidth());
            assertTrue(status.getTooltip().getText().contains("deliberadamente longo"));
        });
    }
}
