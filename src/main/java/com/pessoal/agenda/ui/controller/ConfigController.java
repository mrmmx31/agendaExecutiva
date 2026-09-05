package com.pessoal.agenda.ui.controller;
import com.pessoal.agenda.ui.view.Dialogs;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.Category;
import com.pessoal.agenda.model.CategoryDomain;
import com.pessoal.agenda.model.QuickCaptureShortcut;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.service.QuickCapturePreferences;
import com.pessoal.agenda.service.LocalMetricsService;
import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleSyncErrorPresenter;
import com.pessoal.agenda.service.SigningKeyDriveBackupService;
import com.pessoal.agenda.ui.view.GoogleAccountConnectionFlow;
import com.pessoal.agenda.ui.view.GoogleTasksSyncWindow;
import com.pessoal.agenda.ui.view.MobilePairingWindow;
import com.pessoal.agenda.ui.view.ThemeManager;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Controller da aba de Configurações.
 * Gerencia as categorias de todos os domínios do sistema e o tema visual.
 */
public class ConfigController {

    private final SharedContext ctx;
    private final Runnable notificationSettingsChanged;
    private final Runnable quickCaptureShortcutChanged;
    private final PendencyNotificationService notificationService;
    private final LocalMetricsService localMetricsService;
    private final Runnable localMetricsChanged;

    public ConfigController(SharedContext ctx) {
        this(ctx, () -> {}, () -> {});
    }

    public ConfigController(SharedContext ctx, Runnable notificationSettingsChanged) {
        this(ctx, notificationSettingsChanged, () -> {});
    }

    public ConfigController(SharedContext ctx, Runnable notificationSettingsChanged,
                            Runnable quickCaptureShortcutChanged) {
        this(ctx, notificationSettingsChanged, quickCaptureShortcutChanged,
                PendencyNotificationService.getInstance(),
                AppContextHolder.get().localMetricsService(), ctx::triggerDashboardRefresh);
    }

    public ConfigController(SharedContext ctx, Runnable notificationSettingsChanged,
                            Runnable quickCaptureShortcutChanged,
                            PendencyNotificationService notificationService) {
        this(ctx, notificationSettingsChanged, quickCaptureShortcutChanged,
                notificationService, null, () -> {});
    }

    ConfigController(SharedContext ctx, Runnable notificationSettingsChanged,
                     Runnable quickCaptureShortcutChanged,
                     PendencyNotificationService notificationService,
                     LocalMetricsService localMetricsService,
                     Runnable localMetricsChanged) {
        this.ctx = ctx;
        this.notificationSettingsChanged = notificationSettingsChanged;
        this.quickCaptureShortcutChanged = quickCaptureShortcutChanged;
        this.notificationService = notificationService;
        this.localMetricsService = localMetricsService;
        this.localMetricsChanged = localMetricsChanged;
    }

    public Tab buildTab() {
        Tab tab = new Tab("⚙ Configurações");
        tab.setClosable(false);

        VBox taskSection      = buildCategorySection("Categorias de Tarefas",
                "config-task",       CategoryDomain.TASK,       ctx.taskCatList);
        VBox checklistSection = buildCategorySection("Categorias de Protocolos",
                "config-checklist",  CategoryDomain.CHECKLIST,  ctx.checklistCatList);
        VBox studySection     = buildCategorySection("Categorias de Estudos",
                "config-study",      CategoryDomain.STUDY,      ctx.studyCatList);
        VBox studyTypeSection = buildCategorySection("Tipos de Estudo",
                "config-study-type", CategoryDomain.STUDY_TYPE, ctx.studyTypeCatList);
        VBox ideaSection      = buildCategorySection("Categorias de Projetos e Ideias",
                "config-idea",       CategoryDomain.IDEA,       ctx.ideaCatList);

        HBox row1 = new HBox(14, taskSection, checklistSection);
        HBox row2 = new HBox(14, studySection, studyTypeSection);
        HBox row3 = new HBox(14, ideaSection);
        HBox.setHgrow(taskSection,       Priority.ALWAYS);
        HBox.setHgrow(checklistSection,  Priority.ALWAYS);
        HBox.setHgrow(studySection,      Priority.ALWAYS);
        HBox.setHgrow(studyTypeSection,  Priority.ALWAYS);
        HBox.setHgrow(ideaSection,       Priority.ALWAYS);

        Label header = new Label("Configurações da Agenda");
        header.getStyleClass().add("page-title");
        header.setWrapText(true);

        VBox general = new VBox(14, buildThemeSection(), buildNotificationSection(),
                buildQuickCaptureSection());
        if (localMetricsService != null) general.getChildren().add(buildLocalMetricsSection());

        VBox integrations = new VBox(14, buildGoogleTasksSection(),
                buildSigningKeyBackupSection(), buildMobilePairingSection());
        VBox categories = new VBox(14, row1, row2, row3);

        TabPane sections = new TabPane(
                settingsTab("Geral", general),
                settingsTab("Integrações", integrations),
                settingsTab("Categorias", categories));
        sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(sections, Priority.ALWAYS);

        VBox content = new VBox(10, header, sections);
        content.setPadding(new Insets(16));
        tab.setContent(content);
        return tab;
    }

