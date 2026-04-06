package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.*;
import com.pessoal.agenda.repository.ProtocolRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Janela de execução de um Protocolo Operacional.
 *
 * Layout:
 *   TOP    — header-bar: nome, tipo, categoria, validade, progresso
 *   CENTER — SplitPane(execução atual com checkboxes | histórico de execuções)
 */
public class ProtocolExecutionWindow {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Janelas abertas por ID de protocolo. */
    private static final java.util.Map<Long, Stage> openWindows =
            new java.util.HashMap<>();

    private final Protocol           protocol;
    private final ProtocolRepository repo;
    private final Runnable           onCloseCallback;

    private Stage stage;

    // ── Execução atual ──────────────────────────────────────────────────────
    private ProtocolExecution currentExecution;
    private Label             execHeaderLabel;
    private Label             execProgressLabel;
    private ProgressBar       execProgressBar;
    private VBox              stepsContainer;
    private TextArea          execNotesArea;
    private Label             noExecLabel;
    private VBox              activeExecPanel;
    private Button            startBtn;
    private Button            completeBtn;
    private Button            restartBtn;
    private Button            cancelExecBtn;

    // ── Histórico ──────────────────────────────────────────────────────────
    private final ObservableList<ProtocolExecution> historyItems =
            FXCollections.observableArrayList();
    private ListView<ProtocolExecution> historyList;
    private VBox                        historyDetailBox;

    public ProtocolExecutionWindow(Protocol protocol,
                                   ProtocolRepository repo,
                                   Runnable onCloseCallback) {
        this.protocol        = protocol;
        this.repo            = repo;
        this.onCloseCallback = onCloseCallback;
    }

