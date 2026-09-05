package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.view.FxTestSupport;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ConfigMobilePairingFxTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void stateUpdatesImmediatelyAfterManagerReportsPairingChange() throws Exception {
        FakeActions actions = new FakeActions();

        FxTestSupport.run(() -> {
            VBox section = controller().buildMobilePairingSection(actions);
            Label state = (Label) section.lookup("#mobile-pairing-state");
            Button manage = (Button) section.lookup("#mobile-pairing-manage");

            assertEquals("Nenhum dispositivo conectado", state.getText());
            manage.fire();

            assertEquals("Conectado: motorola edge 60", state.getText());
            assertTrue(state.getStyleClass().contains("t-success"));
        });
    }

    @Test
    void refreshButtonReadsExternalDeviceChanges() throws Exception {
        FakeActions actions = new FakeActions();

        FxTestSupport.run(() -> {
            VBox section = controller().buildMobilePairingSection(actions);
            Label state = (Label) section.lookup("#mobile-pairing-state");
            Button refresh = (Button) section.lookup("#mobile-pairing-refresh");
            actions.names.addAll(List.of("Telefone", "Tablet"));

            refresh.fire();

            assertEquals("2 dispositivos conectados: Telefone, Tablet", state.getText());
        });
    }

    private ConfigController controller() {
        Preferences preferences = Preferences.userRoot().node(
                "/agenda-tests/config-mobile-" + System.nanoTime());
        return new ConfigController(new SharedContext(message -> {}), () -> {}, () -> {},
                new PendencyNotificationService(preferences));
    }

    private static final class FakeActions implements ConfigController.MobilePairingActions {
        private final List<String> names = new ArrayList<>();

        @Override
        public List<String> activeDeviceNames() {
            return List.copyOf(names);
        }

        @Override
        public void openManager(Runnable stateChanged) {
            names.add("motorola edge 60");
            stateChanged.run();
        }
    }
}
