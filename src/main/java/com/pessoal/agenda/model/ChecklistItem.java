package com.pessoal.agenda.model;

public record ChecklistItem(long id, String protocolName, String itemText, boolean done, String category) {
    /** Construtor de compatibilidade sem categoria. */
    public ChecklistItem(long id, String protocolName, String itemText, boolean done) {
        this(id, protocolName, itemText, done, "Geral");
    }
}
