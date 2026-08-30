package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

final class FocusInterruptionDialog {
    private FocusInterruptionDialog() {}

    static boolean show(Task task, String currentNote, Consumer<String> saveAction) {
        Dialog<ButtonType> dialog = create(task, currentNote, saveAction);
        return dialog.showAndWait()
                .filter(result -> result.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .isPresent();
    }

    static Dialog<ButtonType> create(Task task, String currentNote,
                                     Consumer<String> saveAction) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(saveAction);

        Dialog<ButtonType> dialog = Dialogs.prepare(new Dialog<>());
        dialog.setTitle("Registrar interrupção");
        dialog.setHeaderText("Onde você parou em “" + task.title() + "”?");

        ButtonType saveType = new ButtonType("Guardar pista", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(440);
        dialog.getDialogPane().setPrefWidth(520);

        Label instruction = new Label("Registre somente o próximo passo concreto.");
        instruction.setWrapText(true);
        TextArea note = new TextArea(currentNote != null ? currentNote : "");
        note.setId("focus-interruption-note");
        note.setPromptText("Ex.: validar o retorno da API sem CNPJ");
        note.setWrapText(true);
        note.setPrefRowCount(4);

        Label status = new Label();
        status.setId("focus-interruption-status");
        status.setWrapText(true);
        status.setVisible(false);
        status.setManaged(false);

        VBox content = new VBox(8, instruction, note, status);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);

        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.setId("focus-interruption-save");
        save.addEventFilter(ActionEvent.ACTION, event -> {
            String text = note.getText();
            if (text == null || text.isBlank()) {
                showError(status, "Escreva onde parou ou qual é o próximo passo.");
                event.consume();
                note.requestFocus();
                return;
            }
            try {
                saveAction.accept(text);
            } catch (RuntimeException error) {
                showError(status,
                        "Não foi possível guardar a pista. Seu texto continua aqui.");
                save.setText("Tentar novamente");
                event.consume();
                note.requestFocus();
            }
        });
        dialog.setOnShown(event -> note.requestFocus());
        return dialog;
    }

    private static void showError(Label status, String message) {
        status.setText(message);
        status.getStyleClass().removeAll("t-danger", "t-success");
        status.getStyleClass().add("t-danger");
        status.setVisible(true);
        status.setManaged(true);
    }
}
