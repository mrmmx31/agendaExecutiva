package com.pessoal.agenda.ui.view;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

public final class StatusAlertAnimator {
    static final int ATTENTION_CYCLES = 3;

    private final Node target;
    private final Timeline timeline;

    public StatusAlertAnimator(Node target) {
        this(target, Duration.millis(1200));
    }

    StatusAlertAnimator(Node target, Duration cycleDuration) {
        this.target = target;
        this.timeline = new Timeline(
                new KeyFrame(Duration.ZERO, event -> target.setOpacity(1.0)),
                new KeyFrame(cycleDuration.divide(2), event -> target.setOpacity(0.45)),
                new KeyFrame(cycleDuration, event -> target.setOpacity(1.0))
        );
        timeline.setCycleCount(ATTENTION_CYCLES);
        timeline.setOnFinished(event -> target.setOpacity(1.0));
    }

    public void play() {
        if (timeline.getStatus() == Animation.Status.RUNNING) return;
        timeline.playFromStart();
    }

    public void stop() {
        timeline.stop();
        target.setOpacity(1.0);
    }

    boolean isRunning() {
        return timeline.getStatus() == Animation.Status.RUNNING;
    }

    int cycleCount() {
        return timeline.getCycleCount();
    }
}
