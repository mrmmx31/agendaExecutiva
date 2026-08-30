package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.view.FxTestSupport;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ConfigGoogleTasksSettingsFxTest {
    private final List<Preferences> preferenceNodes = new ArrayList<>();

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @AfterEach
    void removePreferences() throws Exception {
        for (Preferences node : preferenceNodes) node.removeNode();
    }

    @Test
    void sectionShowsStateAndEnablesOnlyApplicableActions() throws Exception {
        FakeActions actions = new FakeActions();
        actions.credentials = true;
        actions.mappings = 3;

        FxTestSupport.run(() -> {
            VBox section = controller().buildGoogleTasksSection(actions);
            Button connect = button(section, "#google-settings-connect");
            Button disconnect = button(section, "#google-settings-disconnect");
            Button clearMappings = button(section, "#google-settings-clear-mappings");

            assertEquals("Desconectado", label(section, "#google-settings-connection").getText());
            assertEquals("Prontas", label(section, "#google-settings-credentials").getText());
            assertEquals("3 vínculos", label(section, "#google-settings-mappings").getText());
            assertFalse(connect.isDisabled());
            assertTrue(disconnect.isDisabled());
            assertFalse(clearMappings.isDisabled());
            assertTrue(section.getChildren().get(2) instanceof FlowPane);

            connect.fire();

            assertEquals(1, actions.connectCalls.get());
            assertEquals("Conectado", label(section, "#google-settings-connection").getText());
            assertTrue(connect.isDisabled());
            assertFalse(disconnect.isDisabled());
        });
    }

    @Test
    void missingCredentialsBlocksConnectionButKeepsSyncWindowReachable() throws Exception {
        FakeActions actions = new FakeActions();

        FxTestSupport.run(() -> {
            VBox section = controller().buildGoogleTasksSection(actions);

            assertEquals("Ausentes ou inválidas",
                    label(section, "#google-settings-credentials").getText());
            assertTrue(button(section, "#google-settings-connect").isDisabled());
            assertFalse(button(section, "#google-settings-open-sync").isDisabled());
            assertTrue(button(section, "#google-settings-clear-mappings").isDisabled());
        });
    }

    @Test
    void actionsWrapWithoutLeavingNarrowSettingsPane() throws Exception {
        FakeActions actions = new FakeActions();
        actions.credentials = true;
        actions.authorized = true;
        actions.mappings = 2;

        FxTestSupport.run(() -> {
            VBox section = controller().buildGoogleTasksSection(actions);
            FlowPane actionBar = (FlowPane) section.getChildren().get(2);
            Scene scene = new Scene(section, 390, 300);
            section.applyCss();
            section.layout();

            long rows = actionBar.getChildren().stream()
                    .map(node -> Math.round(node.localToScene(node.getBoundsInLocal()).getMinY()))
                    .distinct()
                    .count();
            assertTrue(rows > 1);
            for (javafx.scene.Node node : actionBar.getChildren()) {
                Bounds bounds = node.localToScene(node.getBoundsInLocal());
                assertTrue(bounds.getMinX() >= 0);
                assertTrue(bounds.getMaxX() <= scene.getWidth());
            }
        });
    }

    private ConfigController controller() {
        Preferences preferences = Preferences.userRoot().node(
                "/agenda-tests/config-google-" + System.nanoTime());
        preferenceNodes.add(preferences);
        return new ConfigController(new SharedContext(message -> {}), () -> {}, () -> {},
                new PendencyNotificationService(preferences));
    }

    private Button button(VBox section, String selector) {
        return (Button) section.lookup(selector);
    }

    private Label label(VBox section, String selector) {
        return (Label) section.lookup(selector);
    }

    private static final class FakeActions implements ConfigController.GoogleTasksSettingsActions {
        private boolean credentials;
        private boolean authorized;
        private int mappings;
        private final AtomicInteger connectCalls = new AtomicInteger();

        @Override public boolean hasValidCredentials() { return credentials; }
        @Override public boolean isAuthorized() { return authorized; }
        @Override public boolean isOperationRunning() { return false; }
        @Override public int mappingCount() { return mappings; }
        @Override public void connect(Consumer<Boolean> busy, Runnable stateChanged) {
            connectCalls.incrementAndGet();
            busy.accept(true);
            authorized = true;
            busy.accept(false);
            stateChanged.run();
        }
        @Override public void disconnect() { authorized = false; }
        @Override public void clearMappings() { mappings = 0; }
        @Override public void openSync() {}
    }
}
