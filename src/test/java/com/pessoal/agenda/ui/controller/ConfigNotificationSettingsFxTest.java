package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.view.FxTestSupport;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class ConfigNotificationSettingsFxTest {
    private Preferences testNode;
    private PendencyNotificationService service;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void setUp() {
        testNode = Preferences.userRoot().node(
                "/com/pessoal/agenda/tests/config-notifications/" + UUID.randomUUID());
        service = new PendencyNotificationService(testNode);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        service.stop();
        testNode.removeNode();
    }

    @Test
    void masterControlDisablesStimuliAndRestoresPreservedChoices() throws Exception {
        service.setSoundEnabled(true);
        service.setBadgeAnimationEnabled(true);
        service.setQuietHoursEnabled(true);

        FxTestSupport.run(() -> {
            VBox section = buildSection(new AtomicInteger(), new AtomicReference<>());
            CheckBox enabled = checkBox(section, "#notifications-enabled");
            CheckBox sound = checkBox(section, "#notifications-sound");
            CheckBox animation = checkBox(section, "#notifications-animation");
            CheckBox quiet = checkBox(section, "#notifications-quiet-enabled");
            Button testSound = (Button) section.lookup("#notifications-test-sound");

            enabled.fire();

            assertFalse(service.isEnabled());
            assertTrue(sound.isDisabled());
            assertTrue(animation.isDisabled());
            assertTrue(quiet.isDisabled());
            assertTrue(testSound.isDisabled());
            assertTrue(service.isSoundEnabled());
            assertTrue(service.isBadgeAnimationEnabled());
            assertTrue(service.isQuietHoursEnabled());

            enabled.fire();

            assertTrue(service.isEnabled());
            assertTrue(sound.isSelected());
            assertTrue(animation.isSelected());
            assertTrue(quiet.isSelected());
            assertFalse(testSound.isDisabled());
        });
    }

    @Test
    void controlsPersistIndependentSettingsAndInvokeVisualRefresh() throws Exception {
        AtomicInteger visualRefreshes = new AtomicInteger();
        AtomicReference<String> status = new AtomicReference<>();

        FxTestSupport.run(() -> {
            VBox section = buildSection(visualRefreshes, status);
            CheckBox sound = checkBox(section, "#notifications-sound");
            CheckBox animation = checkBox(section, "#notifications-animation");
            CheckBox quiet = checkBox(section, "#notifications-quiet-enabled");
            ComboBox<Integer> interval = combo(section, "#notifications-interval");
            ComboBox<LocalTime> quietStart = combo(section, "#notifications-quiet-start");
            ComboBox<LocalTime> quietEnd = combo(section, "#notifications-quiet-end");

            sound.fire();
            animation.fire();
            interval.setValue(30);
            interval.fireEvent(new ActionEvent());
            quiet.fire();
            quietStart.setValue(LocalTime.of(21, 30));
            quietStart.fireEvent(new ActionEvent());
            quietEnd.setValue(LocalTime.of(6, 30));
            quietEnd.fireEvent(new ActionEvent());

            assertTrue(service.isSoundEnabled());
            assertTrue(service.isBadgeAnimationEnabled());
            assertEquals(30, service.getIntervalMinutes());
            assertTrue(service.isQuietHoursEnabled());
            assertEquals(LocalTime.of(21, 30), service.getQuietHoursStart());
            assertEquals(LocalTime.of(6, 30), service.getQuietHoursEnd());
            assertEquals(1, visualRefreshes.get());
            assertTrue(status.get().contains("21:30 até 06:30"));
        });
    }

    private VBox buildSection(AtomicInteger visualRefreshes, AtomicReference<String> status) {
        SharedContext context = new SharedContext(status::set);
        ConfigController controller = new ConfigController(
                context, visualRefreshes::incrementAndGet, () -> {}, service);
        return controller.buildNotificationSection();
    }

    private CheckBox checkBox(VBox section, String selector) {
        return (CheckBox) section.lookup(selector);
    }

    @SuppressWarnings("unchecked")
    private <T> ComboBox<T> combo(VBox section, String selector) {
        return (ComboBox<T>) section.lookup(selector);
    }
}
