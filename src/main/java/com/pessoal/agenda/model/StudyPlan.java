package com.pessoal.agenda.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Plano de estudo — unidade central de acompanhamento.
 *
 * O tipo é armazenado como String para suportar tipos personalizados.
 * O nome canônico do tipo Livro é {@link #LIVRO} — usado para ativar
 * o rastreamento de progresso por páginas.
 */
public record StudyPlan(
        long            id,
        String          title,
        String          studyTypeName,   // ex.: "Livro", "Artigo Científico", tipos custom...
        String          category,
        String          description,
        LocalDate       startDate,
        LocalDate       targetDate,
        StudyPlanStatus status,
        int             totalPages,
        int             currentPage,
        double          progressPercent
) {
    /** Nome canônico do tipo Livro — ativa rastreamento por páginas. */
    public static final String LIVRO = "Livro";

    public boolean isBook() { return LIVRO.equalsIgnoreCase(studyTypeName); }

    /** Progresso efetivo: calculado por páginas (livro) ou manual (outros). */
    public double computedProgress() {
        if (isBook() && totalPages > 0)
            return Math.min(100.0, currentPage * 100.0 / totalPages);
        return Math.min(100.0, Math.max(0.0, progressPercent));
    }

    /** Texto descritivo do progresso para exibir na UI. */
    public String progressDisplay() {
        if (isBook() && totalPages > 0)
            return String.format("%.0f%%   pág. %d / %d", computedProgress(), currentPage, totalPages);
        return String.format("%.0f%%", computedProgress());
    }

    /** Dias restantes até a meta (negativo = prazo vencido; MAX_VALUE = sem meta). */
    public long daysUntilTarget() {
        return targetDate == null ? Long.MAX_VALUE
                : ChronoUnit.DAYS.between(LocalDate.now(), targetDate);
    }

    @Override public String toString() { return title; }
}
