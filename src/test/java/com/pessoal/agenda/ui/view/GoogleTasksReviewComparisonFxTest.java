package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewVersion;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class GoogleTasksReviewComparisonFxTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void versionsRemainReadableSideBySideInDarkTheme() throws Exception {
        FxTestSupport.run(() -> {
            ReviewVersion local = new ReviewVersion(true, "Versão local extensa",
                    "Notas locais extensas para validar quebra de linha", LocalDate.now(), true);
            ReviewVersion google = new ReviewVersion(true, "Versão Google extensa",
                    "Notas Google extensas para validar quebra de linha", LocalDate.now(), false);
            TextArea localArea = GoogleTasksSyncWindow.reviewVersionArea(
                    "google-review-local-version");
            TextArea googleArea = GoogleTasksSyncWindow.reviewVersionArea(
                    "google-review-google-version");
            localArea.setText(GoogleTasksSyncWindow.formatReviewVersion(local, "Indisponível"));
            googleArea.setText(GoogleTasksSyncWindow.formatReviewVersion(google, "Indisponível"));
            GridPane comparison = GoogleTasksSyncWindow.reviewComparison(localArea, googleArea);
            StackPane root = new StackPane(comparison);
            Scene scene = new Scene(root, 620, 260);
            scene.getStylesheets().add(resource("app.css"));
            scene.getStylesheets().add(resource("theme-dark.css"));

            root.applyCss();
            root.layout();

            Bounds localBounds = localArea.localToScene(localArea.getBoundsInLocal());
            Bounds googleBounds = googleArea.localToScene(googleArea.getBoundsInLocal());
            assertTrue(localBounds.getMaxX() <= googleBounds.getMinX());
            assertTrue(googleBounds.getMaxX() <= scene.getWidth());
            assertEquals(localArea.getWidth(), googleArea.getWidth(), 1.0);
            assertTrue(localArea.getText().contains("Notas locais extensas"));
            assertTrue(googleArea.getText().contains("Status: Pendente"));
            assertBrightText(localArea);
            assertBrightText(googleArea);
        });
    }

    private static void assertBrightText(TextArea area) {
        Text text = (Text) area.lookup(".text");
        assertTrue(text != null, "the text area must render its text node");
        Color color = (Color) text.getFill();
        assertTrue(luminance(color) > 0.65, "dark theme text needs high contrast");
    }

    private static double luminance(Color color) {
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen()
                + 0.0722 * color.getBlue();
    }

    private static String resource(String name) {
        return GoogleTasksReviewComparisonFxTest.class
                .getResource("/com/pessoal/agenda/" + name).toExternalForm();
    }
}
