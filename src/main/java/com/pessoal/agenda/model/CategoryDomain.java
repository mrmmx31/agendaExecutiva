package com.pessoal.agenda.model;

/**
 * Dominio de uma categoria — determina a aba onde ela e utilizada.
 */
public enum CategoryDomain {
    TASK("Tarefas"),
    CHECKLIST("Protocolos"),
    STUDY("Estudos"),
    STUDY_TYPE("Tipos de Estudo"),
    IDEA("Projetos");

    private final String label;

    CategoryDomain(String label) { this.label = label; }

    public String label() { return label; }
}

