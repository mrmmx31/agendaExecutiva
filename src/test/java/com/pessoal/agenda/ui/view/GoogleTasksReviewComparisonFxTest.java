package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewDetails;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewItem;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewVersion;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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

    @Test
    void everyPendingItemIsVisibleWithoutOpeningAComboBox() throws Exception {
        FxTestSupport.run(() -> {
            ReviewVersion version = new ReviewVersion(true, "Título", null,
                    LocalDate.now(), true);
            List<ReviewDetails> details = List.of(
                    details(1, "Pegar a Royal no Motorock", version),
                    details(2, "Ler Capítulo 7 do Web Application Hacker's Handbook", version),
                    details(3, "Consulta Rosy", version),
                    details(4, "Verificar relatório e filtros", version),
                    details(5, "Consulta médica", version));
            ListView<ReviewDetails> list = GoogleTasksSyncWindow.reviewItemsList(details);
            StackPane root = new StackPane(list);
            new Scene(root, 700, 190);

            root.applyCss();
            root.layout();

            assertEquals(5, list.getItems().size());
            assertTrue(list.getPrefHeight() >= 172);
            assertTrue(list.lookupAll(".list-cell").stream()
                    .filter(ListCell.class::isInstance)
                    .map(ListCell.class::cast)
                    .anyMatch(cell -> cell.getText() != null
                            && cell.getText().contains("Web Application Hacker")));
        });
    }

    @Test
    void cancellationIsTheDefaultOnFinalConfirmation() throws Exception {
        FxTestSupport.run(() -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            ButtonType apply = new ButtonType(
                    "Aplicar versão local", ButtonBar.ButtonData.OK_DONE);
            confirmation.getButtonTypes().setAll(apply, ButtonType.CANCEL);

            GoogleTasksSyncWindow.preferReviewCancellation(
                    confirmation.getDialogPane(), apply);

            Button applyButton = (Button) confirmation.getDialogPane().lookupButton(apply);
            Button cancelButton = (Button) confirmation.getDialogPane()
                    .lookupButton(ButtonType.CANCEL);
            assertTrue(!applyButton.isDefaultButton());
            assertTrue(cancelButton.isDefaultButton());
        });
    }

    private static ReviewDetails details(long id, String title, ReviewVersion version) {
        return new ReviewDetails(new ReviewItem(id, SyncState.CONFLICT, title, "google-" + id),
                version, version);
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
