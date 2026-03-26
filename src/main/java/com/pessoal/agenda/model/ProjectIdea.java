package com.pessoal.agenda.model;

public record ProjectIdea(long id, String title, String description, String status, String category) {
    /** Construtor de compatibilidade sem categoria. */
    public ProjectIdea(long id, String title, String description, String status) {
        this(id, title, description, status, "Geral");
    }
}
