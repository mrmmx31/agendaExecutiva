package com.pessoal.agenda;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.service.TaskTimerService;
import com.pessoal.agenda.service.TaskTimerRecoveryService;
import com.pessoal.agenda.ui.controller.*;
import com.pessoal.agenda.ui.view.ThemeManager;
import com.pessoal.agenda.ui.view.InboxTriageWindow;
import com.pessoal.agenda.ui.view.QuickCaptureShortcutBinding;
import com.pessoal.agenda.ui.view.QuickCaptureWindow;
import com.pessoal.agenda.ui.view.ReminderShortcutBinding;
import com.pessoal.agenda.ui.view.StatusAlertAnimator;
import com.pessoal.agenda.ui.view.TaskTimerWindow;
import com.pessoal.agenda.ui.view.TimerRecoveryDialog;
import com.pessoal.agenda.ui.view.WindowManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.YearMonth;

/**
 * Agenda Científica Pessoal — ponto de entrada da aplicação JavaFX.
 *
 * Esta classe é responsável apenas pela montagem da janela principal
 * e coordenação dos refreshes entre os controllers de cada aba.
 *
 * A lógica de cada módulo está em {@code com.pessoal.agenda.ui.controller}.
 */
public class AgendaApp extends Application {

    // ── Infraestrutura legada (ainda necessária para operações não migradas) ─
    private final DatabaseService databaseService = new DatabaseService();

    // ── Estado compartilhado entre todos os controllers ────────────────────
    private SharedContext ctx;

    // ── Controllers de cada aba ────────────────────────────────────────────
    private DashboardController  dashboardCtrl;
    private AgendaTabController  agendaCtrl;
    private ChecklistController  checklistCtrl;
    private FinanceController    financeCtrl;
    private SalesController      salesCtrl;
    private StudyController      studyCtrl;
    private IdeasController      ideasCtrl;
    private ConfigController     configCtrl;
    private QuickCaptureWindow quickCaptureWindow;
    private InboxTriageWindow inboxTriageWindow;
    private QuickCaptureShortcutBinding quickCaptureShortcutBinding;
    private Button inboxButton;
    private TaskTimerRecoveryService timerRecoveryService;

    // ── Barra de status ────────────────────────────────────────────────────
    private final Label statusLabel = new Label("Sistema pronto para uso.");
    private final Label statusAlertBadge = new Label("SEM ALERTAS");
    private final Tooltip statusAlertTooltip = new Tooltip();
    private final ContextMenu statusAlertPopover = new ContextMenu();
    private StatusAlertAnimator statusAlertAnimator;
    private int badgeOverdueAlerts = 0;
    private int badgeTodayCount = 0;
    private int badgeProtocolCount = 0;
    private final Runnable timerStateRefresh = () -> Platform.runLater(() -> {
        if (dashboardCtrl != null) refreshDashboardKpis();
    });

    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        WindowManager.initialize(stage);
        databaseService.initialize();
        AppContextHolder.get().localMetricsService().beginSession();
        quickCaptureWindow = new QuickCaptureWindow(
                AppContextHolder.get().inboxCaptureService(), actions -> {
                    AppContextHolder.get().localMetricsService().recordQuickCapture(actions);
                    refreshInboxCount();
                });
        inboxTriageWindow = new InboxTriageWindow(
                AppContextHolder.get().inboxCaptureService(), this::handleInboxChanged);
        timerRecoveryService = AppContextHolder.get().taskTimerRecoveryService();

        // Contexto compartilhado com callback de status
        ctx = new SharedContext(statusLabel::setText);

        try {
            AppContextHolder.get().startMobileSyncServer();
        } catch (RuntimeException error) {
            statusLabel.setText("Sync móvel indisponível nesta rede; tente novamente nas Configurações.");
        }

        // Callbacks coordenados pelo AgendaApp
        ctx.setDashboardRefreshCallback(this::refreshDashboardKpis);
        ctx.setAlertRefreshCallback(this::refreshAlertsAndUpcoming);
        ctx.setInboxRefreshCallback(this::handleInboxChanged);