    private VBox buildSigningKeyBackupSection() {
        GoogleAuthService auth = GoogleAuthService.getInstance();
        SigningKeyDriveBackupService backupService = new SigningKeyDriveBackupService();

        Label title = new Label("Backup da chave de assinatura");
        title.getStyleClass().add("section-title");
        Label localState = new Label();
        Label permissionState = new Label();
        Label remoteState = new Label("Não verificado");
        remoteState.getStyleClass().add("t-muted");

        GridPane stateGrid = new GridPane();
        stateGrid.setHgap(18);
        stateGrid.setVgap(8);
        addSettingRow(stateGrid, 0, "Chave local", localState);
        addSettingRow(stateGrid, 1, "Permissão Drive", permissionState);
        addSettingRow(stateGrid, 2, "Backup remoto", remoteState);

        Button authorize = new Button("Autorizar Google Drive");
        authorize.getStyleClass().add("primary-button");
        Button cancelAuthorization = new Button("Cancelar autorização");
        cancelAuthorization.getStyleClass().add("danger-button");
        cancelAuthorization.setVisible(false);
        cancelAuthorization.setManaged(false);
        Button upload = new Button("Criar ou atualizar backup");
        upload.getStyleClass().add("secondary-button");
        Button restoreTest = new Button("Testar restauração");
        restoreTest.getStyleClass().add("secondary-button");
        Button refresh = new Button("↻");
        refresh.getStyleClass().add("secondary-button");
        refresh.setTooltip(new Tooltip("Atualizar estado do backup"));

        Control[] controls = {authorize, upload, restoreTest, refresh};
        boolean[] busy = {false};
        boolean[] remoteAvailable = {false};
        AtomicReference<GoogleAccountConnectionFlow.ConnectionAttempt> attempt =
                new AtomicReference<>();

        Runnable refreshLocalState = () -> {
            boolean local = backupService.localKeyExists();
            boolean drive = auth.hasDriveAppDataScope();
            localState.setText(local ? "Disponível" : "Ausente");
            permissionState.setText(drive ? "Autorizada" : "Pendente");
            setSemanticState(localState, local, false);
            setSemanticState(permissionState, drive, false);
            authorize.setDisable(busy[0] || drive || !auth.hasValidCredentials());
            upload.setDisable(busy[0] || !drive || !local);
            restoreTest.setDisable(busy[0] || !drive || !remoteAvailable[0]);
            refresh.setDisable(busy[0] || !drive);
        };
        Consumer<Boolean> setBusy = value -> {
            busy[0] = value;
            for (Control control : controls) control.setDisable(value);
            cancelAuthorization.setVisible(value && attempt.get() != null);
            cancelAuthorization.setManaged(value && attempt.get() != null);
            if (!value) refreshLocalState.run();
        };

        Runnable refreshRemote = () -> runGoogleOperation(
                "Verificando backup no Google Drive...", setBusy,
                backupService::findBackup,
                found -> {
                    remoteAvailable[0] = found.isPresent();
                    remoteState.setText(found.map(item -> "Disponível · "
                                    + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                    .withZone(ZoneId.systemDefault()).format(item.modifiedAt()))
                            .orElse("Ainda não criado"));
                    remoteState.getStyleClass().removeAll("t-muted", "t-success", "t-danger");
                    remoteState.getStyleClass().add(found.isPresent() ? "t-success" : "t-muted");
                    refreshLocalState.run();
                });

        authorize.setOnAction(event -> {
            attempt.set(GoogleAccountConnectionFlow.start(auth,
                    Set.of(GoogleAuthService.TASKS_SCOPE, GoogleAuthService.DRIVE_APPDATA_SCOPE),
                    "Autorizar backup no Google Drive", ctx::setStatus, setBusy,
                    () -> {
                        attempt.set(null);
                        ctx.setStatus("Google Drive autorizado para o backup privado.");
                        refreshLocalState.run();
                        refreshRemote.run();
                    },
                    () -> {
                        attempt.set(null);
                        ctx.setStatus("Autorização do Google Drive cancelada.");
                        refreshLocalState.run();
                    },
                    error -> {
                        attempt.set(null);
                        showGoogleOperationError("Erro de autorização do Drive", error);
                        refreshLocalState.run();
                    }));
            cancelAuthorization.setVisible(busy[0] && attempt.get() != null);
            cancelAuthorization.setManaged(busy[0] && attempt.get() != null);
        });
        cancelAuthorization.setOnAction(event -> {
            GoogleAccountConnectionFlow.ConnectionAttempt current = attempt.get();
            if (current != null) current.cancel();
        });
        upload.setOnAction(event -> promptBackupPasswords(true).ifPresent(passwords ->
                runGoogleOperation("Cifrando e enviando a chave...", setBusy,
                        () -> {
                            try {
                                return backupService.createOrUpdate(
                                        passwords.keyPassword(), passwords.recoveryPassword());
                            } finally {
                                passwords.clear();
                            }
                        }, result -> {
                            remoteAvailable[0] = true;
                            remoteState.setText("Disponível · restauração ainda não testada");
                            remoteState.getStyleClass().removeAll("t-muted", "t-danger");
                            remoteState.getStyleClass().add("t-success");
                            ctx.setStatus("Backup cifrado salvo no Google Drive.");
                            refreshLocalState.run();
                        })));
        restoreTest.setOnAction(event -> promptBackupPasswords(false).ifPresent(passwords ->
                runGoogleOperation("Baixando e validando o backup...", setBusy,
                        () -> {
                            try {
                                return backupService.testRestore(
                                        passwords.keyPassword(), passwords.recoveryPassword());
                            } finally {
                                passwords.clear();
                            }
                        }, result -> {
                            remoteState.setText("Disponível · restauração validada");
                            remoteState.getStyleClass().removeAll("t-muted", "t-danger");
                            remoteState.getStyleClass().add("t-success");
                            ctx.setStatus("Teste de restauração concluído sem substituir a chave local.");
                            Dialogs.info("Restauração validada",
                                    "O backup foi baixado, decifrado e validado. A chave local não foi alterada.");
                        })));
        refresh.setOnAction(event -> refreshRemote.run());

        FlowPane buttons = new FlowPane(10, 8,
                authorize, cancelAuthorization, upload, restoreTest, refresh);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setMaxWidth(Double.MAX_VALUE);
        VBox section = new VBox(12, title, stateGrid, buttons);
        section.setId("config-signing-key-backup");
        section.getStyleClass().add("config-section");
        section.setPadding(new Insets(12, 14, 12, 14));
        refreshLocalState.run();
        return section;
    }

