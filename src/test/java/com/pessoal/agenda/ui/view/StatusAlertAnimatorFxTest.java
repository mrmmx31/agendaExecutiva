package com.pessoal.agenda.ui.view;

import javafx.scene.control.Label;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("javafx-ui")
class StatusAlertAnimatorFxTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestSupport.startToolkit();
    }

    @Test
    void runsOnlyThreeCyclesAndRestoresFullOpacity() throws Exception {
        AtomicReference<Label> badge = new AtomicReference<>();
        StatusAlertAnimator animator = FxTestSupport.call(() -> {
            badge.set(new Label("PENDÊNCIAS"));
            StatusAlertAnimator created = new StatusAlertAnimator(badge.get(), Duration.millis(20));
            created.play();
            assertEquals(3, created.cycleCount());
            assertTrue(created.isRunning());
            return created;
        });

        Thread.sleep(300);

        FxTestSupport.run(() -> {
            assertFalse(animator.isRunning());
            assertEquals(1.0, badge.get().getOpacity());
        });
    }

    @Test
    void stopEndsMovementAndRestoresOpacityImmediately() throws Exception {
        FxTestSupport.run(() -> {
            Label badge = new Label("PENDÊNCIAS");
            StatusAlertAnimator animator = new StatusAlertAnimator(badge, Duration.seconds(2));
            animator.play();
            badge.setOpacity(0.45);

            animator.stop();

            assertFalse(animator.isRunning());
            assertEquals(1.0, badge.getOpacity());
        });
    }
}
