package com.pessoal.agenda.model;

/**
 * Estado de execução de uma tarefa.
 * Permite granularidade além do simples done/não-done,
 * útil para acompanhar experimentos e atividades em andamento.
 */
public enum TaskStatus {
    PENDENTE     ("Pendente"),
    EM_ANDAMENTO ("Em andamento"),
    CONCLUIDA    ("Concluída"),
    BLOQUEADA    ("Bloqueada"),
    CANCELADA    ("Cancelada");

    private final String label;

    TaskStatus(String label) { this.label = label; }

    public String label() { return label; }

    @Override
    public String toString() { return label; }
}

