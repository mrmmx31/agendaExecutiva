package com.pessoal.agenda.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DailyPlan(
        LocalDate planDate,
        DailyPlanCapacity capacity,
        Instant createdAt,
        Instant closedAt,
        String closingNote,
        List<DailyPlanItem> items
) {
    public DailyPlan {
        Objects.requireNonNull(planDate, "planDate");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(createdAt, "createdAt");
        items = List.copyOf(Objects.requireNonNull(items, "items"));

        long essentialCount = items.stream()
                .filter(item -> item.role() == DailyPlanRole.ESSENTIAL).count();
        long supportCount = items.stream()
                .filter(item -> item.role() == DailyPlanRole.SUPPORT).count();
        if (essentialCount > 1) throw new IllegalArgumentException("O plano aceita uma unica tarefa essencial");
        if (supportCount > 2) throw new IllegalArgumentException("O plano aceita no maximo duas tarefas de apoio");
        if (capacity == DailyPlanCapacity.REDUCED && supportCount > 0) {
            throw new IllegalArgumentException("Capacidade reduzida aceita somente a tarefa essencial");
        }
        if (items.stream().anyMatch(item -> !planDate.equals(item.planDate()))) {
            throw new IllegalArgumentException("Todos os itens devem pertencer a data do plano");
        }
        if (new HashSet<>(items.stream().map(DailyPlanItem::taskId).toList()).size() != items.size()) {
            throw new IllegalArgumentException("Uma tarefa nao pode aparecer duas vezes no plano");
        }
    }

    public Optional<DailyPlanItem> essentialItem() {
        return items.stream().filter(item -> item.role() == DailyPlanRole.ESSENTIAL).findFirst();
    }

    public List<DailyPlanItem> supportItems() {
        return items.stream().filter(item -> item.role() == DailyPlanRole.SUPPORT).toList();
    }
}
