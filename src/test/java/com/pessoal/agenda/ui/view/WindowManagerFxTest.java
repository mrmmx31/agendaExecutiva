package com.pessoal.agenda.ui.view;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class WindowManagerFxTest {
    private Stage primaryStage;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
        primaryStage = FxTestSupport.call(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 900, 650));
            stage.show();
            WindowManager.initialize(stage);
            return stage;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        FxTestSupport.run(() -> {
            WindowManager.closeAll();
            primaryStage.close();
        });
    }

    @Test
    void createsModelessWindowWithPrimaryOwner() throws Exception {
        FxTestSupport.run(() -> {
            Stage child = WindowManager.createModelessStage();

            assertSame(primaryStage, child.getOwner());
            assertEquals(Modality.NONE, child.getModality());
        });
    }

    @Test
    void createsModalWindowWithWindowModality() throws Exception {
        FxTestSupport.run(() -> {
            Stage child = WindowManager.createModalStage();

            assertSame(primaryStage, child.getOwner());
            assertEquals(Modality.WINDOW_MODAL, child.getModality());
        });
    }

    @Test
    void preparesDialogWithPrimaryOwnerAndWindowModality() throws Exception {
        FxTestSupport.run(() -> {
            Dialog<Void> dialog = WindowManager.prepare(new Dialog<>());

            assertSame(primaryStage, dialog.getOwner());
            assertEquals(Modality.WINDOW_MODAL, dialog.getModality());
        });
    }

    @Test
    void closingDialogReassertsPrimaryMaximization() throws Exception {
        AtomicInteger maximizedTransitions = new AtomicInteger();
        FxTestSupport.run(() -> {
            primaryStage.setMaximized(true);
            primaryStage.maximizedProperty().addListener((obs, oldValue, newValue) ->
                    maximizedTransitions.incrementAndGet());
            Dialog<Void> dialog = WindowManager.prepare(new Dialog<>());
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.show();
            dialog.close();
        });

        FxTestSupport.run(() -> {});
        Thread.sleep(500);

        FxTestSupport.run(() -> {
            assertTrue(primaryStage.isMaximized());
            assertTrue(maximizedTransitions.get() >= 2,
                    "a maximized primary must be reasserted after a dialog closes");
        });
    }

    @Test
    void appliesSecondaryWindowCssContractWhenSceneIsAssigned() throws Exception {
        FxTestSupport.run(() -> {
            Stage child = WindowManager.createModelessStage();
            StackPane root = new StackPane();
            child.setScene(new Scene(root, 400, 300));

            assertTrue(root.getStyleClass().contains("secondary-window-root"));
            assertTrue(child.getScene().getStylesheets().stream()
                    .anyMatch(path -> path.endsWith("/app.css")));
        });
    }

    @Test
    void preservesRequestedPlacementWhenItIsAlreadyVisible() throws Exception {
        PositionedStage positioned = FxTestSupport.call(() -> {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            Stage child = WindowManager.createModelessStage();
            child.setScene(new Scene(new StackPane(), 320, 240));
            double expectedX = bounds.getMinX() + 100;
            double expectedY = bounds.getMinY() + 100;
            child.setX(expectedX);
            child.setY(expectedY);
            WindowManager.preservePlacement(child);
            WindowManager.show(child);
            return new PositionedStage(child, expectedX, expectedY);
        });

        FxTestSupport.run(() -> {}); // Processa o ajuste agendado por WindowManager.show().

        FxTestSupport.run(() -> {
            assertEquals(positioned.x(), positioned.stage().getX(), 1.0);
            assertEquals(positioned.y(), positioned.stage().getY(), 1.0);
        });
    }

    @Test
    void closingSecondaryWindowPreservesPrimaryGeometry() throws Exception {
        FxTestSupport.run(() -> {
            double x = primaryStage.getX();
            double y = primaryStage.getY();
            double width = primaryStage.getWidth();
            double height = primaryStage.getHeight();
            Stage child = WindowManager.createModelessStage();
            child.setScene(new Scene(new StackPane(), 420, 300));

            WindowManager.show(child);
            child.close();

            assertEquals(x, primaryStage.getX(), 1.0);
            assertEquals(y, primaryStage.getY(), 1.0);
            assertEquals(width, primaryStage.getWidth(), 1.0);
            assertEquals(height, primaryStage.getHeight(), 1.0);
        });
    }

    @Test
    void updatesSecondaryWindowThemeInRealTime() throws Exception {
        FxTestSupport.run(() -> {
            ThemeManager manager = ThemeManager.getInstance();
            ThemeManager.Theme original = manager.getTheme();
            Label label = new Label("Texto sem classe específica");
            VBox root = new VBox(label);
            root.getStyleClass().add("app-root");
            Stage child = WindowManager.createModelessStage();
            child.setScene(new Scene(root, 420, 300));

            try {
                manager.setTheme(ThemeManager.Theme.ESCURO);
                root.applyCss();
                Color darkText = (Color) label.getTextFill();
                Color darkBackground = (Color) root.getBackground().getFills().getFirst().getFill();
                assertTrue(root.getStyleClass().contains("theme-dark"));
                assertTrue(luminance(darkText) > 0.65, "dark theme text needs high contrast");
                assertTrue(luminance(darkBackground) < 0.20, "dark theme background must be dark");

                manager.setTheme(ThemeManager.Theme.CLARO);
                root.applyCss();
                Color lightText = (Color) label.getTextFill();
                Color lightBackground = (Color) root.getBackground().getFills().getFirst().getFill();
                assertTrue(!root.getStyleClass().contains("theme-dark"));
                assertTrue(luminance(lightText) < 0.30, "light theme text must return to a dark color");
                assertTrue(luminance(lightBackground) > 0.75, "light theme background must return to light");

                manager.setTheme(ThemeManager.Theme.ESCURO);
                root.applyCss();
                assertTrue(root.getStyleClass().contains("theme-dark"));
                assertTrue(luminance((Color) label.getTextFill()) > 0.65,
                        "a second toggle must not leave light-theme residue");
            } finally {
                manager.setTheme(original);
                child.close();
            }
        });
    }

    private static double luminance(Color color) {
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }

    private record PositionedStage(Stage stage, double x, double y) {}
}
