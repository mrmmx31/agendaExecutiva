package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.LocalMetricType;
import com.pessoal.agenda.repository.LocalMetricsRepository;
import com.pessoal.agenda.service.LocalMetricsService;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class LocalMetricsPanelFxTest {
    @TempDir
    Path tempDir;

    private Preferences preferences;
    private LocalMetricsRepository repository;
    private LocalMetricsService service;
    private ThemeManager.Theme originalTheme;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() {
        Database database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        repository = new LocalMetricsRepository(database);
        preferences = Preferences.userRoot().node(
                "/agenda-tests/metrics-panel-" + System.nanoTime());
        service = new LocalMetricsService(repository, preferences);
        originalTheme = ThemeManager.getInstance().getTheme();
    }

    @AfterEach
    void tearDown() throws Exception {
        ThemeManager.getInstance().setTheme(originalTheme);
        preferences.removeNode();
    }

    @Test
    void remainsEntirelyHiddenWhileCollectionIsDisabled() throws Exception {
        FxTestSupport.run(() -> {
            LocalMetricsPanel panel = new LocalMetricsPanel(service);
            assertFalse(panel.isVisible());
            assertFalse(panel.isManaged());
        });
    }

    @Test
    void showsLocalMediansInNarrowReadableDarkLayout() throws Exception {
        service.setEnabled(true);
        Instant now = Instant.parse("2026-08-30T14:00:00Z");
        repository.save(LocalMetricType.FOCUS_START_SECONDS, 12, now);
        repository.save(LocalMetricType.FOCUS_START_SECONDS, 20, now.plusSeconds(1));
        repository.save(LocalMetricType.QUICK_CAPTURE_ACTIONS, 1, now);
        repository.save(LocalMetricType.INTERRUPTION_RESUME_ACTIONS, 2, now);

        FxTestSupport.run(() -> {
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.ESCURO);
            LocalMetricsPanel panel = new LocalMetricsPanel(service);
            Scene scene = new Scene(new StackPane(panel), 340, 290);
            ThemeManager.getInstance().applyTo(scene);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals("16s · mediana de 2 registro(s)",
                    ((Label) panel.lookup("#local-metrics-focus")).getText());
            assertTrue(((Label) panel.lookup("#local-metrics-capture")).getText()
                    .startsWith("1 ação(ões)"));
            for (Node node : panel.lookupAll(".label")) {
                Bounds bounds = node.localToScene(node.getBoundsInLocal());
                assertTrue(bounds.getMaxX() <= scene.getWidth());
                Color color = (Color) ((Label) node).getTextFill();
                assertTrue(color.getBrightness() >= 0.45,
                        () -> "Texto escuro nas métricas: " + ((Label) node).getText());
            }
        });
    }
}