    private Optional<BackupPasswords> promptBackupPasswords(boolean confirmRecovery) {
        Dialog<BackupPasswords> dialog = new Dialog<>();
        dialog.setTitle(confirmRecovery ? "Criar backup cifrado" : "Testar restauração");
        dialog.setHeaderText(confirmRecovery
                ? "Informe as duas senhas para proteger a chave"
                : "Informe as senhas para validar o backup");
        ButtonType proceed = new ButtonType(confirmRecovery ? "Criar backup" : "Validar",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(proceed, ButtonType.CANCEL);
        PasswordField keyPassword = new PasswordField();
        keyPassword.setPromptText("Senha atual do PKCS#12");
        PasswordField recovery = new PasswordField();
        recovery.setPromptText("Senha de recuperação (mínimo 16 caracteres)");
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText("Repita a senha de recuperação");
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, new Label("Chave local"), keyPassword);
        fields.addRow(1, new Label("Recuperação"), recovery);
        if (confirmRecovery) fields.addRow(2, new Label("Confirmação"), confirmation);
        dialog.getDialogPane().setContent(fields);
        javafx.scene.Node proceedButton = dialog.getDialogPane().lookupButton(proceed);
        Runnable validate = () -> proceedButton.setDisable(recovery.getText().length() < 16
                || (confirmRecovery && !recovery.getText().equals(confirmation.getText())));
        recovery.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        confirmation.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        validate.run();
        dialog.setResultConverter(button -> button == proceed
                ? new BackupPasswords(keyPassword.getText().toCharArray(),
                        recovery.getText().toCharArray()) : null);
        return dialog.showAndWait();
    }

