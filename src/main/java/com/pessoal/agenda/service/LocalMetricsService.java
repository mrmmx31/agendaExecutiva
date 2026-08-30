package com.pessoal.agenda.service;

import com.pessoal.agenda.model.LocalMetricSummary;
import com.pessoal.agenda.model.LocalMetricType;
import com.pessoal.agenda.model.LocalMetricsSnapshot;
import com.pessoal.agenda.repository.LocalMetricsRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.prefs.Preferences;

public class LocalMetricsService {
    private static final String KEY_ENABLED = "localMetrics.enabled";
    private static final int SNAPSHOT_LIMIT = 30;

    private final LocalMetricsRepository repository;
    private final Preferences preferences;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private Long sessionStartedAtNanos;
    private boolean focusRecordedThisSession;

    public LocalMetricsService(LocalMetricsRepository repository) {
        this(repository, Preferences.userNodeForPackage(LocalMetricsService.class),
                Clock.systemUTC(), System::nanoTime);
    }

    public LocalMetricsService(LocalMetricsRepository repository, Preferences preferences) {
        this(repository, preferences, Clock.systemUTC(), System::nanoTime);
    }

    LocalMetricsService(LocalMetricsRepository repository, Preferences preferences,
                        Clock clock, LongSupplier nanoTime) {
        this.repository = Objects.requireNonNull(repository);
        this.preferences = Objects.requireNonNull(preferences);
        this.clock = Objects.requireNonNull(clock);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.putBoolean(KEY_ENABLED, enabled);
        if (enabled) beginSession();
        else resetSession();
    }

    public void beginSession() {
        if (!isEnabled()) {
            resetSession();
            return;
        }
        sessionStartedAtNanos = nanoTime.getAsLong();
        focusRecordedThisSession = false;
    }

    public void recordFocusAction() {
        if (!isEnabled() || sessionStartedAtNanos == null || focusRecordedThisSession) return;
        long elapsedNanos = Math.max(0, nanoTime.getAsLong() - sessionStartedAtNanos);
        long elapsedSeconds = Math.round(elapsedNanos / 1_000_000_000.0);
        try {
            repository.save(LocalMetricType.FOCUS_START_SECONDS, elapsedSeconds, clock.instant());
            focusRecordedThisSession = true;
        } catch (RuntimeException ignored) {
            // Métricas opcionais nunca podem interromper o fluxo medido.
        }
    }

    public void recordQuickCapture(int actions) {
        recordActions(LocalMetricType.QUICK_CAPTURE_ACTIONS, actions);
    }

    public void recordInterruptionResume(int actions) {
        recordActions(LocalMetricType.INTERRUPTION_RESUME_ACTIONS, actions);
    }

    public LocalMetricsSnapshot snapshot() {
        return new LocalMetricsSnapshot(
                summarize(repository.findRecentValues(
                        LocalMetricType.FOCUS_START_SECONDS, SNAPSHOT_LIMIT)),
                summarize(repository.findRecentValues(
                        LocalMetricType.QUICK_CAPTURE_ACTIONS, SNAPSHOT_LIMIT)),
                summarize(repository.findRecentValues(
                        LocalMetricType.INTERRUPTION_RESUME_ACTIONS, SNAPSHOT_LIMIT)));
    }

    public void clear() {
        repository.deleteAll();
    }

    private void recordActions(LocalMetricType type, int actions) {
        if (!isEnabled()) return;
        if (actions <= 0) throw new IllegalArgumentException("A quantidade de ações deve ser positiva");
        try {
            repository.save(type, actions, clock.instant());
        } catch (RuntimeException ignored) {
            // Métricas opcionais nunca podem interromper o fluxo medido.
        }
    }

    private void resetSession() {
        sessionStartedAtNanos = null;
        focusRecordedThisSession = false;
    }

    static LocalMetricSummary summarize(List<Long> values) {
        if (values.isEmpty()) return LocalMetricSummary.empty();
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        return new LocalMetricSummary(sorted.size(), median);
    }
}
