package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleTasksService;
import com.pessoal.agenda.service.GoogleTasksService.GTask;
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
 * Janela de sincronização com o Google Tasks.
 *
 * Layout:
 *   TOP    — header-bar com status de conexão
 *   CENTER — SplitPane: Google Tasks (esq.) | Tarefas locais (dir.)
 *   BOTTOM — barra de status e botões de ação
 */
public class GoogleTasksSyncWindow {

    private static Stage openStage;

    private final GoogleAuthService  auth;
    private final GoogleTasksService gTasks;
    private final Runnable          onSyncCallback;

    private Stage  stage;
    private Label  statusLabel;
    private Label  connectionLabel;
    private Button connectBtn;
    private Button disconnectBtn;

    // Google side
    private ComboBox<TaskList>                 listCombo;
    private ObservableList<GTask>              gTaskItems = FXCollections.observableArrayList();
    private ListView<GTask>                    gTaskList;
    private Button                            loadListBtn;

    // Local side
    private ObservableList<com.pessoal.agenda.model.Task> localItems = FXCollections.observableArrayList();
    private ListView<com.pessoal.agenda.model.Task>       localList;
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
        stage.setTitle("☁  Google Tasks — Sincronização");
        stage.setMinWidth(860);
        stage.setMinHeight(520);
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(0);
        root.getStyleClass().add("app-root");
        root.getChildren().addAll(buildHeader(), buildCenter(), buildBottom());

        Scene scene = new Scene(root, 1000, 600);
        ThemeManager.getInstance().applyTo(scene);
        stage.setScene(scene);
        stage.setOnHidden(e -> openStage = null);

        openStage = stage;
        loadLocalTasks();
        stage.show();
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        Label title = new Label("☁  Google Tasks");
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

    // ── Center ──────────────────────────────────────────────────────────────

