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
 *   TOP    — cabeçalho: nome, tipo, descrição, progresso atual
 *   CENTER — SplitPane(execução atual com checkboxes | histórico de execuções)
 *   BOTTOM — barra de ações
 *
 * Regra: apenas uma janela por protocolo. Se a janela já estiver aberta,
 * ela é trazida para frente em vez de abrir uma duplicata.
 */
public class ProtocolExecutionWindow {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Janelas atualmente abertas, indexadas pelo ID do protocolo. */
    private static final java.util.Map<Long, Stage> openWindows =
            new java.util.HashMap<>();

    private final Protocol           protocol;
    private final ProtocolRepository repo;
    private final Runnable           onCloseCallback;

    private Stage stage;

    // ── Execução atual ──────────────────────────────────────────────────
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

    // ── Histórico ──────────────────────────────────────────────────────
    private final ObservableList<ProtocolExecution> historyItems =
            FXCollections.observableArrayList();
    private ListView<ProtocolExecution> historyList;
    private Label                       historyDetailLabel;

    public ProtocolExecutionWindow(Protocol protocol,
                                   ProtocolRepository repo,
                                   Runnable onCloseCallback) {
        this.protocol        = protocol;
        this.repo            = repo;
        this.onCloseCallback = onCloseCallback;
    }

    public void show() {
        // ── Evita abrir duplicata para o mesmo protocolo ─────────────────
        Stage existing = openWindows.get(protocol.id());
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(protocol.executionType().icon() + "  " + protocol.name());
        stage.setMinWidth(860);
        stage.setMinHeight(560);

        VBox root = new VBox(0);
        root.getStyleClass().add("diary-root");

        root.getChildren().addAll(buildHeader(), buildCenter());

        Scene scene = new Scene(root, 960, 620);
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

    // ── Header ─────────────────────────────────────────────────────────

    private HBox buildHeader() {
        Label typeBadge = new Label("  " + protocol.executionType().icon()
                + " " + protocol.executionType().label() + "  ");
        typeBadge.getStyleClass().addAll("study-badge", "badge-type");

        Label nameLbl = new Label(protocol.name());
        nameLbl.getStyleClass().add("diary-plan-title");

        Label catLbl = new Label(protocol.category() != null ? protocol.category() : "Geral");
        catLbl.getStyleClass().add("study-dates-label");

        execProgressBar = new ProgressBar(0);
        execProgressBar.setPrefWidth(200);
        execProgressBar.setPrefHeight(10);
        execProgressBar.getStyleClass().add("study-progress-bar");

        execProgressLabel = new Label("—");
        execProgressLabel.getStyleClass().add("study-progress-label");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row1 = new HBox(8, typeBadge, nameLbl, sp, execProgressBar, execProgressLabel);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label descLbl = new Label(
            protocol.description() != null && !protocol.description().isBlank()
                ? protocol.description() : protocol.executionType().description());
        descLbl.getStyleClass().add("diary-entry-detail");
        descLbl.setWrapText(true);

        // Badge de validade/prazo
        Label validityLbl = new Label();
        if (protocol.hasValidity()) {
            java.time.LocalDate nextDue = repo.nextDueDate(protocol.id(), protocol.validityDays());
            if (nextDue != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), nextDue);
                if (days < 0) {
                    validityLbl.setText("⚠  Validade vencida há " + (-days) + " dias  (válido por "
                            + protocol.validityDays() + "d)");
                    validityLbl.getStyleClass().add("deadline-overdue");
                } else {
                    validityLbl.setText("📅  Válido por mais " + days + " dias  (expira em "
                            + nextDue.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")");
                    validityLbl.getStyleClass().add(days <= 7 ? "deadline-warn" : "deadline-ok");
                }
            } else {
                validityLbl.setText("📅  Validade: " + protocol.validityDays() + " dias após conclusão");
                validityLbl.getStyleClass().add("study-dates-label");
            }
        }

