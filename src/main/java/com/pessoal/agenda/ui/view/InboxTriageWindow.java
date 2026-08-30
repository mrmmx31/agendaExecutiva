package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.InboxCapture;
import com.pessoal.agenda.service.InboxCaptureService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class InboxTriageWindow {
    private static final int LOAD_LIMIT = 200;
    private static final DateTimeFormatter CREATED_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final InboxCaptureService service;
    private final Runnable inboxChanged;
    private final ObservableList<InboxCapture> items = FXCollections.observableArrayList();

    private Stage stage;
    private ListView<InboxCapture> listView;
    private TextArea detailText;
    private DatePicker taskDate;
    private Label countLabel;
    private Label statusLabel;
    private Button taskButton;
    private Button ideaButton;
    private Button interruptionButton;
    private Button archiveButton;
    private Button retryButton;

    public InboxTriageWindow(InboxCaptureService service, Runnable inboxChanged) {
        this.service = Objects.requireNonNull(service);
        this.inboxChanged = Objects.requireNonNull(inboxChanged);
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            loadCaptures();
            stage.toFront();
            stage.requestFocus();
            return;
        }

        stage = WindowManager.createModelessStage();
        stage.setTitle("Caixa de entrada");
        stage.setMinWidth(680);
        stage.setMinHeight(440);
        stage.setScene(new Scene(buildContent(), 880, 560));
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stage = null);
        loadCaptures();
        WindowManager.show(stage);
    }

    private BorderPane buildContent() {
        Label title = new Label("Caixa de entrada");
        title.getStyleClass().add("quick-capture-title");
        countLabel = new Label();
        countLabel.getStyleClass().add("t-muted-md");
        VBox header = new VBox(3, title, countLabel);
        header.setPadding(new Insets(18, 18, 10, 18));

        listView = new ListView<>(items);
        listView.setId("inbox-triage-list");
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setPlaceholder(new Label("Nenhuma captura aguardando triagem."));
        listView.setCellFactory(ignored -> new CaptureCell());
        listView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldItem, newItem) -> showSelection(newItem));

        Label detailTitle = new Label("Conteúdo capturado");
        detailTitle.getStyleClass().add("t-heading");
        detailText = new TextArea();
        detailText.setId("inbox-triage-detail");
        detailText.setEditable(false);
        detailText.setWrapText(true);
        VBox.setVgrow(detailText, Priority.ALWAYS);

        Label dateLabel = new Label("Data da tarefa");
        taskDate = new DatePicker(LocalDate.now());
        taskDate.setId("inbox-triage-task-date");
        HBox taskDateRow = new HBox(8, dateLabel, taskDate);
        taskDateRow.setAlignment(Pos.CENTER_LEFT);

        taskButton = actionButton("Criar tarefa", "inbox-triage-task", "primary-button",
                () -> triage(TriageAction.TASK));
        ideaButton = actionButton("Criar ideia", "inbox-triage-idea", "secondary-button",
                () -> triage(TriageAction.IDEA));
        interruptionButton = actionButton(
                "Nota de interrupção", "inbox-triage-interruption", "secondary-button",
                () -> triage(TriageAction.INTERRUPTION));
        interruptionButton.setTooltip(new Tooltip("Guardar como contexto para retomada"));
        archiveButton = actionButton("Arquivar", "inbox-triage-archive", "secondary-button",
                () -> triage(TriageAction.ARCHIVE));
        FlowPane actions = new FlowPane(8, 8,
                taskButton, ideaButton, interruptionButton, archiveButton);
        actions.setPrefWrapLength(430);

        statusLabel = new Label();
        statusLabel.setId("inbox-triage-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        setVisibleManaged(statusLabel, false);

        VBox details = new VBox(10, detailTitle, detailText, taskDateRow, actions, statusLabel);
        details.setPadding(new Insets(12));
        details.getStyleClass().add("inbox-triage-detail-pane");

        VBox listPane = new VBox(8, new Label("Não classificadas"), listView);
        listPane.setPadding(new Insets(12));
        VBox.setVgrow(listView, Priority.ALWAYS);

        SplitPane split = new SplitPane(listPane, details);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.38);

        retryButton = new Button("Tentar novamente");
        retryButton.setId("inbox-triage-retry");
        retryButton.getStyleClass().add("secondary-button");
        retryButton.setOnAction(event -> loadCaptures());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, retryButton, spacer);
        footer.setPadding(new Insets(8, 18, 12, 18));
        setVisibleManaged(retryButton, false);

        BorderPane root = new BorderPane(split);
        root.setTop(header);
        root.setBottom(footer);
        root.getStyleClass().addAll("app-root", "inbox-triage-root");
        return root;
    }

    private Button actionButton(String text, String id, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    private boolean loadCaptures() {
        int selectedIndex = listView.getSelectionModel().getSelectedIndex();
        try {
            List<InboxCapture> loaded = service.listUnclassified(LOAD_LIMIT);
            items.setAll(loaded);
            countLabel.setText(loaded.isEmpty()
                    ? "Nada aguardando organização."
                    : loaded.size() + (loaded.size() == 1 ? " captura pendente" : " capturas pendentes"));
            clearStatus();
            setVisibleManaged(retryButton, false);
            if (!loaded.isEmpty()) {
                listView.getSelectionModel().select(Math.min(Math.max(selectedIndex, 0), loaded.size() - 1));
            } else {
                showSelection(null);
            }
            return true;
        } catch (RuntimeException error) {
            items.clear();
            countLabel.setText("Não foi possível carregar as capturas.");
            showStatus("Falha ao consultar a caixa de entrada. Tente novamente.", "t-danger");
            setVisibleManaged(retryButton, true);
            showSelection(null);
            return false;
        }
    }

    private void showSelection(InboxCapture capture) {
        detailText.setText(capture != null ? capture.rawText() : "");
        boolean disabled = capture == null;
        taskDate.setDisable(disabled);
        taskButton.setDisable(disabled);
        ideaButton.setDisable(disabled);
        interruptionButton.setDisable(disabled);
        archiveButton.setDisable(disabled);
    }

    private void triage(TriageAction action) {
        InboxCapture selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (action == TriageAction.TASK && taskDate.getValue() == null) {
            showStatus("Escolha a data da tarefa.", "t-danger");
            taskDate.requestFocus();
            return;
        }

        setActionsDisabled(true);
        String success;
        try {
            success = switch (action) {
                case TASK -> {
                    service.triageAsTask(selected.id(), taskDate.getValue());
                    yield "Tarefa criada.";
                }
                case IDEA -> {
                    service.triageAsIdea(selected.id());
                    yield "Ideia criada e disponível em Revisar ideias.";
                }
                case INTERRUPTION -> {
                    service.triageAsInterruptionNote(selected.id());
                    yield "Nota de interrupção guardada.";
                }
                case ARCHIVE -> {
                    service.archive(selected.id());
                    yield "Captura arquivada.";
                }
            };
        } catch (RuntimeException error) {
            showStatus("Não foi possível concluir a triagem. A captura continua pendente.", "t-danger");
            setActionsDisabled(false);
            return;
        }

        try {
            inboxChanged.run();
        } catch (RuntimeException ignored) {
            // A transação já terminou; a lista local ainda será recarregada abaixo.
        }
        if (loadCaptures()) {
            showStatus(success, "t-success");
        } else {
            showStatus("Triagem concluída, mas a lista não pôde ser atualizada.", "t-danger");
        }
    }

    private void setActionsDisabled(boolean disabled) {
        boolean noSelection = listView.getSelectionModel().getSelectedItem() == null;
        taskButton.setDisable(disabled || noSelection);
        ideaButton.setDisable(disabled || noSelection);
        interruptionButton.setDisable(disabled || noSelection);
        archiveButton.setDisable(disabled || noSelection);
    }

    private void showStatus(String text, String styleClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("t-danger", "t-success");
        statusLabel.getStyleClass().add(styleClass);
        setVisibleManaged(statusLabel, true);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("t-danger", "t-success");
        setVisibleManaged(statusLabel, false);
    }

    private static void setVisibleManaged(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    private static String summary(InboxCapture capture) {
        return capture.rawText().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst().orElse("Captura sem texto");
    }

    private static final class CaptureCell extends ListCell<InboxCapture> {
        @Override
        protected void updateItem(InboxCapture capture, boolean empty) {
            super.updateItem(capture, empty);
            if (empty || capture == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(summary(capture));
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            title.getStyleClass().add("t-heading-sm");
            Label created = new Label(CREATED_FORMAT.format(
                    capture.createdAt().atZone(ZoneId.systemDefault())));
            created.getStyleClass().add("t-muted");
            VBox graphic = new VBox(3, title, created);
            graphic.setMaxWidth(Double.MAX_VALUE);
            setText(null);
            setGraphic(graphic);
        }
    }

    private enum TriageAction {
        TASK,
        IDEA,
        INTERRUPTION,
        ARCHIVE
    }
}
