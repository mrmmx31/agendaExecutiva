package com.pessoal.agenda.service;

import com.pessoal.agenda.model.*;
import com.pessoal.agenda.repository.TaskRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Camada de negocio da agenda.
 * Mantem regras de aplicacao fora da View.
 */
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) { this.taskRepository = taskRepository; }

    // ── compatibilidade retroativa ────────────────────────────────────────
    /** Cria tarefa SINGLE (compatibilidade retroativa). */
    public void createTask(String title, String notes, LocalDate dueDate, String category) {
        createTask(title, notes, dueDate, category, ScheduleType.SINGLE, null, null);
    }

    public void createTask(String title, String notes, LocalDate startDate, String category,
                           ScheduleType scheduleType, LocalDate endDate, String recurrenceDays) {
        createTask(title, notes, startDate, category, scheduleType, endDate, recurrenceDays,
                   null, null, TaskPriority.NORMAL, TaskStatus.PENDENTE);
    }

    // ── criação completa ──────────────────────────────────────────────────
    public void createTask(String title, String notes, LocalDate startDate, String category,
                           ScheduleType scheduleType, LocalDate endDate, String recurrenceDays,
                           String startTime, String endTime,
                           TaskPriority priority, TaskStatus status) {
        validate(title, startDate, scheduleType, endDate, recurrenceDays);
        taskRepository.save(title.trim(), notes == null ? "" : notes.trim(),
                startDate, category, scheduleType, endDate, recurrenceDays,
                blankToNull(startTime), blankToNull(endTime), priority, status);
    }

    // ── edição ────────────────────────────────────────────────────────────
    public void updateTask(long id, String title, String notes, LocalDate startDate, String category,
                           ScheduleType scheduleType, LocalDate endDate, String recurrenceDays,
                           String startTime, String endTime,
                           TaskPriority priority, TaskStatus status) {
        validate(title, startDate, scheduleType, endDate, recurrenceDays);
        boolean done = (status == TaskStatus.CONCLUIDA);
        taskRepository.update(id, title.trim(), notes == null ? "" : notes.trim(),
                startDate, category, scheduleType, endDate, recurrenceDays,
                blankToNull(startTime), blankToNull(endTime), priority, status);
        // sincroniza coluna done com status
        if (done) taskRepository.markDone(id);
    }

    // ── exclusão ──────────────────────────────────────────────────────────
    public void deleteTask(long id) {
        taskRepository.deleteById(id);
    }

    // ── status ────────────────────────────────────────────────────────────
    public void markDone(long taskId) { taskRepository.markDone(taskId); }

    // ── consultas ─────────────────────────────────────────────────────────
    public Optional<Task> findById(long id)                        { return taskRepository.findById(id); }
    public List<Task> listByDay(LocalDate date, String cat)        { return taskRepository.findByDay(date, cat); }
    public List<Task> listByMonth(YearMonth month, String cat)     { return taskRepository.findByMonth(month, cat); }
    public List<MonthSummary> listYearOverview(int year, String cat){ return taskRepository.yearOverview(year, cat); }

    // ── helpers ───────────────────────────────────────────────────────────
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void validate(String title, LocalDate startDate,
                          ScheduleType st, LocalDate endDate, String recurrenceDays) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Titulo da tarefa e obrigatorio");
        if (startDate == null)
            throw new IllegalArgumentException("Data de inicio e obrigatoria");
        ScheduleType s = st != null ? st : ScheduleType.SINGLE;
        if (s == ScheduleType.RANGE || s == ScheduleType.WEEKLY) {
            if (endDate == null)
                throw new IllegalArgumentException("Data de termino e obrigatoria para este tipo de agendamento");
            if (endDate.isBefore(startDate))
                throw new IllegalArgumentException("Data de termino deve ser posterior ao inicio");
        }
        if (s == ScheduleType.WEEKLY && (recurrenceDays == null || recurrenceDays.isBlank()))
            throw new IllegalArgumentException("Selecione pelo menos um dia da semana");
    }
}
