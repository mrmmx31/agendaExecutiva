package com.pessoal.agenda.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Entrada individual no diário de estudo.
 * Cada entrada representa uma sessão com título, anotações livres,
 * duração e faixa de páginas (para livros).
 */
public record StudyEntry(
        long      id,
        long      studyId,
        LocalDate entryDate,
        String    entryTitle,
        String    content,
        int       durationMinutes,
        int       pageStart,
        int       pageEnd,
        int       entryOrder
) {
    public boolean hasPages()  { return pageStart > 0 || pageEnd > 0; }
    public boolean hasTitle()  { return entryTitle != null && !entryTitle.isBlank(); }

    /** Rótulo resumido para o painel de índice. */
    public String indexLabel() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));
        String dateStr = entryDate != null ? entryDate.format(fmt) : "--/--";
        String title   = hasTitle() ? entryTitle : "(sem título)";
        String pages   = hasPages() ? "  pp." + pageStart + "–" + pageEnd : "";
        return dateStr + "  " + title + pages;
    }

    @Override public String toString() { return indexLabel(); }
}

