package com.pessoal.agenda.ui.view;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class QuickCaptureWindowFxTest {
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
    void enterSavesExactTextAndShowsBriefConfirmation() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        FxTestSupport.run(() -> {
            new QuickCaptureWindow(captured::set).show();
            Stage stage = captureStage();
            TextArea textArea = textArea(stage);
            String text = "  Rever hipótese\ncom os dados atuais  ";
            textArea.setText(text);

            Event.fireEvent(textArea, enter(false));

            assertEquals(text, captured.get());
            assertTrue(textArea.isDisabled());
            Label status = (Label) stage.getScene().lookup("#quick-capture-status");
            assertEquals("Salvo na caixa de entrada.", status.getText());
            assertTrue(status.isVisible());

            Event.fireEvent(textArea, escape());
            assertFalse(stage.isShowing(), "Texto já salvo não deve pedir confirmação de descarte");
        });
    }

    @Test
    void shiftEnterRemainsAvailableToTheTextArea() throws Exception {
        AtomicBoolean captured = new AtomicBoolean();
        AtomicBoolean reachedTextAreaHandler = new AtomicBoolean();
        FxTestSupport.run(() -> {
            new QuickCaptureWindow(text -> captured.set(true)).show();
            TextArea textArea = textArea(captureStage());
            textArea.setText("Primeira linha");
            textArea.addEventHandler(KeyEvent.KEY_PRESSED,
                    event -> reachedTextAreaHandler.set(true));

            Event.fireEvent(textArea, enter(true));

            assertFalse(captured.get());
            assertTrue(reachedTextAreaHandler.get(),
                    "Shift+Enter deve continuar até o comportamento nativo do TextArea");
            assertFalse(textArea.isDisabled());
        });
    }

    @Test
    void escapeWithTextRequiresInlineDiscardConfirmation() throws Exception {
        FxTestSupport.run(() -> {
            new QuickCaptureWindow(text -> {}).show();
            Stage stage = captureStage();
            TextArea textArea = textArea(stage);
            textArea.setText("Texto ainda não salvo");

            Event.fireEvent(textArea, escape());

            Node confirmation = stage.getScene().lookup("#quick-capture-discard-confirmation");
            assertTrue(stage.isShowing());
            assertTrue(confirmation.isVisible());
            ((Button) stage.getScene().lookup("#quick-capture-keep")).fire();
            assertFalse(confirmation.isVisible());
            assertTrue(stage.isShowing());

            Event.fireEvent(textArea, escape());
            ((Button) stage.getScene().lookup("#quick-capture-discard")).fire();
            assertFalse(stage.isShowing());
        });
    }

    @Test
    void escapeWithoutTextClosesImmediately() throws Exception {
        FxTestSupport.run(() -> {
            new QuickCaptureWindow(text -> {}).show();
            Stage stage = captureStage();

            Event.fireEvent(textArea(stage), escape());

            assertFalse(stage.isShowing());
        });
    }

    @Test
    void failedSaveRetainsTextAndAllowsRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> captured = new AtomicReference<>();
        FxTestSupport.run(() -> {
            new QuickCaptureWindow(text -> {
                if (attempts.incrementAndGet() == 1) throw new RuntimeException("Banco indisponível");
                captured.set(text);
            }).show();
            Stage stage = captureStage();
            TextArea textArea = textArea(stage);
            textArea.setText("Não posso perder este pensamento");

            ((Button) stage.getScene().lookup("#quick-capture-save")).fire();

            assertEquals("Não posso perder este pensamento", textArea.getText());
            assertFalse(textArea.isDisabled());
            Button retry = (Button) stage.getScene().lookup("#quick-capture-save");
            assertEquals("Tentar novamente", retry.getText());
            assertEquals("Não foi possível salvar. Seu texto continua aqui.",
                    ((Label) stage.getScene().lookup("#quick-capture-status")).getText());

            retry.fire();
            assertEquals("Não posso perder este pensamento", captured.get());
            assertEquals(2, attempts.get());
        });
    }

    @Test
    void minimumLayoutAndTextRemainReadableInDarkTheme() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            new QuickCaptureWindow(text -> {}).show();
            Stage stage = captureStage();
            stage.setWidth(stage.getMinWidth());
            stage.setHeight(stage.getMinHeight());
            Scene scene = stage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertInsideScene(scene, "#quick-capture-text", "#quick-capture-cancel",
                    "#quick-capture-save");
            for (Node node : scene.getRoot().lookupAll(".label")) {
                Color textColor = (Color) ((Label) node).getTextFill();
                assertTrue(textColor.getBrightness() >= 0.45,
                        () -> "Texto escuro no tema escuro: " + ((Label) node).getText());
            }
        });
    }

    @Test
    void openingAndClosingPreservesPrimaryTabAndGeometry() throws Exception {
        FxTestSupport.run(() -> {
            TabPane tabs = new TabPane(new Tab("Hoje"), new Tab("Estudos"), new Tab("Ideias"));
            tabs.getSelectionModel().select(2);
            primaryStage.getScene().setRoot(tabs);
            double x = primaryStage.getX();
            double y = primaryStage.getY();
            double width = primaryStage.getWidth();
            double height = primaryStage.getHeight();

            new QuickCaptureWindow(text -> {}).show();
            Stage capture = captureStage();
            Event.fireEvent(textArea(capture), escape());

            assertEquals(2, tabs.getSelectionModel().getSelectedIndex());
            assertEquals(x, primaryStage.getX(), 1.0);
            assertEquals(y, primaryStage.getY(), 1.0);
            assertEquals(width, primaryStage.getWidth(), 1.0);
            assertEquals(height, primaryStage.getHeight(), 1.0);
        });
    }

    @Test
    void savedCallbackRunsOnlyAfterCaptureSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicInteger measuredActions = new AtomicInteger();
        FxTestSupport.run(() -> {
            new QuickCaptureWindow(text -> {
                if (attempts.incrementAndGet() == 1) throw new RuntimeException("Falha temporária");
            }, actions -> {
                callbacks.incrementAndGet();
                measuredActions.set(actions);
            }).show();
            Stage stage = captureStage();
            TextArea textArea = textArea(stage);
            textArea.setText("Atualizar a contagem somente depois de persistir");
            Button save = (Button) stage.getScene().lookup("#quick-capture-save");

            save.fire();
            assertEquals(0, callbacks.get());
            save.fire();

            assertEquals(2, attempts.get());
            assertEquals(1, callbacks.get());
            assertEquals(2, measuredActions.get());
        });
    }

    private static Stage captureStage() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage)
                .map(window -> (Stage) window)
                .filter(stage -> "Captura rápida".equals(stage.getTitle()))
                .findFirst().orElseThrow();
    }

    private static TextArea textArea(Stage stage) {
        return (TextArea) stage.getScene().lookup("#quick-capture-text");
    }

    private static KeyEvent enter(boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                shift, false, false, false);
    }

    private static KeyEvent escape() {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                false, false, false, false);
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