        VBox header = new VBox(4, row1,
                new HBox(6, catLbl, new Label("|"), descLbl),
                validityLbl);
        validityLbl.setVisible(protocol.hasValidity());
        validityLbl.setManaged(protocol.hasValidity());
        header.setPadding(new Insets(12, 14, 10, 14));
        header.getStyleClass().add("diary-header");
        return new HBox(header);
    }

    // ── Center ─────────────────────────────────────────────────────────

    private SplitPane buildCenter() {
        SplitPane sp = new SplitPane(buildExecutionPanel(), buildHistoryPanel());
        sp.setDividerPositions(0.60);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    // ── Painel de Execução Atual ────────────────────────────────────────

    private VBox buildExecutionPanel() {
        execHeaderLabel = new Label("Nenhuma execução ativa");
        execHeaderLabel.getStyleClass().add("section-title");

        // Painel "sem execução"
        noExecLabel = new Label("Nenhuma execução em andamento.\nClique em 'Iniciar' para começar.");
        noExecLabel.getStyleClass().add("study-dates-label");
        noExecLabel.setWrapText(true);
        noExecLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        startBtn = new Button("▶  Iniciar Execução");
        startBtn.getStyleClass().add("primary-button");
        startBtn.setOnAction(e -> startNewExecution());

        VBox noExecBox = new VBox(16, noExecLabel, startBtn);
        noExecBox.setAlignment(Pos.CENTER);
        noExecBox.setPadding(new Insets(40));

        // Painel de execução ativa
        stepsContainer = new VBox(6);
        stepsContainer.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane stepsScroll = new ScrollPane(stepsContainer);
        stepsScroll.setFitToWidth(true);
        stepsScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(stepsScroll, Priority.ALWAYS);

        execNotesArea = new TextArea();
        execNotesArea.getStyleClass().add("input-control");
        execNotesArea.setPromptText("Observações desta execução (opcional)...");
        execNotesArea.setPrefRowCount(2);
        execNotesArea.setWrapText(true);

        completeBtn = new Button("✓  Concluir Execução");
        completeBtn.getStyleClass().add("primary-button");
        completeBtn.setOnAction(e -> completeExecution(false));

        restartBtn = new Button("↺  Concluir e Reiniciar");
        restartBtn.getStyleClass().add("secondary-button");
        restartBtn.setOnAction(e -> completeExecution(true));

        cancelExecBtn = new Button("✗  Cancelar Execução");
        cancelExecBtn.getStyleClass().add("danger-button");
        cancelExecBtn.setOnAction(e -> cancelCurrentExecution());

        HBox actionBar = new HBox(8, completeBtn, restartBtn, cancelExecBtn);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setPadding(new Insets(8, 0, 0, 0));

        activeExecPanel = new VBox(8, stepsScroll, new Label("Observações:"), execNotesArea, actionBar);
        activeExecPanel.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(activeExecPanel, Priority.ALWAYS);

        // StackPane alterna entre os dois painéis
        VBox panel = new VBox(8, execHeaderLabel, noExecBox);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(panel, Priority.ALWAYS);

        // Guardar referência para trocar conteúdo dinamicamente
        panel.setUserData(new Object[]{ noExecBox, activeExecPanel });
        return panel;
    }

    // ── Painel de Histórico ─────────────────────────────────────────────

    private VBox buildHistoryPanel() {
        Label title = new Label("Histórico de Execuções");
        title.getStyleClass().add("section-title");

        historyList = new ListView<>(historyItems);
        historyList.getStyleClass().add("clean-list");
        historyList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ProtocolExecution e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) { setText(null); setGraphic(null); return; }

                String icon  = e.isCompleted() ? "✓" : e.isCancelled() ? "✗" : "●";
                String color = e.isCompleted() ? "#27ae60" : e.isCancelled() ? "#e74c3c" : "#e67e22";
                String iter  = e.executionType() == ProtocolExecutionType.EXPERIMENTO
                             ? " — Iter. " + e.iterationNumber() : "";
                String dt    = e.startedAt() != null ? e.startedAt().format(DT_FMT) : "?";
                String steps = e.totalSteps() > 0
                             ? " (" + e.checkedSteps() + "/" + e.totalSteps() + " passos)" : "";

                Label iconLbl = new Label(icon);
                iconLbl.setTextFill(Color.web(color));
                iconLbl.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");

                Label mainLbl = new Label(dt + iter + steps);
                mainLbl.getStyleClass().add("study-plan-detail");

                Label statusLbl = new Label(e.status());
                statusLbl.getStyleClass().addAll("study-badge",
                    "badge-status-" + e.status().toLowerCase());

                Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
                HBox row = new HBox(8, iconLbl, mainLbl, sp2, statusLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); setText(null);
            }
        });
        VBox.setVgrow(historyList, Priority.ALWAYS);

        historyDetailLabel = new Label("Selecione uma execução para ver detalhes.");
        historyDetailLabel.getStyleClass().add("study-dates-label");
        historyDetailLabel.setWrapText(true);

        historyList.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, sel) -> showHistoryDetail(sel));

        VBox panel = new VBox(8, title, historyList, new Separator(), historyDetailLabel);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    // ── Lógica de Execução ──────────────────────────────────────────────

    private void loadCurrentExecution() {
        currentExecution = repo.findActiveExecution(protocol.id()).orElse(null);
        refreshExecutionPanel();
    }

    private void loadHistory() {
        historyItems.setAll(repo.findExecutions(protocol.id(), null));
    }

    @SuppressWarnings("unchecked")
    private void refreshExecutionPanel() {
        // Localiza o VBox pai via buildExecutionPanel
        // Usamos o stage para encontrar o SplitPane
        SplitPane sp = (SplitPane) ((VBox) stage.getScene().getRoot())
                .getChildren().stream()
                .filter(n -> n instanceof SplitPane)
                .findFirst().orElse(null);
        if (sp == null) return;

        VBox execPanel = (VBox) sp.getItems().get(0);
        Object[] refs  = (Object[]) execPanel.getUserData();
        if (refs == null) return;

        VBox noExecBox      = (VBox) refs[0];
        VBox activePanel    = (VBox) refs[1];

        // Atualiza header
        if (currentExecution == null) {
            execHeaderLabel.setText("Nenhuma execução ativa");
            execProgressBar.setProgress(0);
            execProgressLabel.setText("—");
            startBtn.setVisible(true);

            // Verifica se foi uso único já executado
            boolean unicoJaUsado = protocol.executionType() == ProtocolExecutionType.UNICO
                    && !repo.findExecutions(protocol.id(), "CONCLUIDA").isEmpty();
            if (unicoJaUsado) {
                noExecLabel.setText("Este protocolo é de uso único e já foi executado.\n"
                        + "Consulte o histórico para ver o registro.");
                startBtn.setVisible(false);
            } else {
                noExecLabel.setText("Nenhuma execução em andamento.\nClique em 'Iniciar' para começar.");
                startBtn.setVisible(true);
            }

            execPanel.getChildren().remove(activePanel);
            if (!execPanel.getChildren().contains(noExecBox)) execPanel.getChildren().add(noExecBox);
        } else {
            int iter = currentExecution.iterationNumber();
            String iterLabel = protocol.executionType() == ProtocolExecutionType.EXPERIMENTO
                    ? "  —  Iteração " + iter : "";
            execHeaderLabel.setText("Em execução" + iterLabel
                    + "  |  Iniciada: "
                    + (currentExecution.startedAt() != null
                       ? currentExecution.startedAt().format(DT_FMT) : "?"));

            double prog = currentExecution.progressPercent() / 100.0;
            execProgressBar.setProgress(prog);
            execProgressLabel.setText(currentExecution.checkedSteps()
                    + "/" + currentExecution.totalSteps() + " passos");

            execPanel.getChildren().remove(noExecBox);
            if (!execPanel.getChildren().contains(activePanel)) execPanel.getChildren().add(activePanel);

            refreshSteps();

            // Ajusta botões de conclusão por tipo
            boolean supportsRestart = protocol.executionType().supportsRestart();
            restartBtn.setVisible(supportsRestart);
            restartBtn.setManaged(supportsRestart);

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

        List<ProtocolExecutionStep> steps =
                repo.findExecutionSteps(currentExecution.id());

        for (ProtocolExecutionStep s : steps) {
            CheckBox cb = new CheckBox();
            cb.setSelected(s.checked());
            cb.setDisable(s.checked()); // passo já marcado vira read-only — mas pode desmarcar

            Label stepLbl = new Label(s.stepOrder() + ".  " + s.stepText());
            stepLbl.getStyleClass().add(s.critical() ? "study-plan-title" : "study-plan-detail");
            if (s.checked()) stepLbl.setStyle("-fx-opacity:0.55; -fx-strikethrough:true;");

            Label critLbl = new Label();
            if (s.critical()) { critLbl.setText("⚠ crítico"); critLbl.getStyleClass().add("deadline-overdue"); }

            Label timeStampLbl = new Label(
                s.checkedAt() != null ? s.checkedAt().format(DT_FMT) : "");
            timeStampLbl.getStyleClass().add("study-dates-label");

            // Desfazer marcação
            Button undoBtn = new Button("↩");
            undoBtn.setTooltip(new Tooltip("Desmarcar este passo"));
            undoBtn.getStyleClass().add("secondary-button");
            undoBtn.setVisible(s.checked());
            undoBtn.setManaged(s.checked());
            undoBtn.setOnAction(e -> {
                repo.uncheckStep(s.id());
                reloadExecution();
            });

            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    repo.checkStep(s.id(), null);
                    reloadExecution();
                }
            });

            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(10, cb, stepLbl, critLbl, spacer, timeStampLbl, undoBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.getStyleClass().add(s.checked() ? "checklist-done-row" : "checklist-pending-row");
            stepsContainer.getChildren().add(row);
        }
    }

    private void reloadExecution() {
        currentExecution = repo.findActiveExecution(protocol.id()).orElse(null);
        refreshExecutionPanel();
        loadHistory();
    }

    private void startNewExecution() {
        repo.startExecution(protocol.id());
        reloadExecution();
    }

    private void completeExecution(boolean andRestart) {
        if (currentExecution == null) return;
        String notes = execNotesArea.getText();

        // Verifica passos críticos não marcados
        List<ProtocolExecutionStep> steps = repo.findExecutionSteps(currentExecution.id());
        long criticalPending = steps.stream()
                .filter(s -> s.critical() && !s.checked()).count();
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
        confirm.setContentText("O progresso será perdido. A execução ficará registrada como Cancelada no histórico.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            repo.cancelExecution(currentExecution.id());
            currentExecution = null;
            reloadExecution();
        });
    }

    private void showHistoryDetail(ProtocolExecution exec) {
        if (exec == null) {
            historyDetailLabel.setText("Selecione uma execução para ver detalhes.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(exec.status()).append("\n");
        if (exec.startedAt() != null)
            sb.append("Início: ").append(exec.startedAt().format(DT_FMT)).append("\n");
        if (exec.completedAt() != null)
            sb.append("Fim: ").append(exec.completedAt().format(DT_FMT)).append("\n");
        sb.append("Progresso: ").append(exec.checkedSteps())
          .append("/").append(exec.totalSteps()).append(" passos");
        if (exec.notes() != null && !exec.notes().isBlank())
            sb.append("\nObservações: ").append(exec.notes());
        historyDetailLabel.setText(sb.toString());
    }
}


