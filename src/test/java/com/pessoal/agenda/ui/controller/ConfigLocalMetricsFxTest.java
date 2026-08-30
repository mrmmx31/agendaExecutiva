package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.LocalMetricsRepository;
import com.pessoal.agenda.service.LocalMetricsService;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.view.FxTestSupport;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ConfigLocalMetricsFxTest {
    @TempDir
    Path tempDir;

    private Preferences metricPreferences;
    private Preferences notificationPreferences;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() {
        metricPreferences = Preferences.userRoot().node(
                "/agenda-tests/config-metrics-" + System.nanoTime());
        notificationPreferences = Preferences.userRoot().node(
                "/agenda-tests/config-metrics-notifications-" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        metricPreferences.removeNode();
        notificationPreferences.removeNode();
    }

    @Test
    void metricsAreOptInAndSettingRefreshesDashboardVisibility() throws Exception {
        Database database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        LocalMetricsService metrics = new LocalMetricsService(
                new LocalMetricsRepository(database), metricPreferences);
        AtomicInteger refreshes = new AtomicInteger();
        AtomicReference<String> status = new AtomicReference<>();
        SharedContext context = new SharedContext(status::set);
        ConfigController controller = new ConfigController(
                context, () -> {}, () -> {},
                new PendencyNotificationService(notificationPreferences),
                metrics, refreshes::incrementAndGet);

        FxTestSupport.run(() -> {
            VBox section = controller.buildLocalMetricsSection();
            CheckBox enabled = (CheckBox) section.lookup("#local-metrics-enabled");

            assertFalse(enabled.isSelected());
            assertFalse(metrics.isEnabled());
            assertTrue(section.lookup("#local-metrics-clear") instanceof Button);
            enabled.fire();

            assertTrue(metrics.isEnabled());
            assertEquals(1, refreshes.get());
            assertEquals("Métricas locais ativadas.", status.get());
        });
    }
}
