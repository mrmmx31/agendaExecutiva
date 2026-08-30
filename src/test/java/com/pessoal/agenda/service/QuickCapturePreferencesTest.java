package com.pessoal.agenda.service;

import com.pessoal.agenda.model.QuickCaptureShortcut;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickCapturePreferencesTest {
    private Preferences testNode;
    private QuickCapturePreferences preferences;

    @BeforeEach
    void setUp() {
        testNode = Preferences.userRoot().node(
                "/com/pessoal/agenda/tests/quick-capture/" + UUID.randomUUID());
        preferences = new QuickCapturePreferences(testNode);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testNode.removeNode();
    }

    @Test
    void usesRequestedDefaultAndRestoresIt() {
        assertEquals(QuickCapturePreferences.DEFAULT_SHORTCUT, preferences.getShortcut());

        preferences.setShortcut(QuickCaptureShortcut.SHORTCUT_ALT_SPACE);
        preferences.restoreDefault();

        assertEquals(QuickCapturePreferences.DEFAULT_SHORTCUT, preferences.getShortcut());
    }

    @Test
    void persistsSelectedShortcutIncludingDisabled() {
        preferences.setShortcut(QuickCaptureShortcut.SHORTCUT_SHIFT_C);
        assertEquals(QuickCaptureShortcut.SHORTCUT_SHIFT_C,
                new QuickCapturePreferences(testNode).getShortcut());

        preferences.setShortcut(QuickCaptureShortcut.DISABLED);
        assertEquals(QuickCaptureShortcut.DISABLED,
                new QuickCapturePreferences(testNode).getShortcut());
    }

    @Test
    void invalidStoredValueFallsBackToDefault() {
        testNode.put("quickCapture.shortcut", "ATALHO_REMOVIDO");

        assertEquals(QuickCapturePreferences.DEFAULT_SHORTCUT, preferences.getShortcut());
    }
}