    public void show() {
        Stage existing = openWindows.get(protocol.id());
        if (existing != null && existing.isShowing()) {
            existing.toFront(); existing.requestFocus(); return;
        }

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(protocol.executionType().icon() + "  " + protocol.name());
        stage.setMinWidth(920);
        stage.setMinHeight(580);

        VBox root = new VBox(0);
        root.getStyleClass().add("app-root");
        root.getChildren().addAll(buildHeader(), buildCenter());

        Scene scene = new Scene(root, 1020, 650);
        try {
            scene.getStylesheets().add(
                getClass().getResource("/com/pessoal/agenda/app.css").toExternalForm());
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.setOnHidden(e -> {
            openWindows.remove(protocol.id());
            if (onCloseCallback != null) onCloseCallback.run();
        });

        openWindows.put(protocol.id(), stage);
        loadCurrentExecution();
        loadHistory();
        stage.show();
    }

    // ── Header (header-bar) ─────────────────────────────────────────────────

    private HBox buildHeader() {
        // Badge de tipo
        Label typeBadge = new Label("  " + protocol.executionType().icon()
                + "  " + protocol.executionType().label() + "  ");
        typeBadge.getStyleClass().addAll("study-badge", "badge-type");
        typeBadge.setStyle(typeBadge.getStyle()
                + "-fx-font-size: 11px; -fx-text-fill: #7ec8e3;"
                + " -fx-background-color: rgba(0,180,216,0.15);"
                + " -fx-border-color: rgba(0,180,216,0.4); -fx-border-radius: 10;");

        // Nome do protocolo
        Label nameLbl = new Label(protocol.name());
        nameLbl.getStyleClass().add("page-title");
        nameLbl.setWrapText(true);

        // Categoria
        Label catBadge = new Label("  " + (protocol.category() != null ? protocol.category() : "Geral") + "  ");
        catBadge.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #a8d4e6;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        // Barra de progresso
        execProgressBar = new ProgressBar(0);
        execProgressBar.setPrefWidth(220);
        execProgressBar.setPrefHeight(10);
        execProgressBar.getStyleClass().add("study-progress-bar");

        execProgressLabel = new Label("—");
        execProgressLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700;"
                + " -fx-text-fill: #7ec8e3;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(10, typeBadge, nameLbl, catBadge, spacer,
                execProgressBar, execProgressLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Descrição / hint de tipo
        Label descLbl = new Label(
            protocol.description() != null && !protocol.description().isBlank()
                ? protocol.description() : protocol.executionType().description());
        descLbl.setStyle("-fx-text-fill: #a8d4e6; -fx-font-size: 11.5px;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
        descLbl.setWrapText(true);

        // Badge de validade
        Label validityLbl = new Label();
        validityLbl.setWrapText(false);
        if (protocol.hasValidity()) {
            java.time.LocalDate nextDue = repo.nextDueDate(protocol.id(), protocol.validityDays());
            if (nextDue != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), nextDue);
                if (days < 0) {
                    validityLbl.setText("⚠  Validade VENCIDA há " + (-days) + " dias"
                            + "  (ciclo: " + protocol.validityDays() + "d)");
                    validityLbl.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: 700;"
                            + " -fx-font-size: 11px;"
                            + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
                } else {
                    validityLbl.setText("📅  Válido por mais " + days + " dias"
                            + "  (expira: " + nextDue.format(DATE_FMT) + ")");
                    validityLbl.setStyle("-fx-text-fill: " + (days <= 7 ? "#ffa94d" : "#69db7c") + ";"
                            + " -fx-font-size: 11px;"
                            + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
                }
            } else {
                validityLbl.setText("📅  Validade: " + protocol.validityDays() + " dias após conclusão");
                validityLbl.setStyle("-fx-text-fill: #a8d4e6; -fx-font-size: 11px;"
                        + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
            }
        }

        VBox headerContent = new VBox(5, topRow, descLbl);
        if (protocol.hasValidity()) headerContent.getChildren().add(validityLbl);
        headerContent.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(headerContent, Priority.ALWAYS);

        HBox header = new HBox(headerContent);
        header.setPadding(new Insets(14, 18, 12, 18));
        header.getStyleClass().add("header-bar");
        return header;
    }

    // ── Center ──────────────────────────────────────────────────────────────

    private SplitPane buildCenter() {
        SplitPane sp = new SplitPane(buildExecutionPanel(), buildHistoryPanel());
        sp.setDividerPositions(0.60);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    // ── Painel de Execução Atual ─────────────────────────────────────────────

    private VBox buildExecutionPanel() {
        execHeaderLabel = new Label("Nenhuma execução ativa");
        execHeaderLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;"
                + " -fx-text-fill: #03183e;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0; -fx-padding: 0 0 6 0;");
        execHeaderLabel.setMaxWidth(Double.MAX_VALUE);

        // Painel "sem execução"
        noExecLabel = new Label("Nenhuma execução em andamento.\nClique em 'Iniciar Execução' para começar.");
        noExecLabel.getStyleClass().add("study-dates-label");
        noExecLabel.setWrapText(true);
        noExecLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        startBtn = new Button("▶  Iniciar Execução");
        startBtn.getStyleClass().add("primary-button");
        startBtn.setOnAction(e -> startNewExecution());

        VBox noExecBox = new VBox(16, noExecLabel, startBtn);
        noExecBox.setAlignment(Pos.CENTER);
        noExecBox.setPadding(new Insets(40));

        // Passos da execução ativa
        stepsContainer = new VBox(5);
        stepsContainer.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane stepsScroll = new ScrollPane(stepsContainer);
        stepsScroll.setFitToWidth(true);
        stepsScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(stepsScroll, Priority.ALWAYS);

        // Notas
        Label notesLbl = new Label("Observações da execução:");
        notesLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a7a9e; -fx-font-weight: 600;");
        execNotesArea = new TextArea();
        execNotesArea.getStyleClass().add("input-control");
        execNotesArea.setPromptText("Observações desta execução (opcional)...");
        execNotesArea.setPrefRowCount(2);
        execNotesArea.setWrapText(true);

        // Botões de conclusão
        completeBtn = new Button("✓  Concluir Execução");
        completeBtn.getStyleClass().add("primary-button");
        completeBtn.setOnAction(e -> completeExecution(false));

        restartBtn = new Button("↺  Concluir e Reiniciar");
        restartBtn.getStyleClass().add("secondary-button");
        restartBtn.setOnAction(e -> completeExecution(true));

        cancelExecBtn = new Button("✗  Cancelar Execução");
        cancelExecBtn.getStyleClass().add("danger-button");
        cancelExecBtn.setOnAction(e -> cancelCurrentExecution());

        HBox actionBar = new HBox(8, completeBtn, restartBtn, createSpacer(), cancelExecBtn);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setPadding(new Insets(8, 0, 0, 0));
        actionBar.setStyle("-fx-border-color: #d6e4f5; -fx-border-width: 1 0 0 0;");

        activeExecPanel = new VBox(8, stepsScroll, notesLbl, execNotesArea, actionBar);
        activeExecPanel.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(stepsScroll, Priority.ALWAYS);
        VBox.setVgrow(activeExecPanel, Priority.ALWAYS);

        VBox panel = new VBox(8, execHeaderLabel, noExecBox);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(panel, Priority.ALWAYS);

        panel.setUserData(new Object[]{ noExecBox, activeExecPanel });
        return panel;
    }

    // ── Painel de Histórico ──────────────────────────────────────────────────

    private VBox buildHistoryPanel() {
        Label title = new Label("📜 Histórico de Execuções");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #03183e;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0; -fx-padding: 0 0 6 0;");
        title.setMaxWidth(Double.MAX_VALUE);

        historyList = new ListView<>(historyItems);
        historyList.getStyleClass().add("clean-list");
        historyList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ProtocolExecution e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) { setText(null); setGraphic(null); return; }

                String icon  = e.isCompleted() ? "✓" : e.isCancelled() ? "✗" : "●";
                Color  color = e.isCompleted() ? Color.web("#27ae60")
                             : e.isCancelled() ? Color.web("#e74c3c") : Color.web("#e67e22");
                String iter  = e.executionType() == ProtocolExecutionType.EXPERIMENTO
                             ? "  Iter. " + e.iterationNumber() : "";
                String dt    = e.startedAt() != null ? e.startedAt().format(DT_FMT) : "?";
                String steps = e.totalSteps() > 0
                             ? "  " + e.checkedSteps() + "/" + e.totalSteps() + " passos" : "";

                Label iconLbl = new Label(icon);
                iconLbl.setTextFill(color);
                iconLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

                Label mainLbl = new Label(dt + iter + steps);
                mainLbl.getStyleClass().add("study-plan-detail");

                Label statusLbl = new Label("  " + e.status() + "  ");
                statusLbl.getStyleClass().addAll("study-badge",
                        "badge-status-" + e.status().toLowerCase());

                Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
                HBox row = new HBox(8, iconLbl, mainLbl, sp2, statusLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); setText(null);
            }
        });
        VBox.setVgrow(historyList, Priority.ALWAYS);

        // Painel de detalhes da execução selecionada
        historyDetailBox = new VBox(6);
        historyDetailBox.setStyle("-fx-background-color: #f8fafc;"
                + " -fx-background-radius: 5; -fx-border-color: #dce8f5;"
                + " -fx-border-radius: 5; -fx-padding: 8;");
        historyDetailBox.setMinHeight(80);

        Label detailHdr = new Label("Detalhes da Execução");
        detailHdr.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #5a7a9e; -fx-font-weight: 700;");
        historyDetailBox.getChildren().add(
                new Label("Selecione uma execução para ver detalhes.") {{
                    getStyleClass().add("study-dates-label"); }});

        historyList.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, sel) -> showHistoryDetail(sel));

        VBox panel = new VBox(8, title, historyList, historyDetailBox);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    // ── Lógica de Execução ───────────────────────────────────────────────────

    private void loadCurrentExecution() {
        currentExecution = repo.findActiveExecution(protocol.id()).orElse(null);
        refreshExecutionPanel();
    }

    private void loadHistory() {
        historyItems.setAll(repo.findExecutions(protocol.id(), null));
    }

    @SuppressWarnings("unchecked")
    private void refreshExecutionPanel() {
        SplitPane sp = (SplitPane) ((VBox) stage.getScene().getRoot())
                .getChildren().stream()
                .filter(n -> n instanceof SplitPane)
                .findFirst().orElse(null);
        if (sp == null) return;

        VBox execPanel = (VBox) sp.getItems().get(0);
        Object[] refs  = (Object[]) execPanel.getUserData();
        if (refs == null) return;

        VBox noExecBox   = (VBox) refs[0];
        VBox activePanel = (VBox) refs[1];

        if (currentExecution == null) {
            execHeaderLabel.setText("Nenhuma execução ativa");
            execProgressBar.setProgress(0);
            execProgressLabel.setText("—");

            boolean unicoJaUsado = protocol.executionType() == ProtocolExecutionType.UNICO
                    && !repo.findExecutions(protocol.id(), "CONCLUIDA").isEmpty();
            if (unicoJaUsado) {
                noExecLabel.setText("Este protocolo é de uso único e já foi executado.\n"
                        + "Consulte o histórico para ver o registro completo.");
                startBtn.setVisible(false); startBtn.setManaged(false);
            } else {
                noExecLabel.setText("Nenhuma execução em andamento.\nClique em 'Iniciar Execução' para começar.");
                startBtn.setVisible(true); startBtn.setManaged(true);
            }

            execPanel.getChildren().remove(activePanel);
            if (!execPanel.getChildren().contains(noExecBox)) execPanel.getChildren().add(noExecBox);
        } else {
            int iter = currentExecution.iterationNumber();
            String iterLabel = protocol.executionType() == ProtocolExecutionType.EXPERIMENTO
                    ? "  —  Iteração " + iter : "";
            execHeaderLabel.setText("Em execução" + iterLabel
                    + "  ·  Iniciada: "
                    + (currentExecution.startedAt() != null
                       ? currentExecution.startedAt().format(DT_FMT) : "?"));

            double prog = currentExecution.progressPercent() / 100.0;
            execProgressBar.setProgress(prog);
            execProgressLabel.setText(currentExecution.checkedSteps()
                    + "/" + currentExecution.totalSteps() + " passos");

            execPanel.getChildren().remove(noExecBox);
            if (!execPanel.getChildren().contains(activePanel)) execPanel.getChildren().add(activePanel);

            refreshSteps();

            boolean supportsRestart = protocol.executionType().supportsRestart();
            restartBtn.setVisible(supportsRestart); restartBtn.setManaged(supportsRestart);

            if (protocol.executionType() == ProtocolExecutionType.EXPERIMENTO) {
                restartBtn.setText("↺  Concluir Iter. " + iter + " → Próxima");
                completeBtn.setText("✓  Encerrar Experimento");
            } else if (supportsRestart) {
                restartBtn.setText("↺  Concluir e Reiniciar");
                completeBtn.setText("✓  Concluir sem Reiniciar");
            } else if (protocol.executionType() == ProtocolExecutionType.UNICO) {
                completeBtn.setText("✓  Concluir (uso único)");
            } else {
                completeBtn.setText("✓  Concluir Execução");
            }
        }
    }

    private void refreshSteps() {
        stepsContainer.getChildren().clear();
        if (currentExecution == null) return;

        List<ProtocolExecutionStep> steps = repo.findExecutionSteps(currentExecution.id());

        for (ProtocolExecutionStep s : steps) {
            boolean done = s.checked();

            CheckBox cb = new CheckBox();
            cb.setSelected(done);
            cb.setDisable(done);

            Label numLbl = new Label(s.stepOrder() + ".");
            numLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #5a7a9e;"
                    + " -fx-min-width: 28px;");

            Label stepLbl = new Label(s.stepText());
            stepLbl.getStyleClass().add(s.critical() ? "study-plan-title" : "study-plan-detail");
            stepLbl.setWrapText(true);
            HBox.setHgrow(stepLbl, Priority.ALWAYS);
            if (done) stepLbl.setStyle("-fx-opacity: 0.5; -fx-strikethrough: true;");

            Label critLbl = new Label();
            if (s.critical()) {
                critLbl.setText("⚠ crítico");
                critLbl.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 700;"
                        + " -fx-text-fill: #b71c1c; -fx-background-color: #fde8e8;"
                        + " -fx-background-radius: 8; -fx-padding: 1 6 1 6;");
            }

            Label timeStampLbl = new Label(s.checkedAt() != null ? s.checkedAt().format(DT_FMT) : "");
            timeStampLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #5a7a9e;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            Button undoBtn = new Button("↩");
            undoBtn.getStyleClass().add("secondary-button");
            undoBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
            undoBtn.setTooltip(new Tooltip("Desmarcar este passo"));
            undoBtn.setVisible(done); undoBtn.setManaged(done);
            undoBtn.setOnAction(e -> { repo.uncheckStep(s.id()); reloadExecution(); });

            cb.setOnAction(e -> {
                if (cb.isSelected()) { repo.checkStep(s.id(), null); reloadExecution(); }
            });

            HBox row = new HBox(8, cb, numLbl, stepLbl, critLbl,
                    new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
                    timeStampLbl, undoBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.setStyle("-fx-background-color: " + (done ? "#f8fffe" : "white") + ";"
                    + " -fx-background-radius: 5;"
                    + " -fx-border-color: " + (done ? "#b2dfdb" : "#e8eef4") + ";"
                    + " -fx-border-radius: 5; -fx-border-width: 1;");
            stepsContainer.getChildren().add(row);
        }
    }

    private void reloadExecution() {
        currentExecution = repo.findActiveExecution(protocol.id()).orElse(null);
        refreshExecutionPanel();
        loadHistory();
    }

    private void startNewExecution() {
        repo.startExecution(protocol.id()); reloadExecution();
    }

    private void completeExecution(boolean andRestart) {
        if (currentExecution == null) return;
        String notes = execNotesArea.getText();

        List<ProtocolExecutionStep> steps = repo.findExecutionSteps(currentExecution.id());
        long criticalPending = steps.stream().filter(s -> s.critical() && !s.checked()).count();
        if (criticalPending > 0) {
            Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
            warn.setTitle("Passos Críticos Pendentes");
            warn.setHeaderText(criticalPending + " passo(s) CRÍTICO(S) ainda não foram marcados.");
            warn.setContentText("Deseja concluir mesmo assim?");
            Optional<ButtonType> r = warn.showAndWait();
            if (r.isEmpty() || r.get() != ButtonType.OK) return;
        }

        repo.completeExecution(currentExecution.id(), notes);
        execNotesArea.clear();

        if (andRestart) {
            repo.startExecution(protocol.id());
        } else {
            currentExecution = null;
        }
        reloadExecution();
    }

    private void cancelCurrentExecution() {
        if (currentExecution == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancelar Execução");
        confirm.setHeaderText("Cancelar a execução em andamento?");
        confirm.setContentText(
                "O progresso será perdido. A execução ficará registrada como Cancelada no histórico.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            repo.cancelExecution(currentExecution.id());
            currentExecution = null;
            reloadExecution();
        });
    }

    private void showHistoryDetail(ProtocolExecution exec) {
        historyDetailBox.getChildren().clear();
        if (exec == null) {
            historyDetailBox.getChildren().add(new Label("Selecione uma execução para ver detalhes.") {{
                getStyleClass().add("study-dates-label");
            }});
            return;
        }

        // Status badge
        Label statusBadge = new Label("  " + exec.status() + "  ");
        statusBadge.getStyleClass().addAll("study-badge",
                "badge-status-" + exec.status().toLowerCase());

        // Métricas
        HBox statusRow = new HBox(8, statusBadge);
        if (exec.executionType() == ProtocolExecutionType.EXPERIMENTO)
            statusRow.getChildren().add(new Label("Iteração " + exec.iterationNumber() + "  ") {{
                setStyle("-fx-font-size: 11px; -fx-text-fill: #5a7a9e;"
                        + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
            }});
        statusRow.setAlignment(Pos.CENTER_LEFT);

        historyDetailBox.getChildren().add(statusRow);

        // Detalhes de tempo
        addDetailRow(historyDetailBox, "Início",
                exec.startedAt() != null ? exec.startedAt().format(DT_FMT) : "—");
        addDetailRow(historyDetailBox, "Conclusão",
                exec.completedAt() != null ? exec.completedAt().format(DT_FMT) : "—");

        // Duração
        if (exec.startedAt() != null && exec.completedAt() != null) {
            long mins = java.time.Duration.between(exec.startedAt(), exec.completedAt()).toMinutes();
            addDetailRow(historyDetailBox, "Duração",
                    mins < 60 ? mins + " min"
                              : (mins / 60) + "h " + (mins % 60) + "min");
        }

        addDetailRow(historyDetailBox, "Progresso",
                exec.checkedSteps() + " / " + exec.totalSteps() + " passos concluídos");

        if (exec.notes() != null && !exec.notes().isBlank()) {
            Label notesLbl = new Label("Obs: " + exec.notes());
            notesLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #1e3a5f; -fx-wrap-text: true;"
                    + " -fx-background-color: #f0f5fc; -fx-background-radius: 4; -fx-padding: 4 6 4 6;");
            notesLbl.setWrapText(true);
            historyDetailBox.getChildren().add(notesLbl);
        }
    }

    private static void addDetailRow(VBox parent, String key, String value) {
        Label lbl = new Label(key + ":  " + value);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #1e3a5f;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
        parent.getChildren().add(lbl);
    }

    // ── Spacer helper ────────────────────────────────────────────────────────

    private static Region createSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