    private SplitPane buildCenter() {
        SplitPane sp = new SplitPane(buildGooglePanel(), buildLocalPanel());
        sp.setDividerPositions(0.50);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    private VBox buildGooglePanel() {
        Label title = new Label("📋  Listas do Google Tasks");
        title.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");
        title.setMaxWidth(Double.MAX_VALUE);

        listCombo = new ComboBox<>();
        listCombo.setMaxWidth(Double.MAX_VALUE);
        listCombo.getStyleClass().add("input-control");
        listCombo.setPromptText("Selecione uma lista...");
        listCombo.setOnAction(e -> {
            if (listCombo.getValue() != null) loadGoogleTasks();
        });

        loadListBtn = new Button("↻  Carregar listas");
        loadListBtn.getStyleClass().add("secondary-button");
        loadListBtn.setMaxWidth(Double.MAX_VALUE);
        loadListBtn.setOnAction(e -> loadGoogleTaskLists());

        HBox comboRow = new HBox(6, listCombo, loadListBtn);
        HBox.setHgrow(listCombo, Priority.ALWAYS);

        gTaskList = new ListView<>(gTaskItems);
        gTaskList.getStyleClass().add("clean-list");
        gTaskList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(GTask t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); setText(null); return; }
                Label icon = new Label(t.completed() ? "✓" : "○");
                icon.setStyle("-fx-font-size: 14px; -fx-text-fill: "
                        + (t.completed() ? "#27ae60;" : "-t-text;"));
                Label titleLbl = new Label(t.title() != null ? t.title() : "(sem título)");
                titleLbl.getStyleClass().add("study-plan-detail");
                if (t.completed()) titleLbl.setStyle("-fx-opacity:0.5;-fx-strikethrough:true;");
                HBox.setHgrow(titleLbl, Priority.ALWAYS);

                Label dateLbl = new Label();
                if (t.dueDate() != null) {
                    dateLbl.setText(t.dueDate().format(DATE_FMT));
                    dateLbl.setStyle("-fx-font-size:10px;-fx-opacity:0.6;");
                }
                HBox row = new HBox(8, icon, titleLbl, dateLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); setText(null);
            }
        });
        VBox.setVgrow(gTaskList, Priority.ALWAYS);

        // Botões de ação no lado Google
        Button importBtn = new Button("⬇  Importar selecionada → Local");
        importBtn.getStyleClass().add("secondary-button");
        importBtn.setMaxWidth(Double.MAX_VALUE);
        importBtn.setOnAction(e -> importSelected());

        Button importAllBtn = new Button("⬇⬇  Importar todas pendentes → Local");
        importAllBtn.getStyleClass().add("secondary-button");
        importAllBtn.setMaxWidth(Double.MAX_VALUE);
        importAllBtn.setOnAction(e -> importAllPending());

        VBox panel = new VBox(8, title, comboRow, gTaskList, importBtn, importAllBtn);
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
                Label prioLbl = new Label(prioIcon);

                HBox row = new HBox(8, icon, prioLbl, titleLbl, dateLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); setText(null);
            }
        });
        VBox.setVgrow(localList, Priority.ALWAYS);

        Button exportBtn = new Button("⬆  Exportar selecionada → Google Tasks");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setOnAction(e -> exportSelected());

        Button exportPendingBtn = new Button("⬆⬆  Exportar todas pendentes → Google Tasks");
        exportPendingBtn.getStyleClass().add("secondary-button");
        exportPendingBtn.setMaxWidth(Double.MAX_VALUE);
        exportPendingBtn.setOnAction(e -> exportAllPending());

        VBox panel = new VBox(8, title, localList, exportBtn, exportPendingBtn);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("section-card");
        VBox.setVgrow(localList, Priority.ALWAYS);
        return panel;
    }

    // ── Bottom ───────────────────────────────────────────────────────────────

    private HBox buildBottom() {
        statusLabel = new Label("Pronto.");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        Button refreshLocalBtn = new Button("↻  Atualizar tarefas locais");
        refreshLocalBtn.getStyleClass().add("secondary-button");
        refreshLocalBtn.setOnAction(e -> loadLocalTasks());

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
        confirm.setContentText("Os tokens de acesso serão removidos localmente.");
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
    }

    // ── Carregar dados ───────────────────────────────────────────────────────

    private void loadGoogleTaskLists() {
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }
        setStatus("Carregando listas do Google Tasks...");
        runBackground(
            () -> gTasks.listTaskLists(),
            lists -> {
                listCombo.getItems().setAll(lists);
                if (!lists.isEmpty()) {
                    listCombo.setValue(lists.get(0));
                    loadGoogleTasks();
                }
                setStatus("Listas carregadas: " + lists.size());
            },
            err -> showError("Erro ao carregar listas", err.getMessage())
        );
    }

    private void loadGoogleTasks() {
        TaskList selected = listCombo.getValue();
        if (selected == null) return;
        setStatus("Carregando tarefas de '" + selected.title() + "'...");
        runBackground(
            () -> gTasks.listTasks(selected.id(), true),
            tasks -> {
                gTaskItems.setAll(tasks);
                setStatus("Tarefas carregadas: " + tasks.size()
                    + "  (lista: " + selected.title() + ")");
            },
            err -> showError("Erro ao carregar tarefas", err.getMessage())
        );
    }

    private void loadLocalTasks() {
        List<com.pessoal.agenda.model.Task> tasks =
                AppContextHolder.get().taskRepository().findOpenTasks();
        localItems.setAll(tasks);
    }

    // ── Importar / Exportar ──────────────────────────────────────────────────

    private void importSelected() {
        GTask selected = gTaskList.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Selecione uma tarefa do Google para importar."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }
        importTask(selected);
    }

    private void importAllPending() {
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }
        List<GTask> pending = gTaskItems.stream().filter(t -> !t.completed()).toList();
        if (pending.isEmpty()) { setStatus("Nenhuma tarefa pendente para importar."); return; }
        for (GTask t : pending) importTask(t);
        setStatus("✓ " + pending.size() + " tarefa(s) importada(s) para o local.");
        if (onSyncCallback != null) onSyncCallback.run();
        loadLocalTasks();
    }

    private void importTask(GTask g) {
        LocalDate due = g.dueDate() != null ? g.dueDate() : LocalDate.now();
        String notes = g.notes() != null ? g.notes() : "";
        String cat   = "Google Tasks";
        AppContextHolder.get().taskRepository().save(
                g.title(), notes.isBlank() ? null : notes,
                due, cat);
        setStatus("Tarefa importada: " + g.title());
        loadLocalTasks();
        if (onSyncCallback != null) onSyncCallback.run();
    }

    private void exportSelected() {
        com.pessoal.agenda.model.Task selected =
                localList.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Selecione uma tarefa local para exportar."); return; }
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }

        TaskList targetList = listCombo.getValue();
        if (targetList == null) {
            setStatus("Selecione uma lista do Google Tasks primeiro.");
            return;
        }
        setStatus("Exportando '" + selected.title() + "'...");
        runBackground(
            () -> {
                gTasks.createTask(targetList.id(), selected.title(),
                        selected.notes(), selected.dueDate());
                return null;
            },
            result -> {
                setStatus("✓ Exportada: " + selected.title());
                loadGoogleTasks();
            },
            err -> showError("Erro ao exportar", err.getMessage())
        );
    }

    private void exportAllPending() {
        if (!auth.isAuthorized()) { setStatus("Conecte ao Google primeiro."); return; }

        TaskList targetList = listCombo.getValue();
        if (targetList == null) {
            setStatus("Selecione uma lista do Google Tasks primeiro.");
            return;
        }
        List<com.pessoal.agenda.model.Task> pending =
                localItems.stream().filter(t -> !t.done()).toList();
        if (pending.isEmpty()) { setStatus("Nenhuma tarefa pendente para exportar."); return; }

        setStatus("Exportando " + pending.size() + " tarefas...");
        runBackground(
            () -> {
                int count = 0;
                for (var t : pending) {
                    gTasks.createTask(targetList.id(), t.title(), t.notes(), t.dueDate());
                    count++;
                }
                return count;
            },
            count -> {
                setStatus("✓ " + count + " tarefa(s) exportada(s) para '" + targetList.title() + "'");
                loadGoogleTasks();
            },
            err -> showError("Erro ao exportar", err.getMessage())
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void showError(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(msg != null ? msg : "Erro desconhecido.");
            alert.showAndWait();
        });
    }

    /**
     * Executa operação em thread de background e trata resultado na FX thread.
     * @param <T> tipo do resultado
     */
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




