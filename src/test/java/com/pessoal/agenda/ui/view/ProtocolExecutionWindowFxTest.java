package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.ProtocolExecution;
import com.pessoal.agenda.model.ProtocolExecutionType;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ProtocolExecutionWindowFxTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void protocolRowsAndHistoryIconsFollowDarkThemeTokens() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager manager = ThemeManager.getInstance();
            ThemeManager.Theme original = manager.getTheme();
            HBox openRow = new HBox();
            HBox doneRow = new HBox();
            Label completed = new Label("✓");
            Label cancelled = new Label("✗");
            Label active = new Label("●");

            ProtocolExecutionWindow.applyStepRowStyle(openRow, false);
            ProtocolExecutionWindow.applyStepRowStyle(doneRow, true);
            ProtocolExecutionWindow.applyHistoryIconStyle(completed, execution("CONCLUIDA"));
            ProtocolExecutionWindow.applyHistoryIconStyle(cancelled, execution("CANCELADA"));
            ProtocolExecutionWindow.applyHistoryIconStyle(active, execution("ATIVA"));

            VBox root = new VBox(openRow, doneRow, completed, cancelled, active);
            root.getStyleClass().add("app-root");
            Scene scene = new Scene(root, 500, 300);
            manager.applyTo(scene);

            try {
                manager.setTheme(ThemeManager.Theme.ESCURO);
                root.applyCss();
                root.layout();

                Color openBackground = (Color) openRow.getBackground().getFills().getFirst().getFill();
                Color doneBackground = (Color) doneRow.getBackground().getFills().getFirst().getFill();
                assertTrue(luminance(openBackground) < 0.20,
                        "an open protocol step must not keep a white background in dark theme");
                assertTrue(luminance(doneBackground) < 0.20,
                        "a completed protocol step must remain dark in dark theme");
                assertTrue(luminance((Color) completed.getTextFill()) > 0.45);
                assertTrue(luminance((Color) cancelled.getTextFill()) > 0.35);
                assertTrue(luminance((Color) active.getTextFill()) > 0.45);
            } finally {
                manager.setTheme(original);
            }
        });
    }

    private static ProtocolExecution execution(String status) {
        return new ProtocolExecution(1, 1, "Protocolo", ProtocolExecutionType.UNICO,
                1, LocalDateTime.now(), null, status, null, 1, 0);
    }

    private static double luminance(Color color) {
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }
}
