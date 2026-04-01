package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskSession;
import com.pessoal.agenda.repository.TaskSessionRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Robust timer window for task sessions with history and metadata. */

public class TaskTimerWindow {
    // Gerencia janelas abertas por taskId
    private static final java.util.Map<Long, Stage> openWindows = new java.util.HashMap<>();

    private final Task task;
    private final TaskSessionRepository repo;
    private Stage stage;

    // UI
    private Button playPauseBtn;
    private Button stopBtn;
    private Button resetBtn;
    private Label timerLabel;
    private TextArea notesArea;
    private Label totalLabel;
    private ListView<String> historyList;

    // Estado do timer (apenas flags locais)
    private volatile boolean running = false;

    private final Runnable refreshCallback;

    // Para remover o tickListener ao fechar
    private Runnable tickUnsubscriber = null;
    // Tick listener para este timer
    private java.util.function.Consumer<Long> tickListener;

    public TaskTimerWindow(Task task, TaskSessionRepository repo, Runnable refreshCallback) {
        this.task = task; this.repo = repo; this.refreshCallback = refreshCallback;
        this.tickListener = sec -> {
            var timerService = com.pessoal.agenda.service.TaskTimerService.get();
            if (timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(task.id())) {
                javafx.application.Platform.runLater(() -> timerLabel.setText(formatTimer(sec)));
            }
        };
    }
    public TaskTimerWindow(Task task, TaskSessionRepository repo) {
        this(task, repo, null);
    }

    public void show() {
        // ── Evita abrir duplicata para a mesma tarefa ──
        Stage existing = openWindows.get(task.id());
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        stage = new Stage();
        openWindows.put(task.id(), stage);
        WindowManager.register(stage);
        stage.initModality(Modality.NONE);
        stage.setTitle("Timer — " + task.title());

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        // ── Barra superior elegante ──
        Label title = new Label("Timer da tarefa");
        title.getStyleClass().add("page-title");
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
        left.setPrefWidth(360);

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(8);
        metaGrid.setVgap(6);
        metaGrid.add(new Label("Tarefa:"), 0, 0);
        Label taskTitleLbl = new Label(task.title());
        taskTitleLbl.getStyleClass().add("section-title");
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
        HBox controls = new HBox(8, playPauseBtn, stopBtn, resetBtn, timerLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        // Notes
        Label notesLbl = new Label("Observações da sessão:");
        notesLbl.getStyleClass().add("form-label");
        notesArea = new TextArea();
        notesArea.getStyleClass().add("input-control");
        notesArea.setPrefRowCount(4);

        left.getChildren().addAll(metaGrid, controls, notesLbl, notesArea);
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        // Coluna direita: histórico e total
        VBox right = new VBox(12);
        right.setPrefWidth(260);
        Label histTitle = new Label("Histórico de sessões");
        histTitle.getStyleClass().add("section-title");
        historyList = new ListView<>();
        historyList.setPrefHeight(260);
        totalLabel = new Label("Tempo total: 0 min");
        totalLabel.getStyleClass().add("section-title");
        right.getChildren().addAll(histTitle, historyList, totalLabel);
        VBox.setVgrow(historyList, Priority.ALWAYS);

        main.getChildren().addAll(left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.NEVER);

        root.setCenter(main);

        // Handlers
        playPauseBtn.setOnAction(e -> toggle());
        stopBtn.setOnAction(e -> stopAndSave());
        resetBtn.setOnAction(e -> resetCounter());

        Scene sc = new Scene(root, 700, 440);
        var cssUrl = TaskTimerWindow.class.getResource("/com/pessoal/agenda/app.css");
        if (cssUrl != null) sc.getStylesheets().add(cssUrl.toExternalForm());
        stage.setScene(sc);
        stage.setOnHiding(e -> {
            // Remove tickListener e referência da janela
            if (tickUnsubscriber != null) tickUnsubscriber.run();
            openWindows.remove(task.id());
            if (refreshCallback != null) refreshCallback.run();
        });

        // Atualiza o label do timer com o valor global
        updateTimerLabelFromService();
        // Listener para ticks globais (agora múltiplos listeners)
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.addTickListener(tickListener);
        tickUnsubscriber = () -> timerService.removeTickListener(tickListener);

        loadHistory();
        updateTotalLabel();
        stage.show();
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
        playPauseBtn.setText("⏸");
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private synchronized void pauseTimer() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.pause();
        playPauseBtn.setText("▶");
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private synchronized void resumeTimer() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.resume();
        playPauseBtn.setText("⏸");
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    // Não há mais tick local

    private synchronized void stopAndSave() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        timerService.stop();
        playPauseBtn.setText("▶");
        if (refreshCallback != null) Platform.runLater(refreshCallback);
        showSaveSessionDialog(task, repo, notesArea.getText(), () -> {
            Platform.runLater(() -> timerLabel.setText("00:00:00"));
            notesArea.clear();
            loadHistory(); updateTotalLabel();
        });
    }

    /** Utilitário para exibir o diálogo de salvar sessão, reutilizável pela lista principal. */
    public static void showSaveSessionDialog(Task task, TaskSessionRepository repo, String notesText, Runnable onSave) {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        long s = timerService.getElapsedSeconds();
        int minutes = (int) Math.max(1, Math.round(s / 60.0));
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Salvar sessão"); dlg.setHeaderText("Salvar sessão de trabalho para a tarefa?");
        ButtonType saveBtn = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        TextField titleField = new TextField("Tarefa:#" + task.id() + " — " + task.title());
        Spinner<Integer> minutesSpinner = new Spinner<>(1, 24*60, minutes);
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
        Platform.runLater(() -> timerLabel.setText("00:00:00"));
        if (refreshCallback != null) Platform.runLater(refreshCallback);
    }

    private void updateTimerLabelFromService() {
        var timerService = com.pessoal.agenda.service.TaskTimerService.get();
        if (timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(task.id())) {
            timerLabel.setText(formatTimer(timerService.getElapsedSeconds()));
        } else {
            timerLabel.setText("00:00:00");
        }
    }

    private static String formatTimer(long s) {
        long hh = s / 3600; long mm = (s % 3600) / 60; long ss = s % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private void loadHistory() {
        try {
            List<TaskSession> sessions = repo.findByTaskId(task.id());
            Platform.runLater(() -> {
                historyList.getItems().clear();
                for (TaskSession s : sessions) {
                    historyList.getItems().add(String.format("%s — %d min — %s", s.sessionDate(), s.durationMinutes(), s.notes() == null ? "" : s.notes()));
                }
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
}

