package com.pessoal.agenda.service;

import com.pessoal.agenda.model.DailyPlan;
import com.pessoal.agenda.model.DailyPlanCapacity;
import com.pessoal.agenda.model.DailyPlanItem;
import com.pessoal.agenda.model.DailyPlanRole;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.DailyPlanRepository;
import com.pessoal.agenda.repository.TaskRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DailyPlanService {
    private final DailyPlanRepository repository;
    private final TaskRepository taskRepository;
    private final Clock clock;

    public DailyPlanService(DailyPlanRepository repository, TaskRepository taskRepository) {
        this(repository, taskRepository, Clock.systemUTC());
    }

    DailyPlanService(DailyPlanRepository repository, TaskRepository taskRepository, Clock clock) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    public DailyPlan savePlan(LocalDate date, DailyPlanCapacity capacity,
                              long essentialTaskId, List<Long> supportTaskIds) {
        if (date == null) throw new IllegalArgumentException("A data do plano e obrigatoria");
        if (capacity == null) throw new IllegalArgumentException("A capacidade do dia e obrigatoria");
        List<Long> supports = supportTaskIds == null ? List.of() : List.copyOf(supportTaskIds);
        validateSelection(capacity, essentialTaskId, supports);

        Instant createdAt = repository.findByDate(date)
                .map(DailyPlan::createdAt)
                .orElseGet(clock::instant);
        List<DailyPlanItem> items = new ArrayList<>();
        items.add(new DailyPlanItem(0, date, essentialTaskId, DailyPlanRole.ESSENTIAL, 0));
        for (int index = 0; index < supports.size(); index++) {
            items.add(new DailyPlanItem(0, date, supports.get(index), DailyPlanRole.SUPPORT, index));
        }

        DailyPlan plan = new DailyPlan(date, capacity, createdAt, null, null, items);
        repository.save(plan);
        return repository.findByDate(date).orElseThrow();
    }

    public Optional<DailyPlan> findByDate(LocalDate date) {
        Optional<DailyPlan> plan = repository.findByDate(date);
        if (plan.isPresent()) {
            Optional<DailyPlanItem> essential = plan.get().essentialItem();
            if (essential.isEmpty() || !isOpenTask(essential.get().taskId())) {
                return Optional.empty();
            }
            List<Long> openSupportIds = plan.get().supportItems().stream()
                    .map(DailyPlanItem::taskId)
                    .filter(this::isOpenTask)
                    .toList();
            if (openSupportIds.size() != plan.get().supportItems().size()) {
                ArrayList<DailyPlanItem> openItems = new ArrayList<>();
                openItems.add(new DailyPlanItem(
                        0, date, essential.get().taskId(), DailyPlanRole.ESSENTIAL, 0));
                for (int index = 0; index < openSupportIds.size(); index++) {
                    openItems.add(new DailyPlanItem(
                            0, date, openSupportIds.get(index), DailyPlanRole.SUPPORT, index));
                }
                DailyPlan sanitized = new DailyPlan(
                        plan.get().planDate(), plan.get().capacity(), plan.get().createdAt(),
                        plan.get().closedAt(), plan.get().closingNote(), openItems);
                return Optional.of(sanitized);
            }
        }
        return plan;
    }

    public void deletePlan(LocalDate date) {
        if (date != null) repository.delete(date);
    }

    private void validateSelection(DailyPlanCapacity capacity, long essentialTaskId, List<Long> supports) {
        if (essentialTaskId <= 0) throw new IllegalArgumentException("Escolha uma tarefa essencial");
        if (supports.size() > 2) throw new IllegalArgumentException("Escolha no maximo duas tarefas de apoio");
        if (capacity == DailyPlanCapacity.REDUCED && !supports.isEmpty()) {
            throw new IllegalArgumentException("Capacidade reduzida aceita somente a tarefa essencial");
        }

        Set<Long> uniqueIds = new HashSet<>();
        uniqueIds.add(essentialTaskId);
        if (supports.stream().anyMatch(id -> id == null || id <= 0 || !uniqueIds.add(id))) {
            throw new IllegalArgumentException("Cada tarefa pode aparecer apenas uma vez no plano");
        }
        uniqueIds.forEach(this::requireOpenTask);
    }

    private void requireOpenTask(long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Tarefa nao encontrada: " + taskId));
        if (task.done() || task.status() == TaskStatus.CONCLUIDA || task.status() == TaskStatus.CANCELADA) {
            throw new IllegalArgumentException("O plano aceita somente tarefas abertas: " + task.title());
        }
    }

    private boolean isOpenTask(long taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> !task.done())
                .filter(task -> task.status() != TaskStatus.CONCLUIDA && task.status() != TaskStatus.CANCELADA)
                .isPresent();
    }
}
