package com.pessoal.agenda.ui.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionHistoryWindowTest {

    @Test
    void extractsTaskIdFromLegacySubject() {
        assertEquals(42L, SessionHistoryWindow.extractTaskIdFromSubject("Tarefa:#42 Revisar texto"));
        assertNull(SessionHistoryWindow.extractTaskIdFromSubject("Sessão de estudo"));
        assertNull(SessionHistoryWindow.extractTaskIdFromSubject(null));
    }

    @Test
    void escapesCsvCellsWithoutLosingContent() {
        assertEquals("\"\"", SessionHistoryWindow.csvCell(null));
        assertEquals("\"texto, com vírgula\"", SessionHistoryWindow.csvCell("texto, com vírgula"));
        assertEquals("\"linha 1\nlinha \"\"2\"\"\"",
                SessionHistoryWindow.csvCell("linha 1\nlinha \"2\""));
    }
}
