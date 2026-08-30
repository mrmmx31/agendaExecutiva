package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskSession;
import com.pessoal.agenda.repository.TaskSessionRepository;
import com.pessoal.agenda.service.FocusContextService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.time.LocalDate;
import java.util.List;

/** Robust timer window for task sessions with history and metadata. */

public class TaskTimerWindow {
    // Gerencia janelas abertas por taskId
    private static final java.util.Map<Long, Stage> openWindows = new java.util.HashMap<>();

    private final Task task;
    private final TaskSessionRepository repo;
    private final FocusContextService focusContextService;
    private Stage stage;

    // UI
    private Button playPauseBtn;
    private Button stopBtn;
    private Button resetBtn;
    private Button interruptBtn;
    private Button compactBtn;
    private Label timerLabel;
    private Label compactTimerLabel;
    private TextArea notesArea;
    private Label totalLabel;
    private ListView<TaskSession> historyList;
    private Stage compactStage;
    private ToggleButton compactPlayPauseBtn;
    private Button compactInterruptBtn;
    private boolean alwaysOnTopEnabled = false;
    private boolean transitioningToCompact = false;
    private boolean transitioningToMain = false;
    private boolean disposed = false;

    private final Runnable refreshCallback;

    // Para remover o tickListener ao fechar
    private Runnable tickUnsubscriber = null;
    // Tick listener para este timer
    private java.util.function.Consumer<Long> tickListener;

    public TaskTimerWindow(Task task, TaskSessionRepository repo, Runnable refreshCallback) {
        this(task, repo, refreshCallback, AppContextHolder.get().focusContextService());
    }

    TaskTimerWindow(Task task, TaskSessionRepository repo, Runnable refreshCallback,
                    FocusContextService focusContextService) {
        this.task = task;
        this.repo = repo;
        this.refreshCallback = refreshCallback;
        this.focusContextService = focusContextService;
        this.tickListener = sec -> {
            var timerService = com.pessoal.agenda.service.TaskTimerService.get();
            if (timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(task.id())) {
                javafx.application.Platform.runLater(() -> setTimerText(formatTimer(sec)));
            }
        };
    }
    public TaskTimerWindow(Task task, TaskSessionRepository repo) {
        this(task, repo, null);
    }

