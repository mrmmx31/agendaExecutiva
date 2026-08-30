package com.pessoal.agenda.service;

import com.pessoal.agenda.model.QuickCaptureShortcut;

import java.util.Objects;
import java.util.prefs.Preferences;

public class QuickCapturePreferences {
    private static final String KEY_SHORTCUT = "quickCapture.shortcut";
    public static final QuickCaptureShortcut DEFAULT_SHORTCUT =
            QuickCaptureShortcut.SHORTCUT_SHIFT_SPACE;

    private final Preferences preferences;

    public QuickCapturePreferences() {
        this(Preferences.userNodeForPackage(QuickCapturePreferences.class));
    }

    public QuickCapturePreferences(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    public QuickCaptureShortcut getShortcut() {
        String stored = preferences.get(KEY_SHORTCUT, DEFAULT_SHORTCUT.name());
        try {
            return QuickCaptureShortcut.valueOf(stored);
        } catch (IllegalArgumentException invalidPreference) {
            return DEFAULT_SHORTCUT;
        }
    }

    public void setShortcut(QuickCaptureShortcut shortcut) {
        preferences.put(KEY_SHORTCUT, Objects.requireNonNull(shortcut).name());
    }

    public void restoreDefault() {
        preferences.remove(KEY_SHORTCUT);
    }
}
