package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleTasksService;
import com.pessoal.agenda.service.GoogleTasksService.GTask;
import com.pessoal.agenda.service.GoogleTasksService.SyncResult;
import com.pessoal.agenda.service.GoogleTasksService.TaskList;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    private final Runnable           onSyncCallback;

    private Stage  stage;
    private Label  statusLabel;
    private Label  connectionLabel;
    private Button connectBtn;
    private Button disconnectBtn;
    private Button syncBtn;

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
        this.onSyncCallback = onSyncCallback;
    }

    public void show() {
        if (openStage != null && openStage.isShowing()) {
            openStage.toFront(); openStage.requestFocus(); return;
        }

        stage = new Stage();
        stage.setTitle("☁  Google Tasks — Sincronização Bidirecional");
        stage.setMinWidth(920);
        stage.setMinHeight(560);
        stage.initModality(Modality.APPLICATION_MODAL);

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
        stage.show();
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        Label title = new Label("☁  Google Tasks — Sincronização Bidirecional");
        title.getStyleClass().add("page-title");

        connectionLabel = new Label();
        updateConnectionLabel();

        connectBtn = new Button("🔗  Conectar conta Google");
        connectBtn.getStyleClass().add("primary-button");
        connectBtn.setOnAction(e -> doConnect());

        disconnectBtn = new Button("✕  Desconectar");
        disconnectBtn.getStyleClass().add("danger-button");
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

    private HBox buildSyncBar() {
        Label listLabel = new Label("Lista Google:");
        listLabel.setStyle("-fx-font-weight: 600;");

        listCombo = new ComboBox<>();
        listCombo.setPrefWidth(220);
        listCombo.getStyleClass().add("input-control");
        listCombo.setPromptText("Selecione uma lista...");
        listCombo.setOnAction(e -> { if (listCombo.getValue() != null) loadGoogleTasks(); });

        Button refreshListsBtn = new Button("↻");
        refreshListsBtn.getStyleClass().add("secondary-button");
        refreshListsBtn.setTooltip(new Tooltip("Recarregar listas do Google"));
        refreshListsBtn.setOnAction(e -> loadGoogleTaskLists());

        syncBtn = new Button("🔄  Sincronizar Agora");
        syncBtn.getStyleClass().add("primary-button");
        syncBtn.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
        syncBtn.setOnAction(e -> doSync());

        // Botões de ação manual
        Button importSelBtn = new Button("⬇  Importar selecionada");
        importSelBtn.getStyleClass().add("secondary-button");
        importSelBtn.setOnAction(e -> importSelected());

        Button exportSelBtn = new Button("⬆  Exportar selecionada");
        exportSelBtn.getStyleClass().add("secondary-button");
        exportSelBtn.setOnAction(e -> exportSelected());

        Button dedupGoogleBtn = new Button("🔍  Remover duplicatas do Google");
        dedupGoogleBtn.getStyleClass().add("secondary-button");
        dedupGoogleBtn.setOnAction(e -> removeGoogleDuplicates());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, listLabel, listCombo, refreshListsBtn, spacer,
                importSelBtn, exportSelBtn, dedupGoogleBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL), syncBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
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
                        + (t.completed() ? "#27ae60;" : "-t-text;"));
                Label titleLbl = new Label(t.title() != null ? t.title() : "(sem título)");
                titleLbl.getStyleClass().add("study-plan-detail");
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
                        + (t.done() ? "#27ae60;" : "-t-text;"));
                Label titleLbl = new Label(t.title());
                titleLbl.getStyleClass().add("study-plan-detail");
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
        if (!auth.hasValidCredentials()) {
            showError("Credenciais não encontradas",
                "Arquivo não encontrado: ~/.agenda/google-credentials.json");
            return;
        }
        setStatus("Iniciando autorização OAuth...");
        runBackground(
            () -> { auth.authorize(msg -> Platform.runLater(() -> setStatus(msg))); return null; },
            result -> {
                updateConnectionLabel();
                refreshConnectButtons();
                setStatus("✓ Conectado ao Google Tasks!");
                loadGoogleTaskLists();
            },
            err -> showError("Erro de autorização", err.getMessage())
        );
    }

    private void doDisconnect() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Desconectar Google Tasks");
        confirm.setHeaderText("Deseja desconectar sua conta do Google?");
        confirm.setContentText("Os tokens de acesso e mapeamentos serão removidos localmente.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                auth.revoke();
                gTaskItems.clear();
                listCombo.getItems().clear();
                updateConnectionLabel();
                refreshConnectButtons();
                setStatus("Desconectado.");
            } catch (Exception e) {
                showError("Erro ao desconectar", e.getMessage());
            }
        });
    }

    private void updateConnectionLabel() {
        if (auth.isAuthorized()) {
            connectionLabel.setText("● Conectado");
            connectionLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: 700;");
        } else {
            connectionLabel.setText("● Desconectado");
            connectionLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: 700;");
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

        syncBtn.setDisable(true);
        setStatus("🔄 Sincronizando com '" + selected.title() + "'...");
        appendLog("─── Iniciando sync: " + selected.title() + " ───");

        runBackground(
            () -> gTasks.syncBidirectional(
                    selected.id(),
                    AppContextHolder.get().taskRepository(),
                    AppContextHolder.get().googleTasksMappingRepository()),
            result -> {
                // Atualiza a UI
                loadLocalTasks();
                loadGoogleTasks();
                if (onSyncCallback != null) onSyncCallback.run();
                syncBtn.setDisable(false);

                // Mostra resultado
                String summary = String.format(
                    "✓ Sync concluído — ⬇ %d criado(s) local / ⬆ %d criado(s) Google" +
                    " / ✓ %d concluído(s) local / ✓ %d concluído(s) Google%s",
                    result.createdLocal(), result.createdGoogle(),
                    result.completedLocal(), result.completedGoogle(),
                    result.errors() > 0 ? " / ✗ " + result.errors() + " erro(s)" : "");
                setStatus(summary);

                for (String line : result.log()) appendLog(line);
                appendLog("─── Fim do sync ───\n");

                if (!result.hasChanges() && result.errors() == 0) {
                    appendLog("(nenhuma alteração detectada)");
                }
            },
            err -> {
                syncBtn.setDisable(false);
                appendLog("✗ ERRO FATAL: " + err.getMessage());
                showError("Erro na sincronização", err.getMessage());
            }
        );
    }

    // ── Ações manuais ────────────────────────────────────────────────────────

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
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Remover Duplicatas do Google Tasks");
                confirm.setHeaderText(groups.size() + " título(s) duplicado(s) — "
                        + totalFinal + " tarefa(s) serão removidas do Google Tasks.");
                confirm.setContentText("A tarefa mais antiga será mantida em cada grupo.\n\n"
                        + sb.toString().trim());
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
                        err -> showError("Erro ao remover duplicatas", err.getMessage())
                    );
                });
            },
            err -> showError("Erro ao buscar duplicatas", err.getMessage())
        );
    }

    private void importSelected() {
        GTask selected = gTaskList.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Selecione uma tarefa do Google para importar."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }

        LocalDate due = selected.dueDate() != null ? selected.dueDate() : LocalDate.now();
        String notes  = selected.notes() != null && !selected.notes().isBlank() ? selected.notes() : null;
        AppContextHolder.get().taskRepository().save(selected.title(), notes, due, "Google Tasks");
        setStatus("Importada: " + selected.title());
        appendLog("⬇ Importada manualmente: " + selected.title());
        loadLocalTasks();
        if (onSyncCallback != null) onSyncCallback.run();
    }

    private void exportSelected() {
        com.pessoal.agenda.model.Task selected = localList.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Selecione uma tarefa local para exportar."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }
        TaskList targetList = listCombo.getValue();
        if (targetList == null) { setStatus("Selecione uma lista do Google Tasks primeiro."); return; }

        runBackground(
            () -> gTasks.createTask(targetList.id(), selected.title(), selected.notes(), selected.dueDate()),
            gId -> {
                if (gId != null) {
                    AppContextHolder.get().googleTasksMappingRepository()
                            .upsert(selected.id(), targetList.id(), gId);
                }
                setStatus("⬆ Exportada: " + selected.title());
                appendLog("⬆ Exportada manualmente: " + selected.title());
                loadGoogleTasks();
                loadLocalTasks();
            },
            err -> showError("Erro ao exportar", err.getMessage())
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
            err -> showError("Erro ao carregar listas", err.getMessage())
        );
    }

    private void loadGoogleTasks() {
        TaskList selected = listCombo.getValue();
        if (selected == null || !auth.isAuthorized()) return;
        runBackground(
            () -> gTasks.listTasks(selected.id(), true),
            tasks -> { gTaskItems.setAll(tasks); gTaskList.refresh(); },
            err -> showError("Erro ao carregar tarefas Google", err.getMessage())
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
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(msg != null ? msg : "Erro desconhecido.");
            alert.showAndWait();
            appendLog("✗ " + title + ": " + msg);
        });
    }

    private <T> void runBackground(
            java.util.concurrent.Callable<T> action,
            java.util.function.Consumer<T>    onSuccess,
            java.util.function.Consumer<Throwable> onError) {

        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("[GoogleTasks] Erro: " + ex.getMessage());
            onError.accept(ex);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
