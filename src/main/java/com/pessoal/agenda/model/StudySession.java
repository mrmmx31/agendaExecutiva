package com.pessoal.agenda.model;
import java.time.LocalDate;
public record StudySession(long id, String subject, LocalDate sessionDate, int durationMinutes, String notes, String category) {
    /** Construtor de compatibilidade sem categoria. */
    public StudySession(long id, String subject, LocalDate sessionDate, int durationMinutes, String notes) {
        this(id, subject, sessionDate, durationMinutes, notes, "Geral");
    }
}