    public void show() {
        // ── Evita abrir duplicata para a mesma tarefa ──
        Stage existing = openWindows.get(task.id());
        if (existing != null) {
            existing.show();
            existing.toFront();
            existing.requestFocus();
            return;
        }

        stage = WindowManager.createModelessStage();
        openWindows.put(task.id(), stage);
        stage.setTitle("Timer — " + task.title());
        stage.setMinWidth(760);
        stage.setMinHeight(500);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        // ── Barra superior elegante ──
        Label title = new Label("Timer da tarefa");
        title.getStyleClass().add("page-title");
        ResponsiveWindowLayout.makeFlexible(title);
        Label typeBadge = new Label(task.priority() != null ? "  " + task.priority().label() + "  " : "  NORMAL  ");
        typeBadge.getStyleClass().addAll("study-badge", "badge-type");
        HBox headerBar = new HBox(10, title, typeBadge);
        headerBar.getStyleClass().add("header-bar");
        headerBar.setPadding(new Insets(16, 28, 16, 28));
        headerBar.setAlignment(Pos.CENTER_LEFT);

        // Espaço extra entre headerBar e main
        Region spacer = new Region();
        spacer.setMinHeight(10); // ajuste conforme necessário
        VBox topContainer = new VBox(headerBar, spacer);
        root.setTop(topContainer);

        // ── Painel central com controles e histórico ──
        HBox main = new HBox(24);
        main.setPadding(new Insets(0, 28, 18, 28));

        // Coluna esquerda: metadados, controles, notas
        VBox left = new VBox(16);
        left.setPrefWidth(420);
        left.setMinWidth(0);
        left.setMaxWidth(Double.MAX_VALUE);

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(8);
        metaGrid.setVgap(6);
        ColumnConstraints metaNameColumn = new ColumnConstraints();
        metaNameColumn.setMinWidth(82);
        ColumnConstraints metaValueColumn = new ColumnConstraints();
        metaValueColumn.setHgrow(Priority.ALWAYS);
        metaGrid.getColumnConstraints().addAll(metaNameColumn, metaValueColumn);
        metaGrid.add(new Label("Tarefa:"), 0, 0);
        Label taskTitleLbl = new Label(task.title());
        taskTitleLbl.getStyleClass().add("section-title");
        taskTitleLbl.setWrapText(true);
        taskTitleLbl.setMaxWidth(Double.MAX_VALUE);
        metaGrid.add(taskTitleLbl, 1, 0);
        metaGrid.add(new Label("Categoria:"), 0, 1);
        Label catLbl = new Label(task.category() == null ? "Geral" : task.category());
        catLbl.getStyleClass().add("study-plan-detail");
        metaGrid.add(catLbl, 1, 1);
        metaGrid.add(new Label("Vencimento:"), 0, 2);
        metaGrid.add(new Label(task.dueDate() != null ? task.dueDate().toString() : "—"), 1, 2);
        metaGrid.add(new Label("Status:"), 0, 3);
        metaGrid.add(new Label(task.status() != null ? task.status().label() : "PENDENTE"), 1, 3);

        // Timer controls
        timerLabel = new Label("00:00:00");
        timerLabel.getStyleClass().add("page-title");
        playPauseBtn = new Button("▶");
        playPauseBtn.getStyleClass().addAll("primary-button", "icon-button");
        playPauseBtn.setPrefWidth(56);
        stopBtn = new Button("■");
        stopBtn.getStyleClass().addAll("danger-button", "icon-button");
        stopBtn.setPrefWidth(56);
        resetBtn = new Button("⟲");
        resetBtn.getStyleClass().addAll("secondary-button", "icon-button");
        resetBtn.setPrefWidth(56);
        interruptBtn = new Button("Fui interrompido");
        interruptBtn.setId("task-timer-interrupt");
        interruptBtn.getStyleClass().add("secondary-button");
        interruptBtn.setTooltip(new Tooltip("Pausar e guardar onde você parou"));
        compactBtn = new Button("🗕");
        compactBtn.setId("task-timer-compact");
        compactBtn.getStyleClass().addAll("secondary-button", "icon-button");
        compactBtn.setPrefWidth(56);
        compactBtn.setTooltip(new Tooltip("Modo mini flutuante"));
        FlowPane controls = new FlowPane(8, 6,
                playPauseBtn, stopBtn, resetBtn, interruptBtn, compactBtn, timerLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        CheckBox pinTopCheck = new CheckBox("Sempre no topo");
        pinTopCheck.getStyleClass().add("form-label");
        pinTopCheck.setSelected(alwaysOnTopEnabled);
        pinTopCheck.setOnAction(e -> {
            alwaysOnTopEnabled = pinTopCheck.isSelected();
            applyAlwaysOnTop();
        });

        // Notes
        Label notesLbl = new Label("Observações da sessão:");
        notesLbl.getStyleClass().add("form-label");
        notesArea = new TextArea();
        notesArea.getStyleClass().add("input-control");
        notesArea.setPrefRowCount(4);

        left.getChildren().addAll(metaGrid, controls, pinTopCheck, notesLbl, notesArea);
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        // Coluna direita: histórico e total
        VBox right = new VBox(12);
        right.setPrefWidth(320);
        right.setMinWidth(0);
        right.setMaxWidth(Double.MAX_VALUE);
        Label histTitle = new Label("Histórico de sessões");
        histTitle.getStyleClass().add("section-title");
        historyList = new ListView<>();
        historyList.setPrefHeight(260);
        historyList.setCellFactory(lv -> new ListCell<>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(lv.widthProperty().subtract(24));
            }

            @Override
            protected void updateItem(TaskSession item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String notes = item.notes() == null ? "" : item.notes();
                setText(String.format("%s — %d min — %s", item.sessionDate(), item.durationMinutes(), notes));
            }
        });
        Button editTodayBtn = new Button("✎ Editar sessão de hoje");
        editTodayBtn.getStyleClass().add("secondary-button");
        editTodayBtn.setOnAction(e -> editSelectedTodaySession());
        totalLabel = new Label("Tempo total: 0 min");
        totalLabel.getStyleClass().add("section-title");
        right.getChildren().addAll(histTitle, historyList, editTodayBtn, totalLabel);
        VBox.setVgrow(historyList, Priority.ALWAYS);

