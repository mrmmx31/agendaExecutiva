package com.pessoal.agenda.model;

/**
 * Prioridade de uma tarefa — baseada na Matriz de Eisenhower adaptada para rotina científica.
 */
public enum TaskPriority {
    CRITICA("Crítica",  "#b71c1c"),
    ALTA   ("Alta",     "#e65100"),
    NORMAL ("Normal",   "#1565c0"),
    BAIXA  ("Baixa",    "#2e7d32");

    private final String label;
    private final String color;

    TaskPriority(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public String color() { return color; }

    @Override
    public String toString() { return label; }
}

