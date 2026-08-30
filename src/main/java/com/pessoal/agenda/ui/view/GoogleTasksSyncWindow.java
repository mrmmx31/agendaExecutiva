package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleTasksService;
import com.pessoal.agenda.service.GoogleTasksSyncService;
import com.pessoal.agenda.service.GoogleTasksSyncService.PreparedSync;
import com.pessoal.agenda.service.GoogleTasksSyncService.Resolution;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewItem;
import com.pessoal.agenda.service.GoogleTasksSyncService.SyncPreview;
import com.pessoal.agenda.service.GoogleSyncErrorPresenter;
import com.pessoal.agenda.service.GoogleTasksService.GTask;
import com.pessoal.agenda.service.GoogleTasksService.SyncResult;
import com.pessoal.agenda.service.GoogleTasksService.TaskList;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

/**
 * Janela de sincronização BIDIRECIONAL com o Google Tasks.
 *
 * Fluxo de sync:
 *  - Google task sem mapeamento → cria localmente
 *  - Local task sem mapeamento  → cria no Google
 *  - Concluída em qualquer lado → conclui nos dois
 *  - Texto local → atualiza no Google (local é fonte de verdade para texto)
 */
public class GoogleTasksSyncWindow {

    private static Stage openStage;

    private final GoogleAuthService  auth;
    private final GoogleTasksService gTasks;
    private final GoogleTasksSyncService syncService;
    private final Runnable           onSyncCallback;
    private static final GoogleOperationGuard OPERATION_GUARD = GoogleOperationGuard.shared();
    private final List<Control> googleControls = new ArrayList<>();

    private Stage  stage;
    private Label  statusLabel;
    private Label  connectionLabel;
    private Button connectBtn;
    private Button disconnectBtn;
    private Button syncBtn;
    private Button reviewBtn;

    // Google side
    private ComboBox<TaskList>    listCombo;
    private ObservableList<GTask> gTaskItems = FXCollections.observableArrayList();
    private ListView<GTask>       gTaskList;

    // Local side
    private ObservableList<com.pessoal.agenda.model.Task> localItems = FXCollections.observableArrayList();
    private ListView<com.pessoal.agenda.model.Task>       localList;

    // Log de sync
    private TextArea logArea;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public GoogleTasksSyncWindow(Runnable onSyncCallback) {
        this.auth          = GoogleAuthService.getInstance();
        this.gTasks        = new GoogleTasksService();
        this.syncService = new GoogleTasksSyncService(
                gTasks,
                AppContextHolder.get().taskRepository(),
                AppContextHolder.get().googleTasksMappingRepository(),
                AppContextHolder.get().googleTasksSyncRepository());
        this.onSyncCallback = onSyncCallback;
    }

