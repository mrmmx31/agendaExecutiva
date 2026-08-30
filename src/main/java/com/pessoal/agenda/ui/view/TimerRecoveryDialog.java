package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.service.TaskTimerRecoveryService;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimerRecoveryDialog {
    public enum Decision { RECOVER, DISCARD }

    private static final DateTimeFormatter CHECKPOINT_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private TimerRecoveryDialog() {}

    public static Decision show(TaskTimerRecoveryService.Candidate candidate) {
        return create(candidate).showAndWait().orElseThrow();
    }

    static Dialog<Decision> create(TaskTimerRecoveryService.Candidate candidate) {
        Dialog<Decision> dialog = Dialogs.prepare(new Dialog<>());
        dialog.setTitle("Recuperar timer");
        dialog.setHeaderText("Foi encontrado um timer anterior");
        dialog.getDialogPane().setMinWidth(460);
        dialog.getDialogPane().setPrefWidth(540);

        ButtonType recover = new ButtonType(
                "Recuperar pausado", ButtonBar.ButtonData.OK_DONE);
        ButtonType discard = new ButtonType(
                "Descartar intervalo", ButtonBar.ButtonData.NO);
        dialog.getDialogPane().getButtonTypes().addAll(recover, discard);
        dialog.getDialogPane().lookupButton(recover).setId("timer-recovery-recover");
        dialog.getDialogPane().lookupButton(discard).setId("timer-recovery-discard");

        Label taskTitle = new Label(candidate.task().title());
        taskTitle.setId("timer-recovery-task");
        taskTitle.getStyleClass().add("section-title");
        taskTitle.setWrapText(true);

        Label elapsed = new Label("Tempo registrado: "
                + formatDuration(candidate.recovery().elapsedSeconds()));
        elapsed.setId("timer-recovery-elapsed");
        elapsed.getStyleClass().add("t-heading-sm");

        String state = candidate.recovery().wasRunning()
                ? "O contador estava rodando."
                : "O contador estava pausado.";
        String checkpoint = CHECKPOINT_TIME.format(
                candidate.recovery().updatedAt().atZone(ZoneId.systemDefault()));
        Label detail = new Label(state + " Último checkpoint: " + checkpoint + ".");
        detail.setId("timer-recovery-detail");
        detail.setWrapText(true);

        Label safety = new Label(
                "A recuperação usa somente o tempo acima. O período em que a aplicação ficou fechada não será somado. O timer voltará pausado.");
        safety.setId("timer-recovery-safety");
        safety.getStyleClass().add("t-muted");
        safety.setWrapText(true);

        VBox content = new VBox(9, taskTitle, elapsed, detail, safety);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == recover
                ? Decision.RECOVER : Decision.DISCARD);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) event.consume();
        });
        dialog.setOnShown(event -> {
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,
                    closeEvent -> closeEvent.consume());
        });
        return dialog;
    }

    static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }
}