        main.getChildren().addAll(left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        root.setCenter(main);

        // Handlers
        playPauseBtn.setOnAction(e -> toggle());
        stopBtn.setOnAction(e -> stopAndSave());
        resetBtn.setOnAction(e -> resetCounter());
        interruptBtn.setOnAction(e -> interruptFocus());
        compactBtn.setOnAction(e -> openCompactWindow());

        Scene sc = new Scene(root, 860, 540);
        ThemeManager.getInstance().applyTo(sc);
        stage.setScene(sc);
        stage.setAlwaysOnTop(alwaysOnTopEnabled);
        stage.setOnHiding(e -> {
            if (transitioningToCompact) {
                transitioningToCompact = false;
                return;
            }
            // Remove tickListener e referência da janela
            disposeWindowState();
        });

        // Atualiza o label do timer com o valor global
        updateTimerLabelFromService();
        // Listener para ticks globais (agora múltiplos listeners)
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.addTickListener(tickListener);
        tickUnsubscriber = () -> timerService.removeTickListener(tickListener);

        loadHistory();
        updateTotalLabel();
        WindowManager.show(stage);
    }

    private void openCompactWindow() {
        if (compactStage == null) {
            compactStage = buildCompactStage();
        }
        transitioningToCompact = true;
        stage.hide();
        WindowManager.show(compactStage);
        compactStage.toFront();
        applyAlwaysOnTop();
        syncPlayButtons();
    }

    private Stage buildCompactStage() {
        Stage mini = WindowManager.createModelessStage();
        mini.initStyle(StageStyle.UNDECORATED);
        mini.setTitle("Timer mini — " + task.title());

        Label miniTitle = new Label("⏱ " + task.title());
        miniTitle.getStyleClass().add("t-heading-sm");
        miniTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
        miniTitle.setMaxWidth(150);
        miniTitle.setTooltip(new Tooltip(task.title()));

        compactTimerLabel = new Label("00:00:00");
        compactTimerLabel.getStyleClass().add("page-title");

        compactPlayPauseBtn = new ToggleButton("▶");
        compactPlayPauseBtn.getStyleClass().add("primary-button");
        compactPlayPauseBtn.setPrefWidth(46);
        compactPlayPauseBtn.setOnAction(e -> toggle());

        Button miniStopBtn = new Button("■");
        miniStopBtn.getStyleClass().add("danger-button");
        miniStopBtn.setPrefWidth(46);
        miniStopBtn.setOnAction(e -> stopAndSave());

        compactInterruptBtn = new Button("↪");
        compactInterruptBtn.setId("compact-task-timer-interrupt");
        compactInterruptBtn.getStyleClass().addAll("secondary-button", "icon-button");
        compactInterruptBtn.setTooltip(new Tooltip("Fui interrompido"));
        compactInterruptBtn.setPrefWidth(46);
        compactInterruptBtn.setOnAction(e -> interruptFocus());

        Button expandBtn = new Button("⤢");
        expandBtn.getStyleClass().add("secondary-button");
        expandBtn.setTooltip(new Tooltip("Expandir timer"));
        expandBtn.setPrefWidth(46);
        expandBtn.setOnAction(e -> expandFromCompact());

        ToggleButton pinBtn = new ToggleButton("📌");
        pinBtn.getStyleClass().add("secondary-button");
        pinBtn.setTooltip(new Tooltip("Sempre no topo"));
        pinBtn.setPrefWidth(46);
        pinBtn.setSelected(alwaysOnTopEnabled);
        pinBtn.setOnAction(e -> {
            alwaysOnTopEnabled = pinBtn.isSelected();
            applyAlwaysOnTop();
        });

        HBox row1 = new HBox(6, compactPlayPauseBtn, miniStopBtn, compactInterruptBtn);
        row1.setAlignment(Pos.CENTER);
        HBox row2 = new HBox(6, pinBtn, expandBtn);
        row2.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(8, miniTitle, compactTimerLabel, row1, row2);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().addAll("section-card", "compact-timer-root");

        final double[] dragOffsetX = new double[1];
        final double[] dragOffsetY = new double[1];
        root.setOnMousePressed(e -> {
            dragOffsetX[0] = e.getSceneX();
            dragOffsetY[0] = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            mini.setX(e.getScreenX() - dragOffsetX[0]);
            mini.setY(e.getScreenY() - dragOffsetY[0]);
        });

        Scene miniScene = new Scene(root, 170, 165);
        ThemeManager.getInstance().applyTo(miniScene);
        mini.setScene(miniScene);
        WindowManager.preservePlacement(mini);
        mini.setAlwaysOnTop(alwaysOnTopEnabled);
        mini.setOnHiding(e -> {
            if (transitioningToMain) {
                transitioningToMain = false;
                return;
            }
            disposeWindowState();
            if (stage != null) stage.close();
        });
        return mini;
    }

