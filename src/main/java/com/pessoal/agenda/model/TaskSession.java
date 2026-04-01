package com.pessoal.agenda.model;

import java.time.LocalDate;

/**
 * Representa uma sessão de trabalho registrada para uma tarefa.
 * Persistida na tabela existente `study_sessions` (reaproveitada).
 */
public record TaskSession(long id, long taskId, String subject, LocalDate sessionDate,
                          int durationMinutes, String notes) {

    public TaskSession(long id, long taskId, String subject, LocalDate sessionDate, int durationMinutes, String notes) {
        this.id = id; // records auto-assign
        this.taskId = taskId;
        this.subject = subject;
        this.sessionDate = sessionDate;
        this.durationMinutes = durationMinutes;
        this.notes = notes;
    }

    public String getSubject() { return subject(); }
    public int getDurationMinutes() { return durationMinutes(); }
    public String getNotes() { return notes(); }
}
