package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.QuickCaptureShortcut;
import com.pessoal.agenda.service.QuickCapturePreferences;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.Objects;

public class QuickCaptureShortcutBinding {
    private final Scene scene;
    private final QuickCapturePreferences preferences;
    private final Runnable captureAction;
    private KeyCombination activeCombination;

    public QuickCaptureShortcutBinding(
            Scene scene, QuickCapturePreferences preferences, Runnable captureAction) {
        this.scene = Objects.requireNonNull(scene);
        this.preferences = Objects.requireNonNull(preferences);
        this.captureAction = Objects.requireNonNull(captureAction);
    }

    public void refresh() {
        if (activeCombination != null) {
            scene.getAccelerators().remove(activeCombination);
        }
        activeCombination = combinationFor(preferences.getShortcut());
        if (activeCombination != null) {
            scene.getAccelerators().put(activeCombination, captureAction);
        }
    }

    public static KeyCombination combinationFor(QuickCaptureShortcut shortcut) {
        return switch (shortcut) {
            case SHORTCUT_SHIFT_SPACE -> new KeyCodeCombination(
                    KeyCode.SPACE, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
            case SHORTCUT_ALT_SPACE -> new KeyCodeCombination(
                    KeyCode.SPACE, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);
            case SHORTCUT_SHIFT_C -> new KeyCodeCombination(
                    KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
            case DISABLED -> null;
        };
    }
}