    private void expandFromCompact() {
        if (compactStage == null || stage == null) return;
        transitioningToMain = true;
        compactStage.hide();
        WindowManager.show(stage);
        stage.toFront();
        stage.requestFocus();
        applyAlwaysOnTop();
        syncPlayButtons();
    }

    private synchronized void toggle() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        if (!timerService.isRunning() || timerService.getActiveTaskId() == null || !timerService.getActiveTaskId().equals(task.id())) {
            startTimer();
        } else {
            pauseTimer();
        }
    }

    private synchronized void startTimer() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.start(task.id());
        syncPlayButtons();
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private synchronized void pauseTimer() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.pause();
        syncPlayButtons();
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private synchronized void interruptFocus() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        boolean activeHere = java.util.Objects.equals(task.id(), timerService.getActiveTaskId());
        if (!activeHere) return;

        boolean resumeIfCancelled = timerService.isRunning();
        if (resumeIfCancelled) timerService.pause();
        syncPlayButtons();
        notifyRefresh();

        String currentNote = currentResumeNote();
        boolean saved = FocusInterruptionDialog.show(task, currentNote,
                note -> focusContextService.interrupt(task.id(), note));

        if (!saved && resumeIfCancelled
                && java.util.Objects.equals(task.id(), timerService.getActiveTaskId())
                && !timerService.isRunning()) {
            timerService.resume();
        }
        syncPlayButtons();
        notifyRefresh();
    }

    private String currentResumeNote() {
        try {
            return focusContextService.current()
                    .filter(context -> context.taskId() == task.id())
                    .map(context -> context.resumeNote())
                    .orElse("");
        } catch (RuntimeException error) {
            return "";
        }
    }

    // Não há mais tick local

    private synchronized void stopAndSave() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        long elapsedSeconds = timerService.getElapsedSeconds();
        timerService.stop();
        syncPlayButtons();
        if (refreshCallback != null) Platform.runLater(refreshCallback);
        showSaveSessionDialog(task, repo, elapsedSeconds, notesArea.getText(), () -> {
            Platform.runLater(() -> setTimerText("00:00:00"));
            notesArea.clear();
            loadHistory(); updateTotalLabel();
        });
    }

    /** Utilitário para exibir o diálogo de salvar sessão, reutilizável pela lista principal. */
    public static void showSaveSessionDialog(Task task, TaskSessionRepository repo, String notesText, Runnable onSave) {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        showSaveSessionDialog(task, repo, timerService.getElapsedSeconds(), notesText, onSave);
    }

    /**
     * Exibe o diálogo de salvar sessão com um tempo padrão vindo do contador.
     * O usuário pode ajustar manualmente antes de salvar.
     */
    public static void showSaveSessionDialog(Task task, TaskSessionRepository repo, long elapsedSeconds, String notesText, Runnable onSave) {
        long s = Math.max(0, elapsedSeconds);
        int minutes = s <= 0 ? 0 : (int) Math.ceil(s / 60.0);
        Dialog<ButtonType> dlg = new Dialog<>();
        WindowManager.prepare(dlg);
        dlg.setTitle("Salvar sessão"); dlg.setHeaderText("Salvar sessão de trabalho para a tarefa?");
        ButtonType saveBtn = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        TextField titleField = new TextField("Tarefa:#" + task.id() + " — " + task.title());
        Spinner<Integer> minutesSpinner = new Spinner<>(0, 24*60, minutes);
        TextArea notes = new TextArea(notesText != null ? notesText : ""); notes.setPrefRowCount(4);
        VBox content = new VBox(8, new Label("Título:"), titleField, new Label("Duração (min):"), minutesSpinner, new Label("Observações:"), notes);
        content.setPadding(new Insets(8));
        dlg.getDialogPane().setContent(content);
        dlg.setResultConverter(bt -> bt == saveBtn ? saveBtn : null);
        dlg.showAndWait().ifPresent(res -> {
            if (res == saveBtn) {
                int m = minutesSpinner.getValue();
                String subj = titleField.getText();
                String noteTxt = notes.getText();
                repo.save(task.id(), subj, LocalDate.now(), m, noteTxt);
                if (onSave != null) onSave.run();
            }
        });
    }

    private void resetCounter() {
        pauseTimer();
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.reset();
        Platform.runLater(() -> setTimerText("00:00:00"));
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private void updateTimerLabelFromService() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        if (timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(task.id())) {
            setTimerText(formatTimer(timerService.getElapsedSeconds()));
        } else {
            setTimerText("00:00:00");
        }
        syncPlayButtons();
    }

    private static String formatTimer(long s) {
        long hh = s / 3600;
        long mm = (s % 3600) / 60;
        long ss = s % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private void setTimerText(String text) {
        if (timerLabel != null) timerLabel.setText(text);
        if (compactTimerLabel != null) compactTimerLabel.setText(text);
    }

    private void syncPlayButtons() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        boolean activeHere = timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(task.id());
        boolean runningHere = activeHere && timerService.isRunning();
        if (playPauseBtn != null) playPauseBtn.setText(runningHere ? "⏸" : "▶");
        if (interruptBtn != null) interruptBtn.setDisable(!activeHere);
        if (compactPlayPauseBtn != null) {
            compactPlayPauseBtn.setSelected(runningHere);
            compactPlayPauseBtn.setText(runningHere ? "⏸" : "▶");
        }
        if (compactInterruptBtn != null) compactInterruptBtn.setDisable(!activeHere);
    }

    private void notifyRefresh() {
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private void applyAlwaysOnTop() {
        if (stage != null) stage.setAlwaysOnTop(alwaysOnTopEnabled);
        if (compactStage != null) compactStage.setAlwaysOnTop(alwaysOnTopEnabled);
    }

    private void disposeWindowState() {
        if (disposed) return;
        disposed = true;
        if (tickUnsubscriber != null) {
            tickUnsubscriber.run();
            tickUnsubscriber = null;
        }
        openWindows.remove(task.id());
        if (compactStage != null) {
            if (compactStage.isShowing()) {
                compactStage.hide();
            }
            compactStage = null;
        }
        if (refreshCallback != null) refreshCallback.run();
    }

    private void loadHistory() {
        try {
            List<TaskSession> sessions = repo.findByTaskId(task.id());
            Platform.runLater(() -> {
                historyList.getItems().clear();
                historyList.getItems().addAll(sessions);
            });
        } catch (Throwable ex) {
            System.err.println("[TaskTimerWindow] failed loading history: " + ex.getMessage());
        }
    }

    private void updateTotalLabel() {
        try {
            List<TaskSession> sessions = repo.findByTaskId(task.id());
            int total = sessions.stream().mapToInt(TaskSession::durationMinutes).sum();
            Platform.runLater(() -> totalLabel.setText("Tempo total: " + total + " min"));
        } catch (Throwable ex) {
            System.err.println("[TaskTimerWindow] failed updating total: " + ex.getMessage());
        }
    }

    private void editSelectedTodaySession() {
        TaskSession selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.warning("Editar sessão", "Selecione uma sessão do histórico para editar.");
            return;
        }
        if (!LocalDate.now().equals(selected.sessionDate())) {
            Dialogs.warning("Editar sessão", "Por segurança, apenas sessões de hoje podem ser editadas por aqui.");
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        WindowManager.prepare(dlg);
        dlg.setTitle("Editar sessão de hoje");
        dlg.setHeaderText("Atualize o tempo e as observações da sessão selecionada.");
        ButtonType saveBtn = new ButtonType("Salvar alterações", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField titleField = new TextField(selected.subject() != null ? selected.subject() : "");
        Spinner<Integer> minutesSpinner = new Spinner<>(0, 24 * 60, Math.max(0, selected.durationMinutes()));
        TextArea notes = new TextArea(selected.notes() != null ? selected.notes() : "");
        notes.setPrefRowCount(4);

        VBox content = new VBox(8,
                new Label("Título:"), titleField,
                new Label("Duração (min):"), minutesSpinner,
                new Label("Observações:"), notes);
        content.setPadding(new Insets(8));
        dlg.getDialogPane().setContent(content);
        dlg.setResultConverter(bt -> bt == saveBtn ? saveBtn : null);

        dlg.showAndWait().ifPresent(res -> {
            if (res == saveBtn) {
                repo.update(selected.id(), titleField.getText(), minutesSpinner.getValue(), notes.getText());
                loadHistory();
                updateTotalLabel();
            }
        });
    }
}