    private <T> void runGoogleOperation(String status,
                                        Consumer<Boolean> busy,
                                        ThrowingSupplier<T> action,
                                        Consumer<T> success) {
        if (!GoogleAccountConnectionFlow.tryStartGoogleOperation()) {
            ctx.setStatus("Aguarde a operação Google em andamento terminar.");
            return;
        }
        busy.accept(true);
        ctx.setStatus(status);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.get(); }
        };
        task.setOnSucceeded(event -> {
            GoogleAccountConnectionFlow.finishGoogleOperation();
            busy.accept(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            GoogleAccountConnectionFlow.finishGoogleOperation();
            busy.accept(false);
            showGoogleOperationError("Erro no backup do Drive", task.getException());
        });
        Thread thread = new Thread(task, "google-drive-backup");
        thread.setDaemon(true);
        thread.start();
    }

    private void showGoogleOperationError(String title, Throwable error) {
        String message = GoogleSyncErrorPresenter.userMessage(error);
        ctx.setStatus(message);
        Dialogs.error(title, message);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record BackupPasswords(char[] keyPassword, char[] recoveryPassword) {
        void clear() {
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(recoveryPassword, '\0');
        }
    }

    private Tab settingsTab(String title, VBox content) {
        content.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        Tab tab = new Tab(title, scroll);
        tab.setClosable(false);
        return tab;
    }

    VBox buildGoogleTasksSection() {
        GoogleAuthService auth = GoogleAuthService.getInstance();
        return buildGoogleTasksSection(new GoogleTasksSettingsActions() {
            @Override public boolean hasValidCredentials() { return auth.hasValidCredentials(); }
            @Override public boolean isAuthorized() { return auth.isAuthorized(); }
            @Override public boolean isOperationRunning() {
                return GoogleAccountConnectionFlow.isGoogleOperationRunning();
            }
            @Override public int mappingCount() {
                return AppContextHolder.get().googleTasksMappingRepository().count();
            }
            @Override public GoogleAccountConnectionFlow.ConnectionAttempt connect(
                    Consumer<Boolean> busy, Runnable stateChanged) {
                return GoogleAccountConnectionFlow.start(
                        auth, ctx::setStatus, busy,
                        () -> {
                            ctx.setStatus("Conectado ao Google Tasks.");
                            stateChanged.run();
                        },
                        () -> {
                            ctx.setStatus("Conexão com o Google cancelada. Você pode tentar novamente.");
                            stateChanged.run();
                        },
                        error -> {
                            ctx.setStatus(GoogleSyncErrorPresenter.userMessage(error));
                            Dialogs.error("Erro de autorização",
                                    GoogleSyncErrorPresenter.userMessage(error));
                            stateChanged.run();
                        });
            }
            @Override public void disconnect() throws Exception { auth.revoke(); }
            @Override public void clearMappings() {
                AppContextHolder.get().googleTasksMappingRepository().deleteAll();
            }
            @Override public void openSync() {
                new GoogleTasksSyncWindow(ctx::triggerDashboardRefresh).show();
            }
        });
    }

    VBox buildGoogleTasksSection(GoogleTasksSettingsActions actions) {
        Label title = new Label("Google Tasks");
        title.getStyleClass().add("section-title");

        Label connectionValue = new Label();
        connectionValue.setId("google-settings-connection");
        Label credentialsValue = new Label();
        credentialsValue.setId("google-settings-credentials");
        Label mappingsValue = new Label();
        mappingsValue.setId("google-settings-mappings");

        GridPane stateGrid = new GridPane();
        stateGrid.setHgap(18);
        stateGrid.setVgap(8);
        addSettingRow(stateGrid, 0, "Conexão", connectionValue);
        addSettingRow(stateGrid, 1, "Credenciais OAuth", credentialsValue);
        addSettingRow(stateGrid, 2, "Permissão", new Label("Google Tasks"));
        addSettingRow(stateGrid, 3, "Sincronização", new Label("Manual, com prévia"));
        addSettingRow(stateGrid, 4, "Vínculos locais", mappingsValue);

        Button connect = new Button("Conectar conta");
        connect.setId("google-settings-connect");
        connect.getStyleClass().add("primary-button");
        Button disconnect = new Button("Desconectar");
        disconnect.setId("google-settings-disconnect");
        disconnect.getStyleClass().add("danger-button");
        Button cancelConnection = new Button("Cancelar conexão");
        cancelConnection.setId("google-settings-cancel-connect");
        cancelConnection.getStyleClass().add("danger-button");
        cancelConnection.setVisible(false);
        cancelConnection.setManaged(false);
        Button openSync = new Button("Abrir sincronização");
        openSync.setId("google-settings-open-sync");
        openSync.getStyleClass().add("secondary-button");
        Button clearMappings = new Button("Limpar vínculos locais");
        clearMappings.setId("google-settings-clear-mappings");
        clearMappings.getStyleClass().add("secondary-button");
        Button refresh = new Button("↻");
        refresh.setId("google-settings-refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setTooltip(new Tooltip("Atualizar estado da integração"));

        Control[] controls = {connect, disconnect, openSync, clearMappings, refresh};
        boolean[] busy = {false};
        AtomicReference<GoogleAccountConnectionFlow.ConnectionAttempt> connectionAttempt =
                new AtomicReference<>();
        Runnable refreshState = () -> {
            boolean credentialsReady = actions.hasValidCredentials();
            boolean connected = actions.isAuthorized();
            boolean operationRunning = busy[0] || actions.isOperationRunning();
            int mappings = actions.mappingCount();

            connectionValue.setText(connected ? "Conectado" : "Desconectado");
            credentialsValue.setText(credentialsReady ? "Prontas" : "Ausentes ou inválidas");
            mappingsValue.setText(mappings + (mappings == 1 ? " vínculo" : " vínculos"));

            setSemanticState(connectionValue, connected, true);
            setSemanticState(credentialsValue, credentialsReady, false);
            connect.setDisable(operationRunning || connected || !credentialsReady);
            disconnect.setDisable(operationRunning || !connected);
            openSync.setDisable(false);
            clearMappings.setDisable(operationRunning || mappings == 0);
            refresh.setDisable(busy[0]);
        };
        Consumer<Boolean> setBusy = value -> {
            busy[0] = value;
            for (Control control : controls) control.setDisable(value);
            cancelConnection.setVisible(value);
            cancelConnection.setManaged(value);
            cancelConnection.setDisable(false);
            if (!value) refreshState.run();
        };

        connect.setOnAction(event -> connectionAttempt.set(
                actions.connect(setBusy, refreshState)));
        cancelConnection.setOnAction(event -> {
            GoogleAccountConnectionFlow.ConnectionAttempt attempt = connectionAttempt.get();
            if (attempt == null) return;
            cancelConnection.setDisable(true);
            ctx.setStatus("Cancelando conexão com o Google...");
            attempt.cancel();
        });
        disconnect.setOnAction(event -> {
            if (actions.isOperationRunning()) {
                ctx.setStatus("Aguarde a operação Google em andamento terminar.");
                refreshState.run();
                return;
            }
            Dialogs.confirm(
                        "Desconectar Google Tasks",
                        "Desconectar a conta Google?",
                        "Os tokens locais serão removidos. Os vínculos de sincronização serão preservados.")
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
                    try {
                        actions.disconnect();
                        ctx.setStatus("Conta Google desconectada.");
                        refreshState.run();
                    } catch (Exception error) {
                        Dialogs.error("Erro ao desconectar", "Não foi possível remover os tokens locais.");
                    }
                });
        });
        clearMappings.setOnAction(event -> {
            if (actions.isOperationRunning()) {
                ctx.setStatus("Aguarde a operação Google em andamento terminar.");
                refreshState.run();
                return;
            }
            Dialogs.confirm(
                        "Limpar vínculos do Google Tasks",
                        "Remover todos os vínculos locais de sincronização?",
                        "Nenhuma tarefa será apagada. Use somente ao trocar de conta ou reiniciar a integração; "
                                + "uma nova sincronização com os mesmos dados pode criar duplicatas.")
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
                    actions.clearMappings();
                    ctx.setStatus("Vínculos locais do Google Tasks removidos.");
                    refreshState.run();
                });
        });
        openSync.setOnAction(event -> actions.openSync());
        refresh.setOnAction(event -> refreshState.run());

        FlowPane actionsBar = new FlowPane(10, 8,
                connect, cancelConnection, disconnect, openSync, clearMappings, refresh);
        actionsBar.setAlignment(Pos.CENTER_LEFT);
        actionsBar.setMaxWidth(Double.MAX_VALUE);

        VBox section = new VBox(12, title, stateGrid, actionsBar);
        section.setId("config-google-tasks");
        section.getStyleClass().addAll("config-section", "config-google-tasks");
        section.setPadding(new Insets(12, 14, 12, 14));
        refreshState.run();
        return section;
    }

    private VBox buildMobilePairingSection() {
        return buildMobilePairingSection(new MobilePairingActions() {
            @Override
            public List<String> activeDeviceNames() {
                return AppContextHolder.get().desktopSyncRepository().listDevices().stream()
                        .filter(device -> device.status().equals("ACTIVE"))
                        .map(device -> device.deviceName())
                        .toList();
            }

            @Override
            public void openManager(Runnable stateChanged) {
                new MobilePairingWindow(stateChanged).show();
            }
        });
    }

    VBox buildMobilePairingSection(MobilePairingActions actions) {
        Label title = new Label("Aplicativo móvel");
        title.getStyleClass().add("section-title");
        Label state = new Label();
        state.setId("mobile-pairing-state");
        state.setWrapText(true);

        Runnable refreshState = () -> {
            List<String> names = actions.activeDeviceNames();
            state.getStyleClass().removeAll("t-success", "t-muted");
            if (names.isEmpty()) {
                state.setText("Nenhum dispositivo conectado");
                state.getStyleClass().add("t-muted");
            } else if (names.size() == 1) {
                state.setText("Conectado: " + names.getFirst());
                state.getStyleClass().add("t-success");
            } else {
                state.setText(names.size() + " dispositivos conectados: "
                        + String.join(", ", names));
                state.getStyleClass().add("t-success");
            }
        };

        Button manage = new Button("Gerenciar dispositivos");
        manage.setId("mobile-pairing-manage");
        manage.getStyleClass().add("secondary-button");
        manage.setOnAction(event -> actions.openManager(refreshState));

        Button refresh = new Button("↻");
        refresh.setId("mobile-pairing-refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setTooltip(new Tooltip("Atualizar estado dos dispositivos"));
        refresh.setOnAction(event -> refreshState.run());

        FlowPane actionsBar = new FlowPane(10, 8, manage, refresh);
        actionsBar.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, title, state, actionsBar);
        section.setId("config-mobile-pairing");
        section.getStyleClass().add("config-section");
        section.setPadding(new Insets(12, 14, 12, 14));
        refreshState.run();
        return section;
    }

    private void setSemanticState(Label label, boolean positive, boolean bold) {
        label.getStyleClass().removeAll("t-success", "t-danger");
        label.getStyleClass().add(positive ? "t-success" : "t-danger");
        label.setStyle(bold ? "-fx-font-weight: 700;" : "");
    }

    private void addSettingRow(GridPane grid, int row, String name, Label value) {
        Label key = new Label(name);
        key.getStyleClass().add("form-label");
        grid.add(key, 0, row);
        grid.add(value, 1, row);
    }

    interface GoogleTasksSettingsActions {
        boolean hasValidCredentials();
        boolean isAuthorized();
        boolean isOperationRunning();
        int mappingCount();
        GoogleAccountConnectionFlow.ConnectionAttempt connect(
                Consumer<Boolean> busy, Runnable stateChanged);
        void disconnect() throws Exception;
        void clearMappings();
        void openSync();
    }

    interface MobilePairingActions {
        List<String> activeDeviceNames();
        void openManager(Runnable stateChanged);
    }

    VBox buildLocalMetricsSection() {
        if (localMetricsService == null) {
            throw new IllegalStateException("Serviço de métricas locais não configurado");
        }
        Label title = new Label("Métricas locais de uso");
        title.getStyleClass().add("section-title");
        Label privacy = new Label(
                "Somente tempos, contagens e horários ficam armazenados neste dispositivo.");
        privacy.getStyleClass().add("t-muted");
        privacy.setWrapText(true);

        CheckBox enabled = new CheckBox("Mostrar e registrar métricas locais");
        enabled.setId("local-metrics-enabled");
        enabled.setSelected(localMetricsService.isEnabled());
        Button clear = new Button("Apagar histórico local");
        clear.setId("local-metrics-clear");
        clear.getStyleClass().add("danger-button");

        enabled.setOnAction(event -> {
            localMetricsService.setEnabled(enabled.isSelected());
            localMetricsChanged.run();
            ctx.setStatus(enabled.isSelected()
                    ? "Métricas locais ativadas."
                    : "Métricas locais desativadas. Nenhum novo registro será criado.");
        });
        clear.setOnAction(event -> Dialogs.confirm(
                        "Apagar métricas locais",
                        "Apagar todo o histórico local de métricas?",
                        "A configuração de ativação será mantida.")
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
                    localMetricsService.clear();
                    localMetricsChanged.run();
                    ctx.setStatus("Histórico local de métricas apagado.");
                }));

        HBox controls = new HBox(12, enabled, clear);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(10, title, privacy, controls);
        section.setId("config-local-metrics");
        section.getStyleClass().add("config-section");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    VBox buildNotificationSection() {
        PendencyNotificationService service = notificationService;

        Label titleLabel = new Label("Lembretes e estímulos");
        titleLabel.getStyleClass().add("section-title");

        CheckBox enabled = new CheckBox("Lembretes periódicos");
        enabled.setId("notifications-enabled");
        enabled.setSelected(service.isEnabled());
        CheckBox sound = new CheckBox("Som");
        sound.setId("notifications-sound");
        sound.setSelected(service.isSoundEnabled());
        CheckBox animation = new CheckBox("Animar indicador de pendências");
        animation.setId("notifications-animation");
        animation.setSelected(service.isBadgeAnimationEnabled());

        Button testSound = new Button("Testar som");
        testSound.setId("notifications-test-sound");
        testSound.getStyleClass().add("secondary-button");

        ComboBox<Integer> interval = new ComboBox<>();
        interval.setId("notifications-interval");
        interval.getItems().addAll(5, 15, 30, 60);
        interval.setValue(service.getIntervalMinutes());
        interval.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer value) {
                return value == null ? "" : value + " minutos";
            }
            @Override public Integer fromString(String value) {
                return Integer.valueOf(value.replaceAll("\\D", ""));
            }
        });

        CheckBox quietHours = new CheckBox("Horário silencioso");
        quietHours.setId("notifications-quiet-enabled");
        quietHours.setSelected(service.isQuietHoursEnabled());
        ComboBox<LocalTime> quietStart = buildTimeSelector(
                "notifications-quiet-start", service.getQuietHoursStart());
        ComboBox<LocalTime> quietEnd = buildTimeSelector(
                "notifications-quiet-end", service.getQuietHoursEnd());

        Runnable syncDisabledState = () -> {
            boolean remindersDisabled = !enabled.isSelected();
            sound.setDisable(remindersDisabled);
            animation.setDisable(remindersDisabled);
            interval.setDisable(remindersDisabled);
            testSound.setDisable(remindersDisabled || !sound.isSelected());
            quietHours.setDisable(remindersDisabled);
            quietStart.setDisable(remindersDisabled || !quietHours.isSelected());
            quietEnd.setDisable(remindersDisabled || !quietHours.isSelected());
        };
        syncDisabledState.run();

        enabled.setOnAction(e -> {
            service.setEnabled(enabled.isSelected());
            syncDisabledState.run();
            notificationSettingsChanged.run();
            ctx.setStatus(enabled.isSelected()
                    ? "Lembretes periódicos ativados."
                    : "Lembretes desligados. Contagens continuam visíveis.");
        });
        sound.setOnAction(e -> {
            service.setSoundEnabled(sound.isSelected());
            syncDisabledState.run();
            ctx.setStatus(sound.isSelected() ? "Som dos lembretes ativado." : "Som dos lembretes desativado.");
        });
        animation.setOnAction(e -> {
            service.setBadgeAnimationEnabled(animation.isSelected());
            notificationSettingsChanged.run();
            ctx.setStatus(animation.isSelected() ? "Animação do indicador ativada." : "Indicador mantido estático.");
        });
        interval.setOnAction(e -> {
            Integer value = interval.getValue();
            if (value != null) {
                service.setIntervalMinutes(value);
                ctx.setStatus("Intervalo dos lembretes: " + value + " minutos.");
            }
        });
        testSound.setOnAction(e -> ctx.setStatus(soundTestStatus(service.testSound())));
        quietHours.setOnAction(e -> {
            service.setQuietHoursEnabled(quietHours.isSelected());
            syncDisabledState.run();
            ctx.setStatus(quietHours.isSelected()
                    ? "Horário silencioso ativado."
                    : "Horário silencioso desativado.");
        });
        Runnable saveQuietHours = () -> {
            service.setQuietHours(quietStart.getValue(), quietEnd.getValue());
            ctx.setStatus("Horário silencioso: " + formatTime(quietStart.getValue())
                    + " até " + formatTime(quietEnd.getValue()) + ".");
        };
        quietStart.setOnAction(e -> saveQuietHours.run());
        quietEnd.setOnAction(e -> saveQuietHours.run());

        Label intervalLabel = new Label("Intervalo");
        HBox primaryControls = new HBox(
                18, enabled, sound, animation, intervalLabel, interval, testSound);
        primaryControls.setAlignment(Pos.CENTER_LEFT);
        Label quietStartLabel = new Label("Das");
        Label quietEndLabel = new Label("até");
        HBox quietControls = new HBox(
                10, quietHours, quietStartLabel, quietStart, quietEndLabel, quietEnd);
        quietControls.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, titleLabel, primaryControls, quietControls);
        section.getStyleClass().addAll("config-section", "config-notifications");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    private ComboBox<LocalTime> buildTimeSelector(String id, LocalTime selected) {
        ComboBox<LocalTime> selector = new ComboBox<>();
        selector.setId(id);
        for (int hour = 0; hour < 24; hour++) {
            selector.getItems().add(LocalTime.of(hour, 0));
            selector.getItems().add(LocalTime.of(hour, 30));
        }
        selector.setValue(selected);
        selector.setPrefWidth(92);
        selector.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalTime value) {
                return value == null ? "" : formatTime(value);
            }
            @Override public LocalTime fromString(String value) {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
            }
        });
        return selector;
    }

    private String formatTime(LocalTime value) {
        return value.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String soundTestStatus(PendencyNotificationService.SoundTestResult result) {
        return switch (result) {
            case PLAYED -> "Som de teste reproduzido uma vez.";
            case ALREADY_PLAYING -> "O som anterior ainda está em reprodução.";
            case REMINDERS_DISABLED -> "Teste indisponível: lembretes estão desligados.";
            case SOUND_DISABLED -> "Teste indisponível: ative o som dos lembretes.";
            case SNOOZED -> "Som não reproduzido: lembretes estão pausados.";
            case QUIET_HOURS -> "Som não reproduzido: horário silencioso ativo.";
        };
    }

    private VBox buildQuickCaptureSection() {
        QuickCapturePreferences preferences = AppContextHolder.get().quickCapturePreferences();

        Label titleLabel = new Label("Captura rápida");
        titleLabel.getStyleClass().add("section-title");

        Label shortcutLabel = new Label("Atalho");
        ComboBox<QuickCaptureShortcut> shortcut = new ComboBox<>();
        shortcut.setId("quick-capture-shortcut");
        shortcut.getItems().setAll(QuickCaptureShortcut.values());
        shortcut.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(QuickCaptureShortcut value) {
                return value == null ? "" : value.label();
            }
            @Override public QuickCaptureShortcut fromString(String value) {
                return shortcut.getItems().stream()
                        .filter(option -> option.label().equals(value))
                        .findFirst().orElse(QuickCapturePreferences.DEFAULT_SHORTCUT);
            }
        });
        shortcut.setValue(preferences.getShortcut());
        shortcut.setOnAction(event -> {
            QuickCaptureShortcut selected = shortcut.getValue();
            if (selected == null) return;
            preferences.setShortcut(selected);
            quickCaptureShortcutChanged.run();
            ctx.setStatus(selected == QuickCaptureShortcut.DISABLED
                    ? "Atalho da captura rápida desativado."
                    : "Atalho da captura rápida: " + selected.label() + ".");
        });

        Button restore = new Button("Restaurar padrão");
        restore.setId("quick-capture-shortcut-restore");
        restore.getStyleClass().add("secondary-button");
        restore.setOnAction(event -> {
            preferences.restoreDefault();
            shortcut.setValue(QuickCapturePreferences.DEFAULT_SHORTCUT);
        });

        HBox controls = new HBox(10, shortcutLabel, shortcut, restore);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(10, titleLabel, controls);
        section.getStyleClass().addAll("config-section", "config-quick-capture");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    /** Seção de seleção de tema visual */
    private VBox buildThemeSection() {
        Label titleLabel = new Label("🎨  Aparência / Tema Visual");
        titleLabel.getStyleClass().add("section-title");

        Label desc = new Label(
                "Escolha o tema visual que será aplicado em toda a aplicação. " +
                "A preferência é salva automaticamente.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11.5px;");

        ToggleGroup group = new ToggleGroup();

        ThemeManager.Theme current = ThemeManager.getInstance().getTheme();

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_LEFT);

        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            ToggleButton btn = new ToggleButton(theme.label);
            btn.setToggleGroup(group);
            btn.getStyleClass().add("filter-toggle");
            btn.setSelected(theme == current);
            btn.setUserData(theme);
            btn.setOnAction(e -> {
                if (btn.isSelected()) {
                    ThemeManager.getInstance().setTheme(theme);
                    ctx.setStatus("Tema alterado para: " + theme.label);
                }
            });
            btns.getChildren().add(btn);
        }

        // Garante que sempre haja um botão selecionado
        group.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (nw == null && old != null) old.setSelected(true);
        });

        VBox section = new VBox(10, titleLabel, desc, btns);
        section.getStyleClass().addAll("config-section", "config-theme");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    private VBox buildCategorySection(String title, String styleClass,
                                      CategoryDomain domain,
                                      ObservableList<Category> catList) {
        ListView<Category> listView = new ListView<>(catList);
        listView.getStyleClass().add("clean-list");
        listView.setPrefHeight(180);

        TextField nameField = new TextField();
        nameField.getStyleClass().add("input-control");
        nameField.setPromptText("Nome da nova categoria...");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button addBtn = new Button("Adicionar");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> {
            if (nameField.getText().isBlank()) { ctx.setStatus("Informe o nome da categoria."); return; }
            try {
                AppContextHolder.get().categoryService().add(nameField.getText(), domain);
                nameField.clear();
                ctx.refreshCategories();
                ctx.setStatus("Categoria adicionada com sucesso.");
            } catch (IllegalArgumentException ex) {
                ctx.setStatus("Erro: " + ex.getMessage());
            }
        });
        nameField.setOnAction(e -> addBtn.fire());

        Button removeBtn = new Button("Remover selecionada");
        removeBtn.getStyleClass().add("secondary-button");
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setOnAction(e -> {
            Category sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione uma categoria para remover."); return; }
            Dialogs.confirm("Confirmar remoção", "Remover categoria \"" + sel.name() + "\"?",
                    "Esta ação remove a categoria permanentemente.\n" +
                    "Os registros vinculados a ela não serão excluídos,\n" +
                    "mas ficarão sem categoria associada.")
                    .ifPresent(btn -> {
                        if (btn == ButtonType.OK) {
                            AppContextHolder.get().categoryService().remove(sel.id());
                            ctx.refreshCategories();
                            ctx.setStatus("Categoria \"" + sel.name() + "\" removida.");
                        }
                    });
        });

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        HBox addBar = new HBox(8, nameField, addBtn);
        addBar.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, titleLabel, listView, addBar, removeBtn);
        section.getStyleClass().addAll("config-section", styleClass);
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }
}
