package com.pessoal.agenda.ui.view;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public final class ReminderShortcutBinding {
    private static final KeyCombination REMIND_NOW = new KeyCodeCombination(
            KeyCode.R, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    private final Scene scene;
    private final Runnable action;

    public ReminderShortcutBinding(Scene scene, Runnable action) {
        this.scene = scene;
        this.action = action;
    }

    public void bind() {
        scene.getAccelerators().put(REMIND_NOW, action);
    }

    static KeyCombination combination() {
        return REMIND_NOW;
    }
}
