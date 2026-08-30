package com.pessoal.agenda.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolve a origem do foco sem depender da ordem incidental de consultas. */
public final class FocusSelectionService {
    public enum Origin {
        TIMER,
        MANUAL,
        DAILY_PLAN,
        AUTOMATIC
    }

    public record Selection(long taskId, Origin origin) {}

    public Optional<Selection> select(Long activeTimerTaskId,
                                      Long manualTaskId,
                                      Long dailyPlanTaskId,
                                      List<Long> automaticTaskIds,
                                      Set<Long> availableTaskIds) {
        Optional<Selection> timer = available(activeTimerTaskId, Origin.TIMER, availableTaskIds);
        if (timer.isPresent()) return timer;

        Optional<Selection> manual = available(manualTaskId, Origin.MANUAL, availableTaskIds);
        if (manual.isPresent()) return manual;

        Optional<Selection> dailyPlan = available(dailyPlanTaskId, Origin.DAILY_PLAN, availableTaskIds);
        if (dailyPlan.isPresent()) return dailyPlan;

        return automaticTaskIds.stream()
                .filter(availableTaskIds::contains)
                .findFirst()
                .map(taskId -> new Selection(taskId, Origin.AUTOMATIC));
    }

    private Optional<Selection> available(Long taskId, Origin origin, Set<Long> availableTaskIds) {
        if (taskId == null || taskId <= 0 || !availableTaskIds.contains(taskId)) {
            return Optional.empty();
        }
        return Optional.of(new Selection(taskId, origin));
    }
}
