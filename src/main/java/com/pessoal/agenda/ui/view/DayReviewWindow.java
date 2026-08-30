package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.DayReviewSummary;
import com.pessoal.agenda.model.DayReviewDecision;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskSession;
import com.pessoal.agenda.service.DayReviewService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DayReviewWindow {
    private static final DateTimeFormatter CLOSED_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final DayReviewService service;
    private final LocalDate date;
    private final Runnable changedCallback;

    private Stage stage;
    private Label summaryLabel;
    private Label closedLabel;
    private ListView<Task> completedList;
    private ListView<TaskSession> sessionList;
    private VBox openDecisionBox;
    private ComboBox<Task> tomorrowInitial;
    private Button clearTomorrowButton;
    private final Map<Long, ComboBox<DayReviewDecision>> decisionEditors = new LinkedHashMap<>();
    private final Map<Long, Task> openTasksById = new LinkedHashMap<>();
    private TextArea closingNote;
    private Label statusLabel;
    private Button closeDayButton;
    private Button reopenButton;
    private Button retryButton;

    public DayReviewWindow(DayReviewService service, LocalDate date,
                           Runnable changedCallback) {
        this.service = Objects.requireNonNull(service);
        this.date = Objects.requireNonNull(date);
        this.changedCallback = Objects.requireNonNull(changedCallback);
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            loadSummary();
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = WindowManager.createModalStage();
        stage.setTitle("Encerrar meu dia");
        stage.setMinWidth(640);
        stage.setMinHeight(500);
        stage.setScene(new Scene(buildContent(), 760, 650));
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stage = null);
        loadSummary();
        WindowManager.show(stage);
    }

    private BorderPane buildContent() {
        Label title = new Label("Encerrar meu dia");
        title.getStyleClass().add("page-title");
        Label intro = new Label(
                "Veja o que foi concluído, o tempo registrado e o que permaneceu aberto.");
        intro.getStyleClass().add("t-muted");
        intro.setWrapText(true);
        summaryLabel = new Label();
        summaryLabel.setId("day-review-summary");
        summaryLabel.getStyleClass().add("t-heading-sm");
        summaryLabel.setWrapText(true);
        closedLabel = new Label();
        closedLabel.setId("day-review-closed-state");
        closedLabel.getStyleClass().add("focus-now-mode");
        closedLabel.setWrapText(true);

        completedList = taskList("Nenhuma tarefa do plano foi concluída.");
        completedList.setId("day-review-completed");
        sessionList = new ListView<>();
        sessionList.setId("day-review-sessions");
        configureList(sessionList, "Nenhuma sessão foi registrada hoje.");
        sessionList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(TaskSession session, boolean empty) {
                super.updateItem(session, empty);
                setText(empty || session == null ? null
                        : session.subject() + " · " + session.durationMinutes() + " min");
            }
        });
        openDecisionBox = new VBox(8);
        openDecisionBox.setId("day-review-open-decisions");
        tomorrowInitial = new ComboBox<>();
        tomorrowInitial.setId("day-review-tomorrow-initial");
        tomorrowInitial.setPromptText("Sem tarefa inicial definida");
        tomorrowInitial.setMaxWidth(Double.MAX_VALUE);
        configureTaskCombo(tomorrowInitial);
        clearTomorrowButton = actionButton(
                "×", "day-review-clear-tomorrow", "secondary-button",
                () -> tomorrowInitial.getSelectionModel().clearSelection());
        clearTomorrowButton.setTooltip(new javafx.scene.control.Tooltip(
                "Remover a tarefa inicial de amanhã"));
        HBox tomorrowRow = new HBox(8, tomorrowInitial, clearTomorrowButton);
        HBox.setHgrow(tomorrowInitial, Priority.ALWAYS);

        Label noteLabel = new Label("Nota opcional do encerramento");
        noteLabel.getStyleClass().add("form-label");
        closingNote = new TextArea();
        closingNote.setId("day-review-note");
        closingNote.setPromptText("Ex.: avancei no essencial; amanhã começo pela revisão final.");
        closingNote.setWrapText(true);
        closingNote.setPrefRowCount(3);

        statusLabel = new Label();
        statusLabel.setId("day-review-status");
        statusLabel.setWrapText(true);
        setVisibleManaged(statusLabel, false);

        VBox content = new VBox(10, title, intro, summaryLabel, closedLabel,
                section("Concluídas", completedList),
                section("Sessões registradas", sessionList),
                section("Decidir itens ainda abertos", openDecisionBox),
                section("Primeira tarefa de amanhã (opcional)", tomorrowRow),
                noteLabel, closingNote, statusLabel);
        content.setPadding(new Insets(18));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");

        closeDayButton = actionButton(
                "Encerrar dia", "day-review-close-day", "primary-button", this::closeDay);
        reopenButton = actionButton(
                "Reabrir dia", "day-review-reopen", "secondary-button", this::reopenDay);
        retryButton = actionButton(
                "Tentar novamente", "day-review-retry", "secondary-button", this::loadSummary);
        Button closeWindow = actionButton(
                "Fechar", "day-review-close-window", "secondary-button", () -> stage.close());
        FlowPane actions = new FlowPane(8, 8,
                closeDayButton, reopenButton, retryButton, closeWindow);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 18, 14, 18));

        BorderPane root = new BorderPane(scroll);
        root.setBottom(actions);
        root.getStyleClass().addAll("app-root", "day-review-root");
        return root;
    }

    private void loadSummary() {
        try {
            var summary = service.summary(date);
            if (summary.isEmpty()) {
                showError("O plano deste dia não está mais disponível.");
                return;
            }
            showSummary(summary.get());
            clearStatus();
        } catch (RuntimeException error) {
            showError("Não foi possível carregar a revisão. Tente novamente.");
        }
    }

    private void showSummary(DayReviewSummary summary) {
        completedList.getItems().setAll(summary.completedTasks());
        sessionList.getItems().setAll(summary.sessions());
        summaryLabel.setText("Concluídas: " + summary.completedTasks().size()
                + " · Sessões: " + summary.sessions().size()
                + " · Em aberto: " + summary.openTasks().size());
        boolean closed = summary.plan().closedAt() != null;
        showOpenDecisions(summary.openTasks(), closed);
        closedLabel.setText(closed
                ? "Dia encerrado em " + CLOSED_TIME.format(
                        summary.plan().closedAt().atZone(ZoneId.systemDefault()))
                : "Dia ainda aberto");
        closingNote.setText(summary.plan().closingNote() != null
                ? summary.plan().closingNote() : "");
        closingNote.setEditable(!closed);
        setVisibleManaged(closeDayButton, !closed);
        setVisibleManaged(reopenButton, closed);
        setVisibleManaged(retryButton, false);
    }

    private void closeDay() {
        try {
            Map<Long, DayReviewDecision> decisions = new LinkedHashMap<>();
            decisionEditors.forEach((taskId, editor) -> decisions.put(taskId, editor.getValue()));
            Task firstTomorrowTask = tomorrowInitial.getValue();
            DayReviewSummary closed = service.closeDay(
                    date, closingNote.getText(), decisions,
                    firstTomorrowTask != null ? firstTomorrowTask.id() : null);
            showSummary(closed);
            showStatus("Dia encerrado. Você pode reabri-lo hoje se precisar.", "t-success");
            changedCallback.run();
        } catch (RuntimeException error) {
            if (error instanceof IllegalStateException
                    && error.getMessage() != null
                    && error.getMessage().startsWith("Amanhã já")) {
                showStatus(error.getMessage()
                        + ". Remova a tarefa inicial de amanhã e tente novamente.", "t-danger");
            } else {
                showStatus("Não foi possível encerrar. A nota e as escolhas foram preservadas.",
                        "t-danger");
            }
        }
    }

    private void reopenDay() {
        try {
            showSummary(service.reopenDay(date));
            showStatus("Dia reaberto sem alterar tarefas ou sessões.", "t-success");
            changedCallback.run();
        } catch (RuntimeException error) {
            showStatus("Não foi possível reabrir o dia. Tente novamente.", "t-danger");
        }
    }

    private void showError(String message) {
        showStatus(message, "t-danger");
        setVisibleManaged(closeDayButton, false);
        setVisibleManaged(reopenButton, false);
        setVisibleManaged(retryButton, true);
    }

    private void showStatus(String message, String styleClass) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("t-danger", "t-success");
        statusLabel.getStyleClass().add(styleClass);
        setVisibleManaged(statusLabel, true);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("t-danger", "t-success");
        setVisibleManaged(statusLabel, false);
    }

    private static ListView<Task> taskList(String placeholder) {
        ListView<Task> list = new ListView<>();
        configureList(list, placeholder);
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                setText(empty || task == null ? null : task.title());
            }
        });
        return list;
    }

    private void showOpenDecisions(List<Task> tasks, boolean closed) {
        Map<Long, DayReviewDecision> previous = new LinkedHashMap<>();
        decisionEditors.forEach((id, editor) -> previous.put(id, editor.getValue()));
        decisionEditors.clear();
        openTasksById.clear();
        tasks.forEach(task -> openTasksById.put(task.id(), task));
        openDecisionBox.getChildren().clear();

        if (tasks.isEmpty()) {
            Label empty = new Label("Nenhum item do plano ficou aberto.");
            empty.getStyleClass().add("t-muted");
            openDecisionBox.getChildren().add(empty);
        } else if (closed) {
            tasks.stream().map(Task::title).map(Label::new)
                    .forEach(openDecisionBox.getChildren()::add);
        } else {
            for (Task task : tasks) {
                Label title = new Label(task.title());
                title.setWrapText(true);
                title.getStyleClass().add("form-label");
                ComboBox<DayReviewDecision> editor = new ComboBox<>();
                editor.getItems().setAll(DayReviewDecision.values());
                editor.setValue(previous.getOrDefault(task.id(), DayReviewDecision.KEEP_DATE));
                editor.setId("day-review-decision-" + task.id());
                editor.getStyleClass().add("day-review-decision");
                editor.setMaxWidth(Double.MAX_VALUE);
                editor.setOnAction(event -> refreshTomorrowCandidates());
                decisionEditors.put(task.id(), editor);
                openDecisionBox.getChildren().add(new VBox(3, title, editor));
            }
        }

        if (closed) {
            tomorrowInitial.getItems().setAll(service.tomorrowInitialTask(date).stream().toList());
            tomorrowInitial.getSelectionModel().selectFirst();
            tomorrowInitial.setDisable(true);
            setVisibleManaged(clearTomorrowButton, false);
        } else {
            tomorrowInitial.setDisable(false);
            setVisibleManaged(clearTomorrowButton, true);
            refreshTomorrowCandidates();
        }
    }

    private void refreshTomorrowCandidates() {
        Task selected = tomorrowInitial.getValue();
        List<Task> candidates = decisionEditors.entrySet().stream()
                .filter(entry -> entry.getValue().getValue() == DayReviewDecision.TOMORROW)
                .map(entry -> openTasksById.get(entry.getKey()))
                .toList();
        tomorrowInitial.getItems().setAll(candidates);
        if (selected != null && candidates.stream().anyMatch(task -> task.id() == selected.id())) {
            tomorrowInitial.getSelectionModel().select(
                    candidates.stream().filter(task -> task.id() == selected.id()).findFirst().orElseThrow());
        } else {
            tomorrowInitial.getSelectionModel().clearSelection();
        }
    }

    private static void configureTaskCombo(ComboBox<Task> combo) {
        combo.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                setText(empty || task == null ? null : task.title());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                setText(empty || task == null ? null : task.title());
            }
        });
    }

    private static void configureList(ListView<?> list, String placeholder) {
        list.getStyleClass().add("clean-list");
        list.setPrefHeight(96);
        list.setMinHeight(72);
        list.setPlaceholder(new Label(placeholder));
    }

    private static VBox section(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("t-heading-sm");
        return new VBox(4, label, content);
    }

    private static Button actionButton(String text, String id, String styleClass,
                                       Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
