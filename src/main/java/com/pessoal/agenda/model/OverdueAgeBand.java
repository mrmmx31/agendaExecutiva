package com.pessoal.agenda.model;

public enum OverdueAgeBand {
    UP_TO_7_DAYS("Até 7 dias"),
    DAYS_8_TO_30("8–30 dias"),
    OVER_30_DAYS("Mais de 30 dias");

    private final String label;

    OverdueAgeBand(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static OverdueAgeBand fromPendingDays(long days) {
        if (days < 1) throw new IllegalArgumentException("A tarefa ainda não está pendente");
        if (days <= 7) return UP_TO_7_DAYS;
        if (days <= 30) return DAYS_8_TO_30;
        return OVER_30_DAYS;
    }
}