    public void show() {
        if (openStage != null && openStage.isShowing()) {
            openStage.toFront(); openStage.requestFocus(); return;
        }

        stage = WindowManager.createModelessStage();
        stage.setTitle("☁  Google Tasks — Sincronização Bidirecional");
        stage.setMinWidth(920);
        stage.setMinHeight(560);

        VBox root = new VBox(0);
        root.getStyleClass().add("app-root");
        root.getChildren().addAll(buildHeader(), buildSyncBar(), buildCenter(), buildBottom());

        Scene scene = new Scene(root, 1060, 660);
        ThemeManager.getInstance().applyTo(scene);
        stage.setScene(scene);
        stage.setOnHidden(e -> openStage = null);

        openStage = stage;
        loadLocalTasks();
        if (auth.isAuthorized()) loadGoogleTaskLists();
        WindowManager.show(stage);
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        Label title = new Label("☁  Google Tasks — Sincronização Bidirecional");
        title.getStyleClass().add("page-title");
        ResponsiveWindowLayout.makeFlexible(title);
        HBox.setHgrow(title, Priority.ALWAYS);

        connectionLabel = new Label();
        updateConnectionLabel();

        connectBtn = new Button("🔗  Conectar conta Google");
        connectBtn.getStyleClass().add("primary-button");
        registerGoogleControl(connectBtn);
        connectBtn.setOnAction(e -> doConnect());

        disconnectBtn = new Button("✕  Desconectar");
        disconnectBtn.getStyleClass().add("danger-button");
        registerGoogleControl(disconnectBtn);
        disconnectBtn.setOnAction(e -> doDisconnect());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, title, spacer, connectionLabel, connectBtn, disconnectBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 18, 12, 18));
        header.getStyleClass().add("header-bar");

        refreshConnectButtons();
        return header;
    }

    // ── Barra de sincronização central ───────────────────────────────────────

    private VBox buildSyncBar() {
        Label listLabel = new Label("Lista Google:");
        listLabel.setStyle("-fx-font-weight: 600;");

        listCombo = new ComboBox<>();
        listCombo.setPrefWidth(220);
        listCombo.getStyleClass().add("input-control");
        registerGoogleControl(listCombo);
        listCombo.setPromptText("Selecione uma lista...");
        listCombo.setOnAction(e -> {
            if (listCombo.getValue() != null) {
                refreshReviewCount();
                loadGoogleTasks();
            }
        });

        Button refreshListsBtn = new Button("↻");
        refreshListsBtn.getStyleClass().add("secondary-button");
        registerGoogleControl(refreshListsBtn);
        refreshListsBtn.setTooltip(new Tooltip("Recarregar listas do Google"));
        refreshListsBtn.setOnAction(e -> loadGoogleTaskLists());

        syncBtn = new Button("🔄  Sincronizar Agora");
        syncBtn.getStyleClass().add("primary-button");
        syncBtn.setId("google-sync-now");
        registerGoogleControl(syncBtn);
        syncBtn.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
        syncBtn.setOnAction(e -> doSync());

        // Botões de ação manual
        Button importSelBtn = new Button("⬇  Importar selecionada");
        importSelBtn.getStyleClass().add("secondary-button");
        registerGoogleControl(importSelBtn);
        importSelBtn.setOnAction(e -> importSelected());

        Button exportSelBtn = new Button("⬆  Exportar selecionada");
        exportSelBtn.getStyleClass().add("secondary-button");
        registerGoogleControl(exportSelBtn);
        exportSelBtn.setOnAction(e -> exportSelected());

        Button dedupGoogleBtn = new Button("🔍  Remover duplicatas do Google");
        dedupGoogleBtn.getStyleClass().add("secondary-button");
        registerGoogleControl(dedupGoogleBtn);
        dedupGoogleBtn.setOnAction(e -> removeGoogleDuplicates());

        reviewBtn = new Button("Revisar pendências");
        reviewBtn.setId("google-review-items");
        reviewBtn.getStyleClass().add("secondary-button");
        registerGoogleControl(reviewBtn);
        reviewBtn.setOnAction(e -> reviewPendingItems());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox selectionRow = new HBox(10, listLabel, listCombo, refreshListsBtn, spacer, syncBtn);
        selectionRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane manualActions = ResponsiveWindowLayout.actionFlow(
                importSelBtn, exportSelBtn, dedupGoogleBtn, reviewBtn);

        VBox bar = new VBox(8, selectionRow, manualActions);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    // ── Center ──────────────────────────────────────────────────────────────

    private SplitPane buildCenter() {
        SplitPane sp = new SplitPane(buildGooglePanel(), buildLocalPanel(), buildLogPanel());
        sp.setDividerPositions(0.38, 0.76);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    private VBox buildGooglePanel() {
        Label title = new Label("📋  Google Tasks");
        title.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");

        gTaskList = new ListView<>(gTaskItems);
        gTaskList.getStyleClass().add("clean-list");
        gTaskList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(GTask t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); setText(null); return; }
                Label icon = new Label(t.completed() ? "✓" : "○");
                icon.setStyle("-fx-font-size:14px; -fx-text-fill:"
                        + (t.completed() ? "-t-success;" : "-t-text;"));
                Label titleLbl = new Label(t.title() != null ? t.title() : "(sem título)");
                titleLbl.getStyleClass().add("study-plan-detail");
                ResponsiveWindowLayout.makeFlexible(titleLbl);
                if (t.completed()) titleLbl.setStyle("-fx-opacity:0.5;-fx-strikethrough:true;");
                HBox.setHgrow(titleLbl, Priority.ALWAYS);

                Label dateLbl = new Label(t.dueDate() != null ? t.dueDate().format(DATE_FMT) : "");
                dateLbl.setStyle("-fx-font-size:10px;-fx-opacity:0.6;");

                // Indica se tem mapeamento local
                boolean mapped = AppContextHolder.get()
                        .googleTasksMappingRepository()
                        .findByGoogleId(listCombo.getValue() != null ? listCombo.getValue().id() : "", t.id())
                        .isPresent();
                Label syncIcon = new Label(mapped ? "🔗" : "");
                syncIcon.setStyle("-fx-font-size:10px;");

                HBox row = new HBox(6, icon, titleLbl, dateLbl, syncIcon);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); setText(null);
            }
        });
        VBox.setVgrow(gTaskList, Priority.ALWAYS);

        VBox panel = new VBox(8, title, gTaskList);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(gTaskList, Priority.ALWAYS);
        return panel;
    }

    private VBox buildLocalPanel() {
        Label title = new Label("🗓  Tarefas Locais");
        title.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");

        localList = new ListView<>(localItems);
        localList.getStyleClass().add("clean-list");
        localList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.pessoal.agenda.model.Task t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); setText(null); return; }
                Label icon = new Label(t.done() ? "✓" : "○");
                icon.setStyle("-fx-font-size:14px;-fx-text-fill:"
                        + (t.done() ? "-t-success;" : "-t-text;"));
                Label titleLbl = new Label(t.title());
                titleLbl.getStyleClass().add("study-plan-detail");
                ResponsiveWindowLayout.makeFlexible(titleLbl);
                if (t.done()) titleLbl.setStyle("-fx-opacity:0.5;-fx-strikethrough:true;");
                HBox.setHgrow(titleLbl, Priority.ALWAYS);

                Label dateLbl = new Label(t.dueDate().format(DATE_FMT));
                dateLbl.setStyle("-fx-font-size:10px;-fx-opacity:0.6;");

                String prioIcon = switch (t.priority()) {
                    case CRITICA -> "🔴";
                    case ALTA    -> "🟠";
                    case NORMAL  -> "🔵";
                    case BAIXA   -> "⚪";
                };

                // Indica se tem mapeamento Google
                boolean mapped = AppContextHolder.get()
                        .googleTasksMappingRepository()
                        .findByLocalId(t.id()).isPresent();
                Label syncIcon = new Label(mapped ? "🔗" : "");
                syncIcon.setStyle("-fx-font-size:10px;");

                HBox row = new HBox(6, icon, new Label(prioIcon), titleLbl, dateLbl, syncIcon);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); setText(null);
            }
        });
        VBox.setVgrow(localList, Priority.ALWAYS);

        VBox panel = new VBox(8, title, localList);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(localList, Priority.ALWAYS);
        return panel;
    }

    private VBox buildLogPanel() {
        Label title = new Label("📄  Log de Sincronização");
        title.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("input-control");
        logArea.setStyle("-fx-font-family: 'JetBrains Mono','Consolas',monospace; -fx-font-size: 11px;");
        logArea.setPromptText("O log da sincronização aparecerá aqui...");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        Button clearBtn = new Button("Limpar log");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> logArea.clear());

        VBox panel = new VBox(8, title, logArea, clearBtn);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        return panel;
    }

    // ── Bottom ───────────────────────────────────────────────────────────────

    private HBox buildBottom() {
        statusLabel = new Label("Pronto.");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        Button refreshLocalBtn = new Button("↻  Atualizar");
        refreshLocalBtn.getStyleClass().add("secondary-button");
        registerGoogleControl(refreshLocalBtn);
        refreshLocalBtn.setOnAction(e -> { loadLocalTasks(); if (auth.isAuthorized()) loadGoogleTasks(); });

        Button closeBtn = new Button("Fechar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> stage.close());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, statusLabel, spacer, refreshLocalBtn, closeBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setStyle("-fx-border-color: -t-border; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    // ── Lógica de conexão ────────────────────────────────────────────────────

    private void doConnect() {
        GoogleAccountConnectionFlow.start(
                auth,
                this::setStatus,
                this::setGoogleControlsBusy,
                () -> {
                updateConnectionLabel();
                refreshConnectButtons();
                setStatus("✓ Conectado ao Google Tasks!");
                loadGoogleTaskLists();
                },
                error -> {
                    refreshConnectButtons();
                    showError("Erro de autorização", error);
                });
    }

    private void doDisconnect() {
        Dialogs.confirm("Desconectar Google Tasks", "Deseja desconectar sua conta do Google?",
                "Os tokens locais serão removidos. Os vínculos de sincronização serão preservados.")
                .filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                auth.revoke();
                gTaskItems.clear();
                listCombo.getItems().clear();
                updateConnectionLabel();
                refreshConnectButtons();
                setStatus("Desconectado.");
            } catch (Exception e) {
                showError("Erro ao desconectar", e);
            }
        });
    }

    private void updateConnectionLabel() {
        if (auth.isAuthorized()) {
            connectionLabel.setText("● Conectado");
            connectionLabel.setStyle("-fx-text-fill: -t-success; -fx-font-weight: 700;");
        } else {
            connectionLabel.setText("● Desconectado");
            connectionLabel.setStyle("-fx-text-fill: -t-err; -fx-font-weight: 700;");
        }
    }

    private void refreshConnectButtons() {
        boolean connected = auth.isAuthorized();
        connectBtn.setVisible(!connected); connectBtn.setManaged(!connected);
        disconnectBtn.setVisible(connected); disconnectBtn.setManaged(connected);
        if (syncBtn != null) syncBtn.setDisable(!connected);
    }

    // ── Sync Bidirecional ────────────────────────────────────────────────────

    private void doSync() {
        TaskList selected = listCombo.getValue();
        if (selected == null) { setStatus("Selecione uma lista do Google Tasks primeiro."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }

        setStatus("🔄 Sincronizando com '" + selected.title() + "'...");
        appendLog("─── Iniciando sync: " + selected.title() + " ───");

        runBackground(
            () -> syncService.prepareSync(selected.id()),
            prepared -> {
                SyncPreview preview = prepared.preview();
                appendLog(formatPreviewSummary(preview));
                int logLimit = Math.min(preview.details().size(), 20);
                for (int index = 0; index < logLimit; index++) {
                    appendLog("  " + preview.details().get(index));
                }
                if (preview.details().size() > logLimit) {
                    appendLog("  ... e mais " + (preview.details().size() - logLimit));
                }
                if (!preview.hasActions()) {
                    applyPreparedSync(selected, prepared);
                    return;
                }
                if (confirmPreview(selected, preview)) {
                    applyPreparedSync(selected, prepared);
                } else {
                    setStatus("Sincronização cancelada antes de alterar dados.");
                    appendLog("Prévia cancelada; nenhuma mudança aplicada.");
                }
            },
            err -> {
                updateConnectionLabel();
                refreshConnectButtons();
                appendLog("Falha ao preparar sincronização: "
                        + GoogleSyncErrorPresenter.logMessage(err));
                showError("Erro na sincronização", err);
            }
        );
    }

    private void applyPreparedSync(TaskList selected, PreparedSync prepared) {
        setStatus("Aplicando mudanças em '" + selected.title() + "'...");
        runBackground(
            () -> syncService.applyPrepared(prepared),
            result -> {
                // Atualiza a UI
                loadLocalTasks();
                loadGoogleTasks();
                refreshReviewCount();
                if (onSyncCallback != null) onSyncCallback.run();

                // Mostra resultado
                setStatus(formatSyncSummary(result));

                for (String line : result.log()) appendLog(line);
                appendLog("─── Fim do sync: " + selected.title() + " ───\n");

                if (!result.hasChanges() && result.errors() == 0) {
                    appendLog("(nenhuma alteração detectada)");
                }
            },
            err -> {
                updateConnectionLabel();
                refreshConnectButtons();
                appendLog("Falha na sincronização: "
                        + GoogleSyncErrorPresenter.logMessage(err));
                showError("Erro na sincronização", err);
            }
        );
    }

    private boolean confirmPreview(TaskList selected, SyncPreview preview) {
        Alert confirmation = Dialogs.build(Alert.AlertType.CONFIRMATION,
                "Prévia da sincronização",
                "Lista: " + selected.title(), previewDialogText(preview));
        ButtonType apply = new ButtonType("Aplicar mudanças", ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(apply, ButtonType.CANCEL);
        confirmation.getDialogPane().setPrefWidth(560);
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == apply;
    }

    private static String previewDialogText(SyncPreview preview) {
        StringBuilder text = new StringBuilder(formatPreviewSummary(preview));
        int limit = Math.min(preview.details().size(), 12);
        for (int index = 0; index < limit; index++) {
            text.append("\n• ").append(preview.details().get(index));
        }
        if (preview.details().size() > limit) {
            text.append("\n• ... e mais ").append(preview.details().size() - limit);
        }
        text.append("\n\nNada será alterado até confirmar.");
        return text.toString();
    }

    static String formatPreviewSummary(SyncPreview preview) {
        return String.format(
                "Prévia: %d criar local, %d criar Google, %d atualizar local, " +
                "%d atualizar Google, %d status local, %d status Google, %d revisar.",
                preview.createLocal(), preview.createGoogle(),
                preview.updateLocal(), preview.updateGoogle(),
                preview.statusLocal(), preview.statusGoogle(), preview.reviewRequired());
    }

    // ── Ações manuais ────────────────────────────────────────────────────────

    private void refreshReviewCount() {
        if (reviewBtn == null || listCombo == null || listCombo.getValue() == null) return;
        int count = syncService.listReviewItems(listCombo.getValue().id()).size();
        reviewBtn.setText(count == 0
                ? "Revisar pendências" : "Revisar pendências (" + count + ")");
    }

    private void reviewPendingItems() {
        TaskList selectedList = listCombo.getValue();
        if (selectedList == null) {
            setStatus("Selecione uma lista do Google Tasks primeiro.");
            return;
        }
        if (!auth.isAuthorized()) {
            setStatus("Conecte ao Google antes de resolver uma revisão.");
            return;
        }
        List<ReviewItem> items = syncService.listReviewItems(selectedList.id());
        if (items.isEmpty()) {
            setStatus("Nenhum conflito ou exclusão aguarda revisão.");
            return;
        }

        Dialog<ReviewDecision> dialog = Dialogs.prepare(new Dialog<>());
        dialog.setTitle("Revisar sincronização");
        dialog.setHeaderText("Escolha qual estado deve prevalecer");
        ButtonType apply = new ButtonType("Aplicar decisão", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(apply, ButtonType.CANCEL);

        ComboBox<ReviewItem> itemCombo = new ComboBox<>(FXCollections.observableArrayList(items));
        itemCombo.setId("google-review-item");
        itemCombo.setMaxWidth(Double.MAX_VALUE);
        itemCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ReviewItem item) {
                return item == null ? "" : reviewStateLabel(item.state()) + ": " + item.title();
            }
            @Override public ReviewItem fromString(String text) { return null; }
        });

        ToggleGroup choice = new ToggleGroup();
        RadioButton localChoice = new RadioButton();
        localChoice.setId("google-review-use-local");
        localChoice.setToggleGroup(choice);
        localChoice.setUserData(Resolution.USE_LOCAL);
        RadioButton googleChoice = new RadioButton();
        googleChoice.setId("google-review-use-google");
        googleChoice.setToggleGroup(choice);
        googleChoice.setUserData(Resolution.USE_GOOGLE);
        Label consequence = new Label();
        consequence.setId("google-review-consequence");
        consequence.setWrapText(true);
        consequence.getStyleClass().add("secondary-text");

        VBox content = new VBox(10, itemCombo, localChoice, googleChoice, consequence);
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);
        javafx.scene.Node applyNode = dialog.getDialogPane().lookupButton(apply);
        applyNode.setDisable(true);

        Runnable updateChoices = () -> {
            ReviewItem item = itemCombo.getValue();
            choice.selectToggle(null);
            consequence.setText("");
            if (item == null) return;
            localChoice.setText(resolutionLabel(item.state(), Resolution.USE_LOCAL));
            googleChoice.setText(resolutionLabel(item.state(), Resolution.USE_GOOGLE));
        };
        itemCombo.valueProperty().addListener((obs, old, value) -> updateChoices.run());
        choice.selectedToggleProperty().addListener((obs, old, toggle) -> {
            applyNode.setDisable(toggle == null || itemCombo.getValue() == null);
            if (toggle != null && itemCombo.getValue() != null) {
                consequence.setText(resolutionConsequence(itemCombo.getValue().state(),
                        (Resolution) toggle.getUserData()));
            }
        });
        dialog.setResultConverter(button -> {
            if (button != apply || itemCombo.getValue() == null
                    || choice.getSelectedToggle() == null) return null;
            return new ReviewDecision(itemCombo.getValue(),
                    (Resolution) choice.getSelectedToggle().getUserData());
        });
        itemCombo.getSelectionModel().selectFirst();
        updateChoices.run();

        dialog.showAndWait().ifPresent(decision -> {
            setStatus("Aplicando decisão para '" + decision.item().title() + "'...");
            runBackground(
                    () -> syncService.resolveReview(
                            decision.item().mappingId(), decision.resolution()),
                    result -> {
                        loadLocalTasks();
                        loadGoogleTasks();
                        refreshReviewCount();
                        if (onSyncCallback != null) onSyncCallback.run();
                        setStatus("Revisão resolvida: " + decision.item().title());
                        appendLog("Revisão resolvida: " + decision.item().title()
                                + " - " + resolutionLabel(
                                decision.item().state(), decision.resolution()));
                    },
                    error -> showError("Erro ao resolver revisão", error));
        });
    }

    static String resolutionLabel(SyncState state, Resolution resolution) {
        return switch (state) {
            case CONFLICT -> resolution == Resolution.USE_LOCAL
                    ? "Usar versão local" : "Usar versão Google";
            case REMOTE_DELETED -> resolution == Resolution.USE_LOCAL
                    ? "Recriar no Google" : "Excluir também a tarefa local";
            case LOCAL_DELETED -> resolution == Resolution.USE_LOCAL
                    ? "Excluir também no Google" : "Restaurar a tarefa local";
            case ACTIVE -> "Nenhuma decisão necessária";
        };
    }

    static String resolutionConsequence(SyncState state, Resolution resolution) {
        return switch (state) {
            case CONFLICT -> resolution == Resolution.USE_LOCAL
                    ? "Título, notas, data e status locais substituirão a versão Google."
                    : "Título, notas, data e status Google substituirão a versão local.";
            case REMOTE_DELETED -> resolution == Resolution.USE_LOCAL
                    ? "A tarefa local será mantida e aparecerá na próxima prévia para recriação."
                    : "A exclusão do Google será aceita e a tarefa local será excluída.";
            case LOCAL_DELETED -> resolution == Resolution.USE_LOCAL
                    ? "A exclusão local será aceita e a tarefa Google será excluída."
                    : "A versão Google será importada novamente como tarefa local.";
            case ACTIVE -> "Este item já está sincronizado.";
        };
    }

    private static String reviewStateLabel(SyncState state) {
        return switch (state) {
            case CONFLICT -> "Conflito";
            case REMOTE_DELETED -> "Excluída no Google";
            case LOCAL_DELETED -> "Excluída localmente";
            case ACTIVE -> "Sincronizada";
        };
    }

    private record ReviewDecision(ReviewItem item, Resolution resolution) {}

    private void removeGoogleDuplicates() {
        TaskList selected = listCombo.getValue();
        if (selected == null) { setStatus("Selecione uma lista do Google Tasks primeiro."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }

        setStatus("Procurando duplicatas no Google Tasks...");
        runBackground(
            () -> gTasks.findGoogleDuplicateGroups(selected.id()),
            groups -> {
                if (groups.isEmpty()) {
                    setStatus("Nenhuma duplicata encontrada no Google Tasks.");
                    appendLog("🔍 Sem duplicatas no Google Tasks.");
                    return;
                }
                // Monta preview
                StringBuilder sb = new StringBuilder();
                int total = 0;
                for (var g : groups) {
                    sb.append("📌 \"").append(g.get(0).title()).append("\"\n");
                    sb.append("   Manter:  ").append(g.get(0).id()).append("\n");
                    for (int i = 1; i < g.size(); i++) {
                        sb.append("   Remover: ").append(g.get(i).id())
                          .append("  (").append(g.get(i).title()).append(")\n");
                        total++;
                    }
                    sb.append("\n");
                }
                int totalFinal = total;
                Alert confirm = Dialogs.build(Alert.AlertType.CONFIRMATION,
                        "Remover Duplicatas do Google Tasks",
                        groups.size() + " título(s) duplicado(s) — " + totalFinal + " tarefa(s) serão removidas do Google Tasks.",
                        "A tarefa mais antiga será mantida em cada grupo.\n\n" + sb.toString().trim());
                confirm.getDialogPane().setPrefWidth(500);
                confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    runBackground(
                        () -> {
                            int removed = 0;
                            for (var g : groups) {
                                for (int i = 1; i < g.size(); i++) {
                                    gTasks.deleteTask(selected.id(), g.get(i).id());
                                    AppContextHolder.get().googleTasksMappingRepository()
                                            .deleteByGoogleId(selected.id(), g.get(i).id());
                                    removed++;
                                }
                            }
                            return removed;
                        },
                        removed -> {
                            setStatus("✓ " + removed + " duplicata(s) removida(s) do Google Tasks.");
                            appendLog("🗑 " + removed + " duplicata(s) removida(s) do Google Tasks.");
                            loadGoogleTasks();
                        },
                        err -> showError("Erro ao remover duplicatas", err)
                    );
                });
            },
            err -> showError("Erro ao buscar duplicatas", err)
        );
    }

    private void importSelected() {
        GTask selected = gTaskList.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Selecione uma tarefa do Google para importar."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }
        TaskList sourceList = listCombo.getValue();
        if (sourceList == null) { setStatus("Selecione uma lista do Google Tasks primeiro."); return; }

        runBackground(
                () -> syncService.importGoogleTask(sourceList.id(), selected),
                result -> {
                    if (result.created()) {
                        setStatus("Importada: " + selected.title());
                        appendLog("⬇ Importada manualmente: " + selected.title());
                    } else {
                        setStatus("Esta tarefa já estava importada.");
                        appendLog("↔ Importação ignorada; mapeamento já existente: " + selected.title());
                    }
                    loadLocalTasks();
                    if (onSyncCallback != null) onSyncCallback.run();
                },
                error -> showError("Erro ao importar", error));
    }

    private void exportSelected() {
        com.pessoal.agenda.model.Task selected = localList.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Selecione uma tarefa local para exportar."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }
        TaskList targetList = listCombo.getValue();
        if (targetList == null) { setStatus("Selecione uma lista do Google Tasks primeiro."); return; }

        runBackground(
            () -> syncService.exportLocalTask(targetList.id(), selected),
            created -> {
                setStatus(created
                        ? "⬆ Exportada: " + selected.title()
                        : "Esta tarefa já estava exportada.");
                appendLog(created
                        ? "⬆ Exportada manualmente: " + selected.title()
                        : "↔ Exportação ignorada; mapeamento já existente: " + selected.title());
                loadGoogleTasks();
                loadLocalTasks();
            },
            err -> showError("Erro ao exportar", err)
        );
    }

    // ── Carregar dados ───────────────────────────────────────────────────────

    private void loadGoogleTaskLists() {
        if (!auth.isAuthorized()) return;
        setStatus("Carregando listas...");
        runBackground(
            () -> gTasks.listTaskLists(),
            lists -> {
                listCombo.getItems().setAll(lists);
                if (!lists.isEmpty()) { listCombo.setValue(lists.get(0)); loadGoogleTasks(); }
                setStatus("Listas: " + lists.size());
            },
            err -> showError("Erro ao carregar listas", err)
        );
    }

    private void loadGoogleTasks() {
        TaskList selected = listCombo.getValue();
        if (selected == null || !auth.isAuthorized()) return;
        runBackground(
            () -> gTasks.listTasks(selected.id(), true),
            tasks -> { gTaskItems.setAll(tasks); gTaskList.refresh(); },
            err -> showError("Erro ao carregar tarefas Google", err)
        );
    }

    private void loadLocalTasks() {
        List<com.pessoal.agenda.model.Task> tasks =
                AppContextHolder.get().taskRepository().findOpenTasks();
        localItems.setAll(tasks);
        if (localList != null) localList.refresh();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(msg + "\n");
            }
        });
    }

    private void showError(String title, String msg) {
        Platform.runLater(() -> {
            Dialogs.error(title, msg != null ? msg : "Erro desconhecido.");
            appendLog("✗ " + title + ": " + msg);
        });
    }

    static String formatSyncSummary(SyncResult result) {
        return String.format(
                "✓ Sync concluído — ⬇ %d criado(s) local / ⬆ %d criado(s) Google" +
                " / ✓ %d status local / ✓ %d status Google" +
                " / ↻ %d atualizado(s) local / ↻ %d atualizado(s) Google" +
                " / %d Google + %d local verificados%s%s",
                result.createdLocal(), result.createdGoogle(),
                result.statusChangedLocal(), result.statusChangedGoogle(),
                result.updatedLocal(), result.updatedGoogle(),
                result.processedGoogle(), result.processedLocal(),
                result.reviewRequired() > 0
                        ? " / ! " + result.reviewRequired() + " para revisão" : "",
                result.errors() > 0 ? " / ✗ " + result.errors() + " erro(s)" : "");
    }

    private void showError(String title, Throwable error) {
        showError(title, GoogleSyncErrorPresenter.userMessage(error));
    }

    private <T> void runBackground(
            java.util.concurrent.Callable<T> action,
            java.util.function.Consumer<T>    onSuccess,
            java.util.function.Consumer<Throwable> onError) {

        if (!OPERATION_GUARD.tryStart()) {
            setStatus("Aguarde a operação Google em andamento terminar.");
            return;
        }
        setGoogleControlsBusy(true);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(e -> {
            finishGoogleOperation();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            finishGoogleOperation();
            System.err.println("[GoogleTasks] " + GoogleSyncErrorPresenter.logMessage(ex));
            onError.accept(ex);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void registerGoogleControl(Control control) {
        googleControls.add(control);
    }

    private void setGoogleControlsBusy(boolean busy) {
        for (Control control : googleControls) control.setDisable(busy);
    }

    private void finishGoogleOperation() {
        OPERATION_GUARD.finish();
        setGoogleControlsBusy(false);
        refreshConnectButtons();
    }
}
