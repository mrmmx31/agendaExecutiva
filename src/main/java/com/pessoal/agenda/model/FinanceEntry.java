package com.pessoal.agenda.model;
import java.time.LocalDate;
public record FinanceEntry(long id, String entryType, String description, double amount, LocalDate dueDate, boolean paid) {
    public boolean isOverdue() { return !paid && dueDate != null && dueDate.isBefore(LocalDate.now()); }
}
