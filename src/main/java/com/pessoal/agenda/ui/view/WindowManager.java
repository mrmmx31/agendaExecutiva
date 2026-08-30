package com.pessoal.agenda.ui.view;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Gerencia janelas secundárias abertas para permitir fechamento global.
 * Ao registrar uma janela, o tema atual é automaticamente aplicado à cena.
 */
public class WindowManager {
    private static final String PRESERVE_PLACEMENT_KEY = "agenda.preserveWindowPlacement";
    private static final String REQUESTED_X_KEY = "agenda.requestedWindowX";
    private static final String REQUESTED_Y_KEY = "agenda.requestedWindowY";
    private static final double SCREEN_MARGIN = 24;
    private static final Set<Stage> openStages = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Stage> configuredStages =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static Stage primaryStage;
    private static PrimaryBounds primaryRestoredBounds;
    private static boolean repairingPrimaryMaximization;

    private WindowManager() {}

    public static void initialize(Stage stage) {
        primaryStage = stage;
        primaryRestoredBounds = null;
        repairingPrimaryMaximization = false;
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> capturePrimaryRestoredBounds());
        stage.xProperty().addListener((obs, oldValue, newValue) -> capturePrimaryRestoredBounds());
        stage.yProperty().addListener((obs, oldValue, newValue) -> capturePrimaryRestoredBounds());
        stage.widthProperty().addListener((obs, oldValue, newValue) -> capturePrimaryRestoredBounds());
        stage.heightProperty().addListener((obs, oldValue, newValue) -> capturePrimaryRestoredBounds());
        stage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            if (wasMaximized && !isMaximized && !repairingPrimaryMaximization) {
                restorePrimaryBoundsAfterNativeTransition();
            }
        });
        if (stage.isShowing()) {
            capturePrimaryRestoredBounds();
        }
    }

    public static Stage createModelessStage() {
        return createStage(Modality.NONE);
    }

    public static Stage createModalStage() {
        return createStage(Modality.WINDOW_MODAL);
    }

    private static Stage createStage(Modality modality) {
        Stage stage = new Stage();
        Window owner = activeOwner();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(modality == Modality.WINDOW_MODAL && owner == null
                ? Modality.APPLICATION_MODAL
                : modality);
        register(stage);
        return stage;
    }

    public static <D extends Dialog<?>> D prepare(D dialog) {
        boolean primaryWasMaximized = isPrimaryMaximized();
        ThemeManager.getInstance().applyTo(dialog.getDialogPane());
        Window owner = activeOwner();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        if (primaryWasMaximized) {
            dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN,
                    event -> reassertPrimaryMaximization(true));
        }
        return dialog;
    }

    public static void show(Stage stage) {
        boolean restoreMaximized = primaryStage != null && primaryStage.isMaximized();
        rememberRequestedPlacement(stage);
        register(stage);
        stage.show();
        Platform.runLater(() -> {
            fitToAvailableScreen(stage);
            if (restoreMaximized && primaryStage != null
                    && primaryStage.isShowing() && !primaryStage.isMaximized()) {
                primaryStage.setMaximized(true);
            }
        });
    }

    /** Mantém a posição escolhida pelo usuário em janelas compactas arrastáveis. */
    public static void preservePlacement(Stage stage) {
        stage.getProperties().put(PRESERVE_PLACEMENT_KEY, true);
    }

    private static void rememberRequestedPlacement(Stage stage) {
        if (!Boolean.TRUE.equals(stage.getProperties().get(PRESERVE_PLACEMENT_KEY))) return;
        if (Double.isFinite(stage.getX())) stage.getProperties().put(REQUESTED_X_KEY, stage.getX());
        if (Double.isFinite(stage.getY())) stage.getProperties().put(REQUESTED_Y_KEY, stage.getY());
    }

    private static Window activeOwner() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.isFocused()) {
                return window;
            }
        }
        return primaryStage != null && primaryStage.isShowing() ? primaryStage : null;
    }

    private static boolean isPrimaryMaximized() {
        return primaryStage != null && primaryStage.isShowing() && primaryStage.isMaximized();
    }

    private static void capturePrimaryRestoredBounds() {
        if (primaryStage == null || primaryStage.isMaximized() || repairingPrimaryMaximization) return;
        if (!Double.isFinite(primaryStage.getX()) || !Double.isFinite(primaryStage.getY())
                || primaryStage.getWidth() <= 0 || primaryStage.getHeight() <= 0) return;
        primaryRestoredBounds = new PrimaryBounds(
                primaryStage.getX(), primaryStage.getY(),
                primaryStage.getWidth(), primaryStage.getHeight());
    }

    private static void restorePrimaryBoundsAfterNativeTransition() {
        PrimaryBounds bounds = primaryRestoredBounds;
        if (bounds == null) return;
        PauseTransition restoreDelay = new PauseTransition(Duration.millis(120));
        restoreDelay.setOnFinished(event -> {
            if (primaryStage == null || !primaryStage.isShowing()
                    || primaryStage.isMaximized() || repairingPrimaryMaximization) return;
            primaryStage.setX(bounds.x());
            primaryStage.setY(bounds.y());
            primaryStage.setWidth(bounds.width());
            primaryStage.setHeight(bounds.height());
        });
        restoreDelay.play();
    }

    static void reassertPrimaryMaximization(boolean expectedMaximized) {
        if (!expectedMaximized) return;
        Platform.runLater(() -> {
            if (primaryStage == null || !primaryStage.isShowing()) return;
            if (!primaryStage.isMaximized()) {
                primaryStage.setMaximized(true);
                return;
            }

            // KWin/Wayland can retain the flag while restoring the native surface size.
            repairingPrimaryMaximization = true;
            primaryStage.setMaximized(false);
            PauseTransition nativeStateDelay = new PauseTransition(Duration.millis(180));
            nativeStateDelay.setOnFinished(event -> {
                if (primaryStage == null || !primaryStage.isShowing()) {
                    repairingPrimaryMaximization = false;
                    return;
                }
                if (primaryRestoredBounds != null) {
                    primaryStage.setX(primaryRestoredBounds.x());
                    primaryStage.setY(primaryRestoredBounds.y());
                    primaryStage.setWidth(primaryRestoredBounds.width());
                    primaryStage.setHeight(primaryRestoredBounds.height());
                }
                PauseTransition maximizeDelay = new PauseTransition(Duration.millis(120));
                maximizeDelay.setOnFinished(maximizeEvent -> {
                    if (primaryStage != null && primaryStage.isShowing()) {
                        primaryStage.setMaximized(true);
                    }
                    repairingPrimaryMaximization = false;
                });
                maximizeDelay.play();
            });
            nativeStateDelay.play();
        });
    }

    public static void register(Stage stage) {
        openStages.add(stage);
        if (!configuredStages.add(stage)) {
            return;
        }
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> openStages.remove(stage));
        if (stage.getScene() != null) {
            prepareScene(stage.getScene());
        } else {
            stage.sceneProperty().addListener((obs, oldScene, newScene) -> prepareScene(newScene));
        }
    }

    private static void prepareScene(Scene scene) {
        if (scene != null) {
            if (!scene.getRoot().getStyleClass().contains("secondary-window-root")) {
                scene.getRoot().getStyleClass().add("secondary-window-root");
            }
            ThemeManager.getInstance().applyTo(scene);
        }
    }

    private static void fitToAvailableScreen(Stage stage) {
        if (!stage.isShowing() || stage.isMaximized() || stage.isFullScreen()) return;

        Window owner = stage.getOwner();
        double referenceX = owner != null ? owner.getX() : stage.getX();
        double referenceY = owner != null ? owner.getY() : stage.getY();
        double referenceWidth = owner != null ? owner.getWidth() : Math.max(stage.getWidth(), 1);
        double referenceHeight = owner != null ? owner.getHeight() : Math.max(stage.getHeight(), 1);
        Screen screen = Screen.getScreensForRectangle(
                        referenceX, referenceY, referenceWidth, referenceHeight)
                .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D bounds = screen.getVisualBounds();
        WindowPlacementCalculator.Bounds screenBounds = new WindowPlacementCalculator.Bounds(
                bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        WindowPlacementCalculator.Bounds ownerBounds = owner == null ? null
                : new WindowPlacementCalculator.Bounds(
                        owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
        double requestedX = requestedCoordinate(stage, REQUESTED_X_KEY, stage.getX());
        double requestedY = requestedCoordinate(stage, REQUESTED_Y_KEY, stage.getY());
        WindowPlacementCalculator.WindowSpec window = new WindowPlacementCalculator.WindowSpec(
                requestedX, requestedY, stage.getWidth(), stage.getHeight(),
                stage.getMinWidth(), stage.getMinHeight());
        WindowPlacementCalculator.Placement placement = WindowPlacementCalculator.fit(
                screenBounds, ownerBounds, window, SCREEN_MARGIN,
                Boolean.TRUE.equals(stage.getProperties().get(PRESERVE_PLACEMENT_KEY)));

        stage.setMinWidth(placement.minWidth());
        stage.setMinHeight(placement.minHeight());
        stage.setWidth(placement.width());
        stage.setHeight(placement.height());
        stage.setX(placement.x());
        stage.setY(placement.y());
    }

    private static double requestedCoordinate(Stage stage, String key, double fallback) {
        Object value = stage.getProperties().remove(key);
        return value instanceof Double coordinate ? coordinate : fallback;
    }

    public static void closeAll() {
        List<Stage> snapshot;
        synchronized (openStages) {
            snapshot = List.copyOf(openStages);
        }
        for (Stage stage : snapshot) {
            if (stage.isShowing()) {
                stage.close();
            }
        }
        openStages.clear();
    }

    private record PrimaryBounds(double x, double y, double width, double height) {}
}
