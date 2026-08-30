package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.service.InboxCaptureService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.IntConsumer;

public class QuickCaptureWindow {
    private static final Duration SUCCESS_VISIBLE_DURATION = Duration.millis(700);

    private final CaptureAction captureAction;
    private final IntConsumer captureSavedAction;
    private Stage stage;
    private TextArea textArea;
    private Label statusLabel;
    private Button saveButton;
    private VBox discardConfirmation;
    private boolean closingProgrammatically;
    private boolean saved;
    private int saveAttempts;

    public QuickCaptureWindow(InboxCaptureService service) {
        this(service, () -> {});
    }

    public QuickCaptureWindow(InboxCaptureService service, Runnable captureSavedAction) {
        this(service::capture, ignored -> captureSavedAction.run());
    }

    public QuickCaptureWindow(InboxCaptureService service, IntConsumer captureSavedAction) {
        this(service::capture, captureSavedAction);
    }

    QuickCaptureWindow(CaptureAction captureAction) {
        this(captureAction, () -> {});
    }

    QuickCaptureWindow(CaptureAction captureAction, Runnable captureSavedAction) {
        this(captureAction, ignored -> captureSavedAction.run());
    }

    QuickCaptureWindow(CaptureAction captureAction, IntConsumer captureSavedAction) {
        this.captureAction = Objects.requireNonNull(captureAction);
        this.captureSavedAction = Objects.requireNonNull(captureSavedAction);
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            textArea.requestFocus();
            return;
        }

        stage = WindowManager.createModelessStage();
        saved = false;
        saveAttempts = 0;
        stage.setTitle("Captura rápida");
        stage.setMinWidth(380);
        stage.setMinHeight(260);
        stage.setScene(new Scene(buildContent(), 500, 300));
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (!closingProgrammatically && hasUnsavedText()) {
                event.consume();
                showDiscardConfirmation();
            }
        });
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stage = null);

        WindowManager.show(stage);
        Platform.runLater(textArea::requestFocus);
    }

    private VBox buildContent() {
        Label title = new Label("Capturar pensamento");
        title.getStyleClass().add("quick-capture-title");

        Label prompt = new Label("O que precisa sair da sua cabeça agora?");
        prompt.getStyleClass().add("t-muted-md");

        textArea = new TextArea();
        textArea.setId("quick-capture-text");
        textArea.setPromptText("Escreva sem organizar");
        textArea.setWrapText(true);
        textArea.setPrefRowCount(5);
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        textArea.textProperty().addListener((obs, oldText, newText) -> {
            hideDiscardConfirmation();
            if (!Objects.equals(oldText, newText)) clearStatus();
        });
        VBox.setVgrow(textArea, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.setId("quick-capture-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        setVisibleManaged(statusLabel, false);

        saveButton = new Button("Salvar");
        saveButton.setId("quick-capture-save");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> save());

        Button cancelButton = new Button("Cancelar");
        cancelButton.setId("quick-capture-cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> requestClose());

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actions = new HBox(8, statusLabel, actionSpacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        discardConfirmation = buildDiscardConfirmation();

        VBox root = new VBox(10, title, prompt, textArea, discardConfirmation, actions);
        root.setPadding(new Insets(18));
        root.getStyleClass().addAll("app-root", "quick-capture-root");
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                requestClose();
            }
        });
        return root;
    }

    private VBox buildDiscardConfirmation() {
        Label question = new Label("Descartar o texto digitado?");
        question.setWrapText(true);
        question.getStyleClass().add("t-heading-sm");

        Button keepButton = new Button("Continuar escrevendo");
        keepButton.setId("quick-capture-keep");
        keepButton.getStyleClass().add("secondary-button");
        keepButton.setOnAction(event -> {
            hideDiscardConfirmation();
            textArea.requestFocus();
        });

        Button discardButton = new Button("Descartar");
        discardButton.setId("quick-capture-discard");
        discardButton.getStyleClass().add("danger-button");
        discardButton.setOnAction(event -> closeNow());

        HBox confirmationActions = new HBox(8, keepButton, discardButton);
        confirmationActions.setAlignment(Pos.CENTER_RIGHT);
        VBox confirmation = new VBox(8, question, confirmationActions);
        confirmation.setId("quick-capture-discard-confirmation");
        confirmation.getStyleClass().add("quick-capture-confirmation");
        setVisibleManaged(confirmation, false);
        return confirmation;
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            save();
        }
    }

    private void save() {
        String rawText = textArea.getText();
        if (rawText == null || rawText.isBlank()) {
            showStatus("Escreva algo antes de salvar.", "t-danger");
            textArea.requestFocus();
            return;
        }

        saveButton.setDisable(true);
        saveAttempts++;
        try {
            captureAction.capture(rawText);
            saved = true;
            try {
                captureSavedAction.accept(saveAttempts);
            } catch (RuntimeException ignored) {
                // A captura já foi persistida; falha de refresh não pode sugerir perda do texto.
            }
            textArea.setDisable(true);
            showStatus("Salvo na caixa de entrada.", "t-success");
            PauseTransition closeDelay = new PauseTransition(SUCCESS_VISIBLE_DURATION);
            closeDelay.setOnFinished(event -> closeNow());
            closeDelay.play();
        } catch (RuntimeException error) {
            saveButton.setDisable(false);
            saveButton.setText("Tentar novamente");
            showStatus("Não foi possível salvar. Seu texto continua aqui.", "t-danger");
            textArea.requestFocus();
        }
    }

    private void requestClose() {
        if (hasUnsavedText()) {
            showDiscardConfirmation();
        } else {
            closeNow();
        }
    }

    private boolean hasUnsavedText() {
        return !saved && textArea != null && !textArea.getText().isBlank();
    }

    private void showDiscardConfirmation() {
        setVisibleManaged(discardConfirmation, true);
        clearStatus();
    }

    private void hideDiscardConfirmation() {
        if (discardConfirmation != null) setVisibleManaged(discardConfirmation, false);
    }

    private void showStatus(String text, String styleClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("t-danger", "t-success");
        statusLabel.getStyleClass().add(styleClass);
        setVisibleManaged(statusLabel, true);
    }

    private void clearStatus() {
        if (statusLabel == null) return;
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("t-danger", "t-success");
        setVisibleManaged(statusLabel, false);
        if (saveButton != null && !textArea.isDisabled()) saveButton.setText("Salvar");
    }

    private void closeNow() {
        if (stage == null) return;
        closingProgrammatically = true;
        stage.close();
        closingProgrammatically = false;
    }

    private static void setVisibleManaged(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    @FunctionalInterface
    interface CaptureAction {
        void capture(String rawText);
    }
}
