package com.pessoal.agenda.model;

import java.time.LocalDate;
import java.util.Objects;

public record DailyPlanItem(
        long id,
        LocalDate planDate,
        long taskId,
        DailyPlanRole role,
        int position
) {
    public DailyPlanItem {
        Objects.requireNonNull(planDate, "planDate");
        Objects.requireNonNull(role, "role");
        if (id < 0) throw new IllegalArgumentException("id nao pode ser negativo");
        if (taskId <= 0) throw new IllegalArgumentException("taskId deve ser positivo");
        if (role == DailyPlanRole.ESSENTIAL && position != 0) {
            throw new IllegalArgumentException("A tarefa essencial deve ocupar a posicao zero");
        }
        if (role == DailyPlanRole.SUPPORT && (position < 0 || position > 1)) {
            throw new IllegalArgumentException("Tarefas de apoio devem ocupar as posicoes zero ou um");
        }
    }
}
