package com.pessoal.agenda.model;

public enum DayReviewDecision {
    TOMORROW("Amanhã"),
    KEEP_DATE("Manter data"),
    RETURN_TO_INBOX("Voltar à caixa de entrada"),
    COMPLETE("Concluir");

    private final String label;

    DayReviewDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
