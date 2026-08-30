package com.pessoal.agenda.model;

import java.util.List;
import java.util.Objects;

public record DayReviewSummary(
        DailyPlan plan,
        List<Task> completedTasks,
        List<Task> openTasks,
        List<TaskSession> sessions
) {
    public DayReviewSummary {
        Objects.requireNonNull(plan);
        completedTasks = List.copyOf(Objects.requireNonNull(completedTasks));
        openTasks = List.copyOf(Objects.requireNonNull(openTasks));
        sessions = List.copyOf(Objects.requireNonNull(sessions));
    }
}
