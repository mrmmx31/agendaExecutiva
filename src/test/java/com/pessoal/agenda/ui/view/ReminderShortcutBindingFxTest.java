package com.pessoal.agenda.ui.view;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ReminderShortcutBindingFxTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void bindsOnlySafeReminderShortcut() throws Exception {
        FxTestSupport.run(() -> {
            Scene scene = new Scene(new StackPane());
            AtomicInteger reminders = new AtomicInteger();
            new ReminderShortcutBinding(scene, reminders::incrementAndGet).bind();

            scene.getAccelerators().get(ReminderShortcutBinding.combination()).run();

            assertEquals(1, reminders.get());
            assertEquals(1, scene.getAccelerators().size());
            assertTrue(ReminderShortcutBinding.combination().getName().contains("Shift"));
            assertTrue(ReminderShortcutBinding.combination().getName().contains("R"));
            KeyCombination unsafeSave = new KeyCodeCombination(
                    KeyCode.S, KeyCombination.SHORTCUT_DOWN);
            assertFalse(scene.getAccelerators().containsKey(unsafeSave));
        });
    }
}
