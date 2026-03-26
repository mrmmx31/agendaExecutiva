package com.pessoal.agenda.model;

import java.time.LocalDate;

/**
 * Entidade de dominio: tarefa da agenda científica.
 *
 * Campos de agendamento:
 *   scheduleType    — SINGLE | RANGE | WEEKLY
 *   dueDate         — data única (SINGLE) ou início do intervalo (RANGE/WEEKLY)
 *   endDate         — fim do intervalo; null para SINGLE
 *   recurrenceDays  — dias ativos no formato strftime('%w'): "1,3,5" = Seg,Qua,Sex
 *
 * Campos de tempo e prioridade:
 *   startTime  — horário de início no formato HH:MM (nullable)
 *   endTime    — horário de término no formato HH:MM (nullable)
 *   priority   — CRITICA | ALTA | NORMAL | BAIXA
 *   status     — PENDENTE | EM_ANDAMENTO | CONCLUIDA | BLOQUEADA | CANCELADA
 */
public record Task(long id, String title, String notes, LocalDate dueDate, boolean done,
                   String category, ScheduleType scheduleType, LocalDate endDate,
                   String recurrenceDays, String startTime, String endTime,
                   TaskPriority priority, TaskStatus status) {

    /** Construtor de compatibilidade total para código legado (apenas campos básicos). */
    public Task(long id, String title, String notes, LocalDate dueDate, boolean done, String category) {
        this(id, title, notes, dueDate, done, category,
             ScheduleType.SINGLE, null, null, null, null,
             TaskPriority.NORMAL, TaskStatus.PENDENTE);
    }

    /** Construtor de compatibilidade com agendamento mas sem tempo/prioridade. */
    public Task(long id, String title, String notes, LocalDate dueDate, boolean done,
                String category, ScheduleType scheduleType, LocalDate endDate, String recurrenceDays) {
        this(id, title, notes, dueDate, done, category,
             scheduleType, endDate, recurrenceDays, null, null,
             TaskPriority.NORMAL, TaskStatus.PENDENTE);
    }

    public boolean isOverdue()  { return !done && effectiveEndDate().isBefore(LocalDate.now()); }
    public boolean isDueToday() { return isActiveOn(LocalDate.now()); }

    public boolean isActiveOn(LocalDate date) {
        if (done) return false;
        return switch (scheduleType != null ? scheduleType : ScheduleType.SINGLE) {
            case SINGLE -> dueDate.equals(date);
            case RANGE  -> !date.isBefore(dueDate) && !date.isAfter(effectiveEndDate());
            case WEEKLY -> !date.isBefore(dueDate) && !date.isAfter(effectiveEndDate())
                           && isRecurringOnDayOf(date);
        };
    }

    private LocalDate effectiveEndDate() {
        return endDate != null ? endDate : dueDate;
    }

    private boolean isRecurringOnDayOf(LocalDate date) {
        if (recurrenceDays == null || recurrenceDays.isBlank()) return true;
        int sqliteDay = date.getDayOfWeek().getValue() % 7;
        return ("," + recurrenceDays + ",").contains("," + sqliteDay + ",");
    }
}
