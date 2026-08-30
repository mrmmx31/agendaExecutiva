package com.pessoal.agenda.model;

public enum QuickCaptureShortcut {
    SHORTCUT_SHIFT_SPACE("Ctrl/Cmd+Shift+Espaço"),
    SHORTCUT_ALT_SPACE("Ctrl/Cmd+Alt+Espaço"),
    SHORTCUT_SHIFT_C("Ctrl/Cmd+Shift+C"),
    DISABLED("Sem atalho");

    private final String label;

    QuickCaptureShortcut(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
