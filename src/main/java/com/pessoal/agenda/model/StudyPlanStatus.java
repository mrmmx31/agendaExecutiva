package com.pessoal.agenda.model;

/** Estado de andamento de um plano de estudo. */
public enum StudyPlanStatus {
    PLANEJADO    ("Pendente"),
    EM_ANDAMENTO ("Ativo"),
    CONCLUIDO    ("Concluído"),
    PAUSADO      ("Inativo"),
    ABANDONADO   ("Cancelado");

    private final String label;
    StudyPlanStatus(String label) { this.label = label; }
    public String label()         { return label; }
    @Override public String toString() { return label; }
}
