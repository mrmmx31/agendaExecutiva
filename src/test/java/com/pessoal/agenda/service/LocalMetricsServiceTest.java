package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.LocalMetricType;
import com.pessoal.agenda.repository.LocalMetricsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMetricsServiceTest {
    @TempDir
    Path tempDir;

    private Preferences preferences;
    private LocalMetricsRepository repository;
    private LocalMetricsService service;
    private AtomicLong nanoTime;

    @BeforeEach
    void setUp() {
        Database database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        repository = new LocalMetricsRepository(database);
        preferences = Preferences.userRoot().node(
                "/agenda-tests/local-metrics-" + System.nanoTime());
        nanoTime = new AtomicLong(1_000_000_000L);
        service = new LocalMetricsService(repository, preferences,
                Clock.fixed(Instant.parse("2026-08-30T14:00:00Z"), ZoneOffset.UTC),
                nanoTime::get);
    }

    @AfterEach
    void tearDown() throws Exception {
        preferences.removeNode();
    }

    @Test
    void remainsDisabledByDefaultAndDoesNotCollectEvents() {
        assertFalse(service.isEnabled());

        service.beginSession();
        service.recordFocusAction();
        service.recordQuickCapture(1);
        service.recordInterruptionResume(1);

        var snapshot = service.snapshot();
        assertEquals(0, snapshot.focusStart().samples());
        assertEquals(0, snapshot.quickCapture().samples());
        assertEquals(0, snapshot.interruptionResume().samples());
        assertNull(snapshot.focusStart().median());
    }

    @Test
    void recordsOnlyFirstFocusActionInSessionAndCalculatesMedians() {
        service.setEnabled(true);
        nanoTime.addAndGet(12_000_000_000L);
        service.recordFocusAction();
        nanoTime.addAndGet(20_000_000_000L);
        service.recordFocusAction();
        service.recordQuickCapture(1);
        service.recordQuickCapture(2);
        service.recordInterruptionResume(2);

        var snapshot = service.snapshot();
        assertEquals(1, snapshot.focusStart().samples());
        assertEquals(12.0, snapshot.focusStart().median());
        assertEquals(1.5, snapshot.quickCapture().median());
        assertEquals(2.0, snapshot.interruptionResume().median());
    }

    @Test
    void clearRemovesOnlyLocalMetricHistoryAndKeepsPreference() {
        service.setEnabled(true);
        service.recordQuickCapture(1);

        service.clear();

        assertTrue(service.isEnabled());
        assertEquals(0, service.snapshot().quickCapture().samples());
    }

    @Test
    void repositoryRetainsAtMostTwoHundredEventsPerType() {
        Instant base = Instant.parse("2026-08-30T14:00:00Z");
        for (int index = 0; index < 205; index++) {
            repository.save(LocalMetricType.QUICK_CAPTURE_ACTIONS, index,
                    base.plusSeconds(index));
        }

        var values = repository.findRecentValues(LocalMetricType.QUICK_CAPTURE_ACTIONS, 300);
        assertEquals(200, values.size());
        assertEquals(204L, values.getFirst());
        assertEquals(5L, values.getLast());
    }

    @Test
    void metricWriteFailureNeverInterruptsTheMeasuredWorkflow() {
        LocalMetricsRepository failingRepository = new LocalMetricsRepository(
                new Database(tempDir.resolve("agenda-test.db"))) {
            @Override
            public void save(LocalMetricType type, long value, Instant occurredAt) {
                throw new RuntimeException("Falha simulada");
            }
        };
        LocalMetricsService failingService = new LocalMetricsService(
                failingRepository, preferences,
                Clock.fixed(Instant.parse("2026-08-30T14:00:00Z"), ZoneOffset.UTC),
                nanoTime::get);
        failingService.setEnabled(true);

        assertDoesNotThrow(failingService::recordFocusAction);
        assertDoesNotThrow(() -> failingService.recordQuickCapture(1));
        assertDoesNotThrow(() -> failingService.recordInterruptionResume(1));
    }
}
