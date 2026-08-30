package com.pessoal.agenda.model;

import java.util.Objects;

public record LocalMetricsSnapshot(
        LocalMetricSummary focusStart,
        LocalMetricSummary quickCapture,
        LocalMetricSummary interruptionResume) {
    public LocalMetricsSnapshot {
        Objects.requireNonNull(focusStart);
        Objects.requireNonNull(quickCapture);
        Objects.requireNonNull(interruptionResume);
    }
}
