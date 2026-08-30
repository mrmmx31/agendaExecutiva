package com.pessoal.agenda.service;

import com.pessoal.agenda.model.DailyPlan;
import com.pessoal.agenda.model.DayReviewSummary;
import com.pessoal.agenda.model.DayReviewDecision;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.DailyPlanRepository;
import com.pessoal.agenda.repository.DayReviewRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.TaskSessionRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DayReviewService {
    private final DailyPlanRepository dailyPlanRepository;
    private final TaskRepository taskRepository;
    private final TaskSessionRepository taskSessionRepository;
    private final DayReviewRepository dayReviewRepository;
    private final Clock clock;

    public DayReviewService(DailyPlanRepository dailyPlanRepository,
                            TaskRepository taskRepository,
                            TaskSessionRepository taskSessionRepository,
                            DayReviewRepository dayReviewRepository) {
        this(dailyPlanRepository, taskRepository, taskSessionRepository,
                dayReviewRepository, Clock.systemUTC());
    }

    DayReviewService(DailyPlanRepository dailyPlanRepository,
                     TaskRepository taskRepository, TaskSessionRepository taskSessionRepository,
                     DayReviewRepository dayReviewRepository, Clock clock) {
        this.dailyPlanRepository = Objects.requireNonNull(dailyPlanRepository);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.taskSessionRepository = Objects.requireNonNull(taskSessionRepository);
        this.dayReviewRepository = Objects.requireNonNull(dayReviewRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Optional<DayReviewSummary> summary(LocalDate date) {
        if (date == null) return Optional.empty();
        return dailyPlanRepository.findByDate(date).map(plan -> {
            List<Task> plannedTasks = plan.items().stream()
                    .map(item -> taskRepository.findById(item.taskId()))
                    .flatMap(Optional::stream)
                    .toList();
            List<Task> completed = plannedTasks.stream()
                    .filter(DayReviewService::isCompleted)
                    .toList();
            List<Task> open = plannedTasks.stream()
                    .filter(task -> !isCompleted(task))
                    .filter(task -> task.status() != TaskStatus.CANCELADA)
                    .toList();
            return new DayReviewSummary(plan, completed, open,
                    taskSessionRepository.findByDateRange(date, date));
        });
    }

    public Optional<Task> tomorrowInitialTask(LocalDate date) {
        if (date == null) return Optional.empty();
        return dailyPlanRepository.findByDate(date.plusDays(1))
                .flatMap(DailyPlan::essentialItem)
                .flatMap(item -> taskRepository.findById(item.taskId()));
    }

    public DayReviewSummary closeDay(LocalDate date, String closingNote) {
        DayReviewSummary current = summary(date)
                .orElseThrow(() -> new IllegalStateException("Não há plano para encerrar"));
        Map<Long, DayReviewDecision> decisions = new LinkedHashMap<>();
        current.openTasks().forEach(task -> decisions.put(task.id(), DayReviewDecision.KEEP_DATE));
        return closeDay(date, closingNote, decisions, null);
    }

    public DayReviewSummary closeDay(LocalDate date, String closingNote,
                                     Map<Long, DayReviewDecision> decisions,
                                     Long tomorrowInitialTaskId) {
        DayReviewSummary current = summary(date)
                .orElseThrow(() -> new IllegalStateException("Não há plano para encerrar"));
        if (current.plan().closedAt() != null) return current;
        Map<Long, DayReviewDecision> requested = decisions == null
                ? Map.of() : Map.copyOf(decisions);
        List<Long> openTaskIds = current.openTasks().stream().map(Task::id).toList();
        if (requested.size() != openTaskIds.size()
                || !requested.keySet().containsAll(openTaskIds)) {
            throw new IllegalArgumentException("Escolha uma decisão para cada item aberto");
        }
        if (tomorrowInitialTaskId != null
                && requested.get(tomorrowInitialTaskId) != DayReviewDecision.TOMORROW) {
            throw new IllegalArgumentException(
                    "A tarefa inicial precisa estar entre os itens enviados para amanhã");
        }
        dayReviewRepository.applyAndClose(date, requested, tomorrowInitialTaskId,
                normalizeNote(closingNote), clock.instant());
        return summary(date).orElseThrow();
    }

    public DayReviewSummary reopenDay(LocalDate date) {
        DailyPlan plan = requirePlan(date);
        if (plan.closedAt() != null || plan.closingNote() != null) {
            dailyPlanRepository.save(new DailyPlan(
                    plan.planDate(), plan.capacity(), plan.createdAt(), null, null,
                    plan.items()));
        }
        return summary(date).orElseThrow();
    }

    private DailyPlan requirePlan(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("A data é obrigatória");
        return dailyPlanRepository.findByDate(date)
                .orElseThrow(() -> new IllegalStateException("Não há plano para encerrar"));
    }

    private static boolean isCompleted(Task task) {
        return task.done() || task.status() == TaskStatus.CONCLUIDA;
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }
}