        // Inicialização dos controllers
        dashboardCtrl = new DashboardController(ctx, databaseService,
                AppContextHolder.get().dailyPlanService(),
                AppContextHolder.get().dayReviewService(),
                AppContextHolder.get().localMetricsService(),
                AppContextHolder.get().taskRepository(),
                AppContextHolder.get().focusContextService());
        agendaCtrl    = new AgendaTabController(ctx, databaseService);
        checklistCtrl = new ChecklistController(ctx);
        financeCtrl   = new FinanceController(ctx, databaseService);
        salesCtrl     = new SalesController(ctx, databaseService);
        studyCtrl     = new StudyController(ctx);
        ideasCtrl     = new IdeasController(ctx, databaseService);
        configCtrl    = new ConfigController(
                ctx, this::updateCriticalBadge, this::refreshQuickCaptureShortcut);
        TaskTimerService.get().addStateListener(timerStateRefresh);

        // Montagem do TabPane
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tabs");

        // Navegação a partir do Dashboard: Agenda=1, Financeiro=3
        dashboardCtrl.setTabNavigator(idx -> tabPane.getSelectionModel().select(idx));
        dashboardCtrl.setTaskNavigator((date, taskId) -> {
            tabPane.getSelectionModel().select(1);
            agendaCtrl.navigateToTask(date, taskId);
        });

        tabPane.getTabs().addAll(
                dashboardCtrl.buildTab(),
                agendaCtrl.buildTab(),
                checklistCtrl.buildTab(),
                financeCtrl.buildTab(),
                salesCtrl.buildTab(),
                studyCtrl.buildTab(),
                ideasCtrl.buildTab(),
                configCtrl.buildTab()
        );

