package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.QuickCaptureShortcut;
import com.pessoal.agenda.service.QuickCapturePreferences;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class QuickCaptureShortcutBindingFxTest {
    private Preferences testNode;
    private QuickCapturePreferences preferences;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() {
        testNode = Preferences.userRoot().node(
                "/com/pessoal/agenda/tests/quick-capture-binding/" + UUID.randomUUID());
        preferences = new QuickCapturePreferences(testNode);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testNode.removeNode();
    }

    @Test
    void bindsDefaultShortcutAndRunsCaptureAction() throws Exception {
        FxTestSupport.run(() -> {
            Scene scene = new Scene(new StackPane());
            AtomicInteger openings = new AtomicInteger();
            QuickCaptureShortcutBinding binding = new QuickCaptureShortcutBinding(
                    scene, preferences, openings::incrementAndGet);

            binding.refresh();
            KeyCombination expected = QuickCaptureShortcutBinding.combinationFor(
                    QuickCapturePreferences.DEFAULT_SHORTCUT);
            scene.getAccelerators().get(expected).run();

            assertEquals(1, openings.get());
        });
    }

    @Test
    void changingPreferenceRemovesPreviousAccelerator() throws Exception {
        FxTestSupport.run(() -> {
            Scene scene = new Scene(new StackPane());
            QuickCaptureShortcutBinding binding = new QuickCaptureShortcutBinding(
                    scene, preferences, () -> {});
            binding.refresh();
            KeyCombination previous = QuickCaptureShortcutBinding.combinationFor(
                    QuickCaptureShortcut.SHORTCUT_SHIFT_SPACE);

            preferences.setShortcut(QuickCaptureShortcut.SHORTCUT_ALT_SPACE);
            binding.refresh();
            KeyCombination current = QuickCaptureShortcutBinding.combinationFor(
                    QuickCaptureShortcut.SHORTCUT_ALT_SPACE);

            assertFalse(scene.getAccelerators().containsKey(previous));
            assertTrue(scene.getAccelerators().containsKey(current));
            assertEquals(1, scene.getAccelerators().size());
        });
    }

    @Test
    void disablingShortcutRemovesCaptureAccelerator() throws Exception {
        FxTestSupport.run(() -> {
            Scene scene = new Scene(new StackPane());
            QuickCaptureShortcutBinding binding = new QuickCaptureShortcutBinding(
                    scene, preferences, () -> {});
            binding.refresh();

            preferences.setShortcut(QuickCaptureShortcut.DISABLED);
            binding.refresh();

            assertTrue(scene.getAccelerators().isEmpty());
            assertNull(QuickCaptureShortcutBinding.combinationFor(QuickCaptureShortcut.DISABLED));
        });
    }
}
