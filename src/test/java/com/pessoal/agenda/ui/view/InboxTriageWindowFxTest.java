package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.InboxCaptureRepository;
import com.pessoal.agenda.service.InboxCaptureService;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class InboxTriageWindowFxTest {
    @TempDir
    Path tempDir;

    private Stage primaryStage;
    private ThemeManager.Theme originalTheme;
    private Database database;
    private InboxCaptureService service;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        service = new InboxCaptureService(new InboxCaptureRepository(database));
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
    void listsPendingCapturesAndCreatesTaskFromSelection() throws Exception {
        service.capture("Primeira captura");
        service.capture("Preparar documento\nConferir os anexos");
        AtomicInteger changes = new AtomicInteger();

        FxTestSupport.run(() -> {
            new InboxTriageWindow(service, changes::incrementAndGet).show();
            Stage stage = triageStage();
            ListView<?> list = (ListView<?>) stage.getScene().lookup("#inbox-triage-list");
            TextArea detail = (TextArea) stage.getScene().lookup("#inbox-triage-detail");

            assertEquals(2, list.getItems().size());
            assertEquals("Preparar documento\nConferir os anexos", detail.getText());
            ((DatePicker) stage.getScene().lookup("#inbox-triage-task-date"))
                    .setValue(LocalDate.of(2026, 8, 30));
            ((Button) stage.getScene().lookup("#inbox-triage-task")).fire();

            assertEquals(1, changes.get());
            assertEquals(1, list.getItems().size());
            assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
            assertEquals("Tarefa criada.",
                    ((Label) stage.getScene().lookup("#inbox-triage-status")).getText());
        });
    }

    @Test
    void destinationFailureKeepsCaptureVisibleAndRetryable() throws Exception {
        service.capture("Captura preservada após erro");
        database.execute("DROP TABLE tasks");

        FxTestSupport.run(() -> {
            new InboxTriageWindow(service, () -> {}).show();
            Stage stage = triageStage();
            ListView<?> list = (ListView<?>) stage.getScene().lookup("#inbox-triage-list");

            ((Button) stage.getScene().lookup("#inbox-triage-task")).fire();

            assertEquals(1, list.getItems().size());
            assertFalse(((Button) stage.getScene().lookup("#inbox-triage-task")).isDisabled());
            assertEquals("Não foi possível concluir a triagem. A captura continua pendente.",
                    ((Label) stage.getScene().lookup("#inbox-triage-status")).getText());
            assertEquals(1, service.countUnclassified());
        });
    }

    @Test
    void emptyInboxDisablesAllTriageActions() throws Exception {
        FxTestSupport.run(() -> {
            new InboxTriageWindow(service, () -> {}).show();
            Scene scene = triageStage().getScene();

            assertTrue(((Button) scene.lookup("#inbox-triage-task")).isDisabled());
            assertTrue(((Button) scene.lookup("#inbox-triage-idea")).isDisabled());
            assertTrue(((Button) scene.lookup("#inbox-triage-interruption")).isDisabled());
            assertTrue(((Button) scene.lookup("#inbox-triage-archive")).isDisabled());
            assertEquals("Nada aguardando organização.",
                    ((Label) scene.getRoot().lookupAll(".t-muted-md").stream()
                            .filter(node -> node instanceof Label label
                                    && label.getText().contains("aguardando"))
                            .findFirst().orElseThrow()).getText());
        });
    }

    @Test
    void minimumLayoutAndTextRemainReadableInDarkTheme() throws Exception {
        service.capture("Uma captura longa para validar a leitura no tema escuro");
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            new InboxTriageWindow(service, () -> {}).show();
            Stage stage = triageStage();
            stage.setWidth(stage.getMinWidth());
            stage.setHeight(stage.getMinHeight());
            Scene scene = stage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertInsideScene(scene, "#inbox-triage-list", "#inbox-triage-detail",
                    "#inbox-triage-task", "#inbox-triage-idea",
                    "#inbox-triage-interruption", "#inbox-triage-archive");
            for (Node node : scene.getRoot().lookupAll(".label")) {
                Color color = (Color) ((Label) node).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + ((Label) node).getText());
            }
        });
    }

    private static Stage triageStage() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage)
                .map(window -> (Stage) window)
                .filter(stage -> "Caixa de entrada".equals(stage.getTitle()))
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