        // Layout principal
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader(tabPane));
        root.setCenter(tabPane);
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1260, 820);
        ThemeManager.getInstance().applyTo(scene);
        registerShortcuts(scene);

        // Hook global: aplica tema automaticamente a TODA nova janela/diálogo que abrir
        ThemeManager.getInstance().initGlobalWindowHook();

        stage.setTitle("Agenda Científica Pessoal — Planejamento Integrado");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            PendencyNotificationService.getInstance().stop();
            timerRecoveryService.stopTracking();
            TaskTimerService.get().removeStateListener(timerStateRefresh);
            AppContextHolder.get().stopMobileSyncServer();
            WindowManager.closeAll();
            Platform.exit();
        });
        stage.show();

        // Carga inicial de todos os dados
        refreshAllData(YearMonth.now());
        Platform.runLater(this::offerTimerRecovery);

        PendencyNotificationService.getInstance().start(() -> {
            refreshAlertsAndUpcoming();
            refreshDashboardKpis();
            updateCriticalBadge();
            ctx.setStatus("Lembrete: você tem pendências críticas para revisar.");
        });
    }

    private void offerTimerRecovery() {
        var pending = timerRecoveryService.pending();
        if (pending.isEmpty()) {
            timerRecoveryService.startTracking();
            return;
        }

        TimerRecoveryDialog.Decision decision = TimerRecoveryDialog.show(pending.get());
        if (decision == TimerRecoveryDialog.Decision.RECOVER) {
            var recovered = timerRecoveryService.recover();
            new TaskTimerWindow(recovered.task(),
                    AppContextHolder.get().taskSessionRepository(),
                    ctx::triggerDashboardRefresh).show();
            ctx.setStatus("Timer recuperado e pausado: " + recovered.task().title());
        } else {
            timerRecoveryService.discard();
            ctx.setStatus("Intervalo anterior descartado.");
        }
        timerRecoveryService.startTracking();
        refreshDashboardKpis();
    }

    // ── Coordenação de refreshes ───────────────────────────────────────────

    private void refreshAllData(YearMonth month) {
        ctx.refreshCategories();
        agendaCtrl.refresh();              // agenda + alertas + upcoming
        checklistCtrl.refresh();
        financeCtrl.refresh();
        salesCtrl.refresh();
        studyCtrl.refresh();
        ideasCtrl.refresh();
        refreshDashboardKpis();
        refreshAlertsAndUpcoming();
        updateCriticalBadge();
        refreshInboxCount();
    }

    private void refreshDashboardKpis() {
        YearMonth month = YearMonth.from(agendaCtrl.getCurrentDate());
        dashboardCtrl.refreshKpis(month);
        updateCriticalBadge();
    }

    private void refreshAlertsAndUpcoming() {
        ctx.alertItems.setAll(databaseService.listDeadlineAlerts());
        ctx.upcomingItems.setAll(databaseService.listUpcomingDeadlines(10));
        updateCriticalBadge();
    }

    private void registerShortcuts(Scene scene) {
        Runnable remindAction = () -> triggerManualReminder("atalho Ctrl/Cmd+Shift+R");

        new ReminderShortcutBinding(scene, remindAction).bind();

        quickCaptureShortcutBinding = new QuickCaptureShortcutBinding(
                scene,
                AppContextHolder.get().quickCapturePreferences(),
                this::openQuickCapture);
        quickCaptureShortcutBinding.refresh();
    }

    private void refreshQuickCaptureShortcut() {
        if (quickCaptureShortcutBinding != null) {
            quickCaptureShortcutBinding.refresh();
        }
    }

    private void openQuickCapture() {
        quickCaptureWindow.show();
    }

    private void handleInboxChanged() {
        refreshInboxCount();
        agendaCtrl.refresh();
        ideasCtrl.refresh();
        refreshDashboardKpis();
    }

    private void refreshInboxCount() {
        if (inboxButton == null) return;
        try {
            int count = AppContextHolder.get().inboxCaptureService().countUnclassified();
            inboxButton.setText(count > 0 ? "Caixa de entrada (" + count + ")" : "Caixa de entrada");
        } catch (RuntimeException error) {
            inboxButton.setText("Caixa de entrada");
        }
    }

    private void triggerManualReminder(String source) {
        refreshAlertsAndUpcoming();
        refreshDashboardKpis();
        PendencyNotificationService notificationService = PendencyNotificationService.getInstance();
        if (!notificationService.isEnabled()) {
            ctx.setStatus("Lembretes estão desligados nas Configurações.");
            return;
        }
        if (notificationService.isSnoozed()) {
            ctx.setStatus("Pendências atualizadas. Lembretes continuam pausados.");
            return;
        }
        notificationService.forceCheck();
        if (notificationService.isSoundEnabled()
                && notificationService.isQuietHoursEnabled()
                && notificationService.isQuietHours()) {
            ctx.setStatus("Lembrete visual solicitado por " + source
                    + "; som omitido pelo horário silencioso.");
        } else {
            ctx.setStatus("Lembrete manual solicitado por " + source + ".");
        }
    }

    private void updateCriticalBadge() {
        int overdueAlerts = (int) ctx.alertItems.stream()
                .filter(s -> s != null && !s.isBlank() && !s.startsWith("Sem atrasos"))
                .count();
        int todayCount = ctx.todayTaskItems.size();
        int protocolCount = ctx.expiringProtocolItems.size();
        int critical = overdueAlerts + todayCount + protocolCount;

        badgeOverdueAlerts = overdueAlerts;
        badgeTodayCount = todayCount;
        badgeProtocolCount = protocolCount;
        rebuildStatusPopover();

        if (critical <= 0) {
            statusAlertBadge.setText("SEM ALERTAS");
            statusAlertTooltip.setText("Sem pendências críticas no momento.");
            statusAlertBadge.getStyleClass().remove("status-alert-critical");
            statusAlertBadge.getStyleClass().remove("status-alert-warning");
            statusAlertBadge.getStyleClass().add("status-alert-ok");
            statusAlertBadge.setOpacity(1.0);
            stopBadgeBlink();
            return;
        }

        statusAlertBadge.setText("PENDÊNCIAS: " + critical + " (A:" + overdueAlerts
                + " H:" + todayCount + " P:" + protocolCount + ")");
        statusAlertTooltip.setText("Pendências críticas:\n"
                + "A = Alertas de atraso: " + overdueAlerts + "\n"
                + "H = Tarefas de hoje: " + todayCount + "\n"
                + "P = Protocolos periódicos: " + protocolCount + "\n\n"
                + "Clique no indicador para revisar ou pausar lembretes.");
        statusAlertBadge.getStyleClass().remove("status-alert-ok");
        statusAlertBadge.getStyleClass().remove("status-alert-critical");
        statusAlertBadge.getStyleClass().remove("status-alert-warning");
        if (overdueAlerts > 0) {
            statusAlertBadge.getStyleClass().add("status-alert-critical");
        } else {
            statusAlertBadge.getStyleClass().add("status-alert-warning");
        }
        PendencyNotificationService notificationService = PendencyNotificationService.getInstance();
        if (notificationService.isBadgeAttentionAllowed()) {
            startBadgeBlink();
        } else {
            stopBadgeBlink();
            statusAlertBadge.setOpacity(1.0);
        }
    }

    private void startBadgeBlink() {
        if (statusAlertAnimator == null) {
            statusAlertAnimator = new StatusAlertAnimator(statusAlertBadge);
        }
        statusAlertAnimator.play();
    }

    private void stopBadgeBlink() {
        if (statusAlertAnimator != null) {
            statusAlertAnimator.stop();
            statusAlertAnimator = null;
        }
    }

    // ── Construção do cabeçalho e barra de status ──────────────────────────

    private Node buildHeader(TabPane tabPane) {
        Label title    = new Label("Painel de Operação Pessoal");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label(
                "Planejamento diário, financeiro, estudos e execução de projetos em um único fluxo");
        subtitle.getStyleClass().add("page-subtitle");

        Button refreshAllBtn = new Button("Atualizar tudo");
        refreshAllBtn.getStyleClass().add("secondary-button");
        refreshAllBtn.setOnAction(e -> {
            refreshAllData(YearMonth.from(agendaCtrl.getCurrentDate()));
            ctx.setStatus("Dados atualizados com sucesso.");
        });

        Button focusBtn = new Button("Ir para Dashboard");
        focusBtn.getStyleClass().add("secondary-button");
        focusBtn.setOnAction(e -> tabPane.getSelectionModel().select(0));

        Button captureBtn = new Button("Capturar");
        captureBtn.setId("global-quick-capture");
        captureBtn.getStyleClass().add("primary-button");
        captureBtn.setOnAction(e -> openQuickCapture());

        inboxButton = new Button("Caixa de entrada");
        inboxButton.setId("global-inbox-triage");
        inboxButton.getStyleClass().add("secondary-button");
        inboxButton.setOnAction(e -> inboxTriageWindow.show());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, new VBox(4, title, subtitle), spacer,
                focusBtn, refreshAllBtn, inboxButton, captureBtn);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 18, 14, 18));
        return header;
    }

    private Node buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        statusAlertBadge.getStyleClass().addAll("status-label", "status-alert-badge", "status-alert-ok");
        statusAlertTooltip.setText("Sem pendências críticas no momento.");
        Tooltip.install(statusAlertBadge, statusAlertTooltip);
        statusAlertBadge.setOnMouseClicked(e -> {
            rebuildStatusPopover();
            if (statusAlertPopover.isShowing()) {
                statusAlertPopover.hide();
            } else {
                statusAlertPopover.show(statusAlertBadge, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, statusLabel, spacer, statusAlertBadge);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 18, 8, 18));
        return bar;
    }

    private void rebuildStatusPopover() {
        statusAlertPopover.getItems().clear();
        MenuItem title = new MenuItem("Pendências críticas");
        title.setDisable(true);
        MenuItem overdue = new MenuItem("Atrasos (A): " + badgeOverdueAlerts);
        overdue.setDisable(true);
        MenuItem today = new MenuItem("Hoje (H): " + badgeTodayCount);
        today.setDisable(true);
        MenuItem protocol = new MenuItem("Protocolos (P): " + badgeProtocolCount);
        protocol.setDisable(true);
        MenuItem remindNow = new MenuItem("Lembrar agora");
        remindNow.setOnAction(ev -> triggerManualReminder("indicador de pendências"));
        PendencyNotificationService notificationService = PendencyNotificationService.getInstance();
        remindNow.setDisable(!notificationService.isEnabled());
        boolean snoozed = notificationService.isSnoozed();
        MenuItem snooze = new MenuItem(snoozed
                ? "Retomar lembretes"
                : "Pausar lembretes por 30 minutos");
        snooze.setDisable(!notificationService.isEnabled());
        snooze.setOnAction(ev -> {
            if (snoozed) {
                notificationService.clearSnooze();
                updateCriticalBadge();
                ctx.setStatus("Lembretes retomados.");
            } else {
                notificationService.snoozeForMinutes(30);
                stopBadgeBlink();
                statusAlertBadge.setOpacity(1.0);
                ctx.setStatus("Lembretes pausados por 30 minutos. As pendências continuam visíveis.");
            }
        });
        statusAlertPopover.getItems().addAll(title, new javafx.scene.control.SeparatorMenuItem(), overdue, today, protocol,
                new javafx.scene.control.SeparatorMenuItem(), remindNow, snooze);
    }
}
