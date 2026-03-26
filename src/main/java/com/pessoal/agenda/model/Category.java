package com.pessoal.agenda.model;

/**
 * Categoria definida pelo usuario para organizar tarefas, protocolos, estudos ou projetos.
 */
public record Category(long id, String name, CategoryDomain domain, String color) {

    /** Construtor sem cor (nullable). */
    public Category(long id, String name, CategoryDomain domain) {
        this(id, name, domain, null);
    }

    @Override
    public String toString() { return name; }
}

