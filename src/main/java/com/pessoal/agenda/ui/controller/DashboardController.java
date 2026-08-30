package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.model.Protocol;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.DailyPlan;
import com.pessoal.agenda.model.DailyPlanCapacity;
import com.pessoal.agenda.model.FocusContext;
import com.pessoal.agenda.model.OverdueAgeBand;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.service.DailyPlanService;
import com.pessoal.agenda.service.DayReviewService;
import com.pessoal.agenda.service.FocusSelectionService;
import com.pessoal.agenda.service.FocusContextService;
import com.pessoal.agenda.service.TaskTimerService;
import com.pessoal.agenda.service.LocalMetricsService;
import com.pessoal.agenda.ui.view.DailyPlanPanel;
import com.pessoal.agenda.ui.view.DayReviewWindow;
import com.pessoal.agenda.ui.view.IdeaInboxReviewWindow;
import com.pessoal.agenda.ui.view.ProjectIdeaDetailWindow;
import com.pessoal.agenda.ui.view.Dialogs;
import com.pessoal.agenda.ui.view.TaskTimerWindow;
import com.pessoal.agenda.ui.view.LocalMetricsPanel;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.view.ProtocolExecutionWindow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Controller da aba Dashboard.
 * Exibe KPIs consolidados, próximos prazos críticos e alertas de atraso.
 */
public class DashboardController {

    private static final String FOCUS_TASK_PREF = "dashboard.focus.taskId";

    private final SharedContext    ctx;
    private final DatabaseService  db;
    private final DailyPlanService dailyPlanService;
    private final DayReviewService dayReviewService;
    private final TaskRepository taskRepository;
    private final FocusContextService focusContextService;
    private final LocalMetricsService localMetricsService;
    private final Consumer<Task> timerWindowOpener;
    private final FocusSelectionService focusSelectionService = new FocusSelectionService();

    /**
     * Callback para navegar até uma aba pelo índice.
     * Índices: 0=Dashboard, 1=Agenda, 2=Checklist, 3=Financeiro,
     *          4=Vendas, 5=Estudos, 6=Ideias, 7=Config.
     */
    private IntConsumer tabNavigator;
    private BiConsumer<LocalDate, Long> taskNavigator;

    private Label  focusNowTitleLabel;
    private Label  focusNowDetailLabel;
    private Label  focusNowModeLabel;
    private Button focusStartBtn;
    private Button focusOpenBtn;
    private Button focusChooseBtn;
    private Button focusAutomaticBtn;
    private VBox focusNowBox;
    private final javafx.collections.ObservableList<Protocol> frequentProtocolItems = javafx.collections.FXCollections.observableArrayList();
    private final ObservableList<ProtocolNowItem> protocolNowItems = FXCollections.observableArrayList();
    private final ObservableList<TaskReminderItem> highlightedTaskItems = FXCollections.observableArrayList();
    private final ObservableList<TaskReminderItem> overdueUpTo7Items = FXCollections.observableArrayList();
    private final ObservableList<TaskReminderItem> overdue8To30Items = FXCollections.observableArrayList();
    private final ObservableList<TaskReminderItem> overdueOver30Items = FXCollections.observableArrayList();
    private final ObservableList<IdeaInboxItem> ideaInboxItems = FXCollections.observableArrayList();
    private final ObservableList<StudyTodayItem> studyTodayItems = FXCollections.observableArrayList();
    private Runnable focusNowAction = () -> {};
    private List<TaskReminderItem> focusCandidates = List.of();
    private TaskReminderItem currentFocusTask;
    private FocusSelectionService.Origin currentFocusOrigin;
    private FocusContext currentResumeContext;
    private final Preferences preferences;
    private TextField quickIdeaTitleField;
    private TextArea quickIdeaNotesArea;
    private DailyPlanPanel dailyPlanPanel;
    private VBox dashboardContent;
    private DailyPlan currentDailyPlan;
    private Task dailyPlanEssentialTask;
    private Tab overdueUpTo7Tab;
    private Tab overdue8To30Tab;
    private Tab overdueOver30Tab;
    private LocalMetricsPanel localMetricsPanel;
    private Long resumeAttemptTaskId;
    private int resumeActionAttempts;

    private record TaskReminderItem(long taskId,
                                    String title,
                                    LocalDate anchorDate,
                                    TaskPriority priority,
                                    long overdueDays,
                                    boolean dueToday,
                                    boolean overdue,
                                    boolean longPending,
                                    String category,
                                    String linkedProtocolName) {
        @Override
        public String toString() {
            String priorityLabel = priority != null ? priority.label() : "Normal";
            return title + " · " + anchorDate + " · " + priorityLabel;
        }
    }

    private record ProtocolNowItem(Protocol protocol, String reason, int score) {}

    private record TimedProtocolSignal(int score, String reason) {}

    private record FocusedTask(TaskReminderItem item, FocusSelectionService.Origin origin) {}

    private record ResumeFocus(FocusContext context, TaskReminderItem item) {}

    private record IdeaInboxItem(long id,
                                 String title,
                                 String category,
                                 String priority,
                                 String parentTitle) {}

    private record StudyTodayItem(long planId,
                                  String title,
                                  String category,
                                  String progressDisplay,
                                  double presenceRate) {}

    public DashboardController(SharedContext ctx, DatabaseService db,
                               DailyPlanService dailyPlanService, DayReviewService dayReviewService,
                               LocalMetricsService localMetricsService,
                               TaskRepository taskRepository,
                               FocusContextService focusContextService) {
        this(ctx, db, dailyPlanService, dayReviewService, localMetricsService,
                taskRepository, focusContextService,
                task -> new TaskTimerWindow(task,
                        AppContextHolder.get().taskSessionRepository(),
                        ctx::triggerDashboardRefresh).show(),
                Preferences.userNodeForPackage(DashboardController.class));
    }

    DashboardController(SharedContext ctx, DatabaseService db,
                        DailyPlanService dailyPlanService, DayReviewService dayReviewService,
                        LocalMetricsService localMetricsService,
                        TaskRepository taskRepository,
                        FocusContextService focusContextService,
                        Consumer<Task> timerWindowOpener, Preferences preferences) {
        this.ctx = ctx;
        this.db  = db;
        this.dailyPlanService = dailyPlanService;
        this.dayReviewService = dayReviewService;
        this.localMetricsService = localMetricsService;
        this.taskRepository = taskRepository;
        this.focusContextService = focusContextService;
        this.timerWindowOpener = timerWindowOpener;
        this.preferences = preferences;
    }

    /** Define o callback de navegação entre abas (chamado pelo AgendaApp). */
    public void setTabNavigator(IntConsumer navigator) {
        this.tabNavigator = navigator;
    }

    public void setTaskNavigator(BiConsumer<LocalDate, Long> navigator) {
        this.taskNavigator = navigator;
    }

    // ── Construção da aba ──────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("Dashboard");
        tab.setClosable(false);

        FlowPane cards = new FlowPane();
        cards.getStyleClass().addAll("dashboard-cards", "reduced-attention");
        cards.setHgap(12); cards.setVgap(12);
        cards.getChildren().addAll(
                UIHelper.createKpiCard("📋 Tarefas de HOJE",    ctx.tasksDueCountLabel,   "kpi-red"),
                UIHelper.createKpiCard("⚠️ Protocolos vencendo", ctx.protocolsExpiringCountLabel, "kpi-orange"),
                UIHelper.createKpiCard("Tarefas abertas",       ctx.openTasksValue,       "kpi-blue"),
                UIHelper.createKpiCard("Tarefas vencidas",      ctx.overdueTasksValue,    "kpi-red"),
                UIHelper.createKpiCard("Pagamentos pendentes",  ctx.pendingPaymentsValue, "kpi-orange"),
                UIHelper.createKpiCard("Valor pendente",        ctx.pendingAmountValue,   "kpi-purple"),
                UIHelper.createKpiCard("Checklist pendente",    ctx.checklistPendingValue,"kpi-cyan"),
                UIHelper.createKpiCard("Estudo no mês",         ctx.studyHoursValue,      "kpi-green"),
                UIHelper.createKpiCard("Estoque baixo",         ctx.lowStockValue,        "kpi-red"),
                UIHelper.createKpiCard("Ideias em progresso",   ctx.ideasInProgressValue, "kpi-indigo")
        );

        focusNowModeLabel = new Label("Seleção automática");
        focusNowModeLabel.setId("dashboard-focus-mode");
        focusNowModeLabel.getStyleClass().add("focus-now-mode");

        focusNowTitleLabel = new Label("Carregando foco do dia...");
        focusNowTitleLabel.setId("dashboard-focus-title");
        focusNowTitleLabel.getStyleClass().add("focus-now-title");
        focusNowTitleLabel.setWrapText(true);

        focusNowDetailLabel = new Label("Assim que os dados forem atualizados, este bloco mostrará a próxima ação.");
        focusNowDetailLabel.setId("dashboard-focus-detail");
        focusNowDetailLabel.getStyleClass().add("focus-now-detail");
        focusNowDetailLabel.setWrapText(true);

        focusStartBtn = new Button("▶  Iniciar foco");
        focusStartBtn.setId("dashboard-focus-start");
        focusStartBtn.getStyleClass().add("primary-button");
        focusStartBtn.setTooltip(new Tooltip("Abre o timer diretamente para a tarefa escolhida."));
        focusStartBtn.setOnAction(e -> startCurrentFocus());

        focusOpenBtn = new Button("Abrir detalhes");
        focusOpenBtn.setId("dashboard-focus-open");
        focusOpenBtn.getStyleClass().add("secondary-button");
        focusOpenBtn.setOnAction(e -> openCurrentFocusDetails());

        focusChooseBtn = new Button("Escolher foco");
        focusChooseBtn.setId("dashboard-focus-choose");
        focusChooseBtn.getStyleClass().add("secondary-button");
        focusChooseBtn.setOnAction(e -> chooseFocusManually());

        focusAutomaticBtn = new Button("Usar automático");
        focusAutomaticBtn.setId("dashboard-focus-automatic");
        focusAutomaticBtn.getStyleClass().add("secondary-button");
        focusAutomaticBtn.setOnAction(e -> clearManualFocus());

        FlowPane focusActions = new FlowPane(8, 8,
                focusStartBtn, focusOpenBtn, focusChooseBtn, focusAutomaticBtn);
        focusActions.getStyleClass().add("focus-now-actions");

        VBox focusNowContent = new VBox(6, focusNowModeLabel, focusNowTitleLabel,
                focusNowDetailLabel, focusActions);
        focusNowContent.setFillWidth(true);
        focusNowBox = UIHelper.createCardSection("Agora", focusNowContent);
        focusNowBox.setId("dashboard-focus-now");
        focusNowBox.getStyleClass().add("focus-now-card");

        dailyPlanPanel = new DailyPlanPanel();
        dailyPlanPanel.setStartAction(this::beginDailyPlanning);
        dailyPlanPanel.setEditAction(this::beginDailyPlanning);
        dailyPlanPanel.setOpenEssentialAction(this::openDailyPlanEssential);
        dailyPlanPanel.setCloseDayAction(this::openDayReview);
        dailyPlanPanel.setRetryAction(this::updateDailyPlanPanel);
        dailyPlanPanel.setSaveAction(this::saveDailyPlan);
        dailyPlanPanel.setCancelPlanningAction(this::updateDailyPlanPanel);
        dailyPlanPanel.setCapacityChangeAction(this::applyDailyPlanCapacityMode);

        quickIdeaTitleField = new TextField();
        quickIdeaTitleField.getStyleClass().add("input-control");
        quickIdeaTitleField.setPromptText("Título opcional da captura...");

        quickIdeaNotesArea = new TextArea();
        quickIdeaNotesArea.getStyleClass().add("input-control");
        quickIdeaNotesArea.setPromptText("Despeje aqui as ideias do momento.\n\nDica: separe blocos com uma linha em branco para criar várias notas agrupadas.");
        quickIdeaNotesArea.setPrefRowCount(5);
        quickIdeaNotesArea.setWrapText(true);

        Button saveQuickIdeaBtn = new Button("💾 Salvar captura");
        saveQuickIdeaBtn.getStyleClass().add("primary-button");
        saveQuickIdeaBtn.setOnAction(e -> saveQuickIdeaCapture());

        Button reviewIdeasBtn = new Button("🗂 Revisar ideias");
        reviewIdeasBtn.getStyleClass().add("secondary-button");
        reviewIdeasBtn.setOnAction(e -> openIdeaReview(null));

        ListView<IdeaInboxItem> ideaInboxList = new ListView<>(ideaInboxItems);
        ideaInboxList.getStyleClass().add("clean-list");
        ideaInboxList.setPrefHeight(150);
        ideaInboxList.setMinHeight(110);
        ideaInboxList.setPlaceholder(new Label("Nenhuma ideia aguardando revisão."));
        VBox.setVgrow(ideaInboxList, Priority.ALWAYS);
        ideaInboxList.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLabel = new Label();
            private final Label metaLabel = new Label();
            private final VBox box = new VBox(2, titleLabel, metaLabel);
            {
                titleLabel.getStyleClass().add("t-heading-sm");
                metaLabel.getStyleClass().add("t-muted");
                titleLabel.setWrapText(true);
                metaLabel.setWrapText(true);
            }

            @Override
            protected void updateItem(IdeaInboxItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                titleLabel.setText(item.title());
                String meta = item.priority() + " · " + item.category();
                if (item.parentTitle() != null && !item.parentTitle().isBlank()) {
                    meta += " · ↳ " + item.parentTitle();
                }
                metaLabel.setText(meta);
                setGraphic(box);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        ideaInboxList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                IdeaInboxItem selected = ideaInboxList.getSelectionModel().getSelectedItem();
                if (selected != null) openIdeaReview(selected.id());
            }
        });
        Tooltip.install(ideaInboxList, new Tooltip("Ideias novas aguardando organização. Duplo clique para abrir a revisão."));

        Label quickIdeaHint = new Label("💡 Capture sem parar o que está fazendo. Depois você revisa, prioriza e pode ligar uma nota à outra.");
        quickIdeaHint.getStyleClass().add("dashboard-hint");
        HBox quickIdeaButtons = new HBox(8, saveQuickIdeaBtn, reviewIdeasBtn);
        VBox quickIdeasBox = UIHelper.createCardSection("🧠 Ideias para revisar",
                new VBox(6,
                        new VBox(4,
                                new Label("Anote no impulso, organize na hora certa."),
                                quickIdeaHint),
                        quickIdeaTitleField,
                        quickIdeaNotesArea,
                        quickIdeaButtons,
                        ideaInboxList));

        // ── Card: estudos do dia ───────────────────────────────────────────────
        ListView<StudyTodayItem> studyTodayList = new ListView<>(studyTodayItems);
        studyTodayList.getStyleClass().add("clean-list");
        studyTodayList.setPrefHeight(150);
        studyTodayList.setMinHeight(100);
        studyTodayList.setPlaceholder(new Label("Nenhum estudo programado para hoje."));
        VBox.setVgrow(studyTodayList, Priority.ALWAYS);
        studyTodayList.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLabel = new Label();
            private final Label metaLabel  = new Label();
            private final Button openBtn   = new Button("▶ Abrir Diário");
            private final VBox textBox     = new VBox(2, titleLabel, metaLabel);
            private final Region spc       = new Region();
            private final HBox row         = new HBox(8, textBox, spc, openBtn);
            {
                titleLabel.getStyleClass().add("t-heading-sm");
                metaLabel.getStyleClass().add("t-muted");
                openBtn.getStyleClass().add("secondary-button");
                openBtn.setStyle("-fx-font-size: 10.5px; -fx-padding: 2 8 2 8;");
                HBox.setHgrow(spc, Priority.ALWAYS);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(StudyTodayItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                titleLabel.setText(item.title());
                metaLabel.setText(item.category() + " · " + item.progressDisplay()
                        + " · presença " + String.format("%.0f%%", item.presenceRate()));
                openBtn.setOnAction(e -> openStudyDiary(item.planId()));
                setGraphic(row);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        studyTodayList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                StudyTodayItem sel = studyTodayList.getSelectionModel().getSelectedItem();
                if (sel != null) openStudyDiary(sel.planId());
            }
        });
        Tooltip.install(studyTodayList, new Tooltip("Estudos com frequência programada para hoje. Duplo clique ou ▶ para abrir o diário."));

        Label studyTodayHint = new Label("💡 Somente estudos em andamento com o dia de hoje na grade de frequência aparecem aqui.");
        studyTodayHint.getStyleClass().add("dashboard-hint");
        VBox studyTodayBox = UIHelper.createCardSection("📚 Estudos do dia",
                new VBox(6,
                        new VBox(4, new Label("Estudos com frequência programada para hoje."), studyTodayHint),
                        studyTodayList));
        studyTodayBox.getStyleClass().add("reduced-attention");

        // ── ListView de tarefas de hoje ────────────────────────────────────────
        ListView<String> todayTasksList = new ListView<>(ctx.todayTaskItems);
        todayTasksList.getStyleClass().add("clean-list");
        todayTasksList.setPrefHeight(120);
        todayTasksList.setMinHeight(96);
        todayTasksList.setPlaceholder(new Label("Nenhuma tarefa programada para hoje."));
        VBox.setVgrow(todayTasksList, Priority.ALWAYS);
        Tooltip.install(todayTasksList,
                new Tooltip("Tarefas vencendo hoje. Duplo clique para navegar."));
        todayTasksList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openTaskByDate(LocalDate.now(), null);
            }
        });

        Label todayHint = new Label("💡 Duplo clique para abrir Agenda e Prioridades");
        todayHint.getStyleClass().add("dashboard-hint");

        VBox todayTasksBox = UIHelper.createCardSection("📋 Tarefas de Hoje",
                new VBox(4,
                        new VBox(4, new Label("Tarefas disponíveis para hoje."), todayHint),
                        todayTasksList));

        // ── ListView de protocolos vencendo ─────────────────────────────────────
        ListView<String> expiringProtocolsList = new ListView<>(ctx.expiringProtocolItems);
        expiringProtocolsList.getStyleClass().add("clean-list");
        expiringProtocolsList.setPrefHeight(120);
        expiringProtocolsList.setMinHeight(96);
        VBox.setVgrow(expiringProtocolsList, Priority.ALWAYS);
        Tooltip.install(expiringProtocolsList,
                new Tooltip("Protocolos que expiram em breve. Duplo clique para ir à aba."));
        expiringProtocolsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tabNavigator != null) {
                tabNavigator.accept(2); // Protocolos Operacionais
            }
        });

        Label protocolHint = new Label("💡 Duplo clique para abrir Protocolos Operacionais");
        protocolHint.getStyleClass().add("dashboard-hint");

        VBox expiringProtocolsBox = UIHelper.createCardSection("⚠️ Protocolos Vencendo",
                new VBox(4,
                        new VBox(4, new Label("Protocolos próximos do vencimento — execute em breve!"), protocolHint),
                        expiringProtocolsList));

        ListView<TaskReminderItem> highlightedTasksList = buildTaskReminderList(highlightedTaskItems,
                "Tarefas mais recentes/importantes. Duplo clique abre a data exata na Agenda.");
        Label highlightedHint = new Label("Tarefas vencidas ficam disponíveis na revisão por período.");
        highlightedHint.getStyleClass().add("dashboard-hint");
        VBox highlightedTasksBox = UIHelper.createCardSection("🎯 Tarefas em destaque",
                new VBox(4,
                        new VBox(4, new Label("Tarefas recentes e prioritárias para revisão."), highlightedHint),
                        highlightedTasksList));

        ListView<TaskReminderItem> overdueUpTo7List = buildTaskReminderList(overdueUpTo7Items,
                "Tarefas pendentes há até 7 dias. Duplo clique abre a tarefa.");
        overdueUpTo7List.setId("dashboard-overdue-up-to-7");
        ListView<TaskReminderItem> overdue8To30List = buildTaskReminderList(overdue8To30Items,
                "Tarefas pendentes entre 8 e 30 dias. Duplo clique abre a tarefa.");
        overdue8To30List.setId("dashboard-overdue-8-to-30");
        ListView<TaskReminderItem> overdueOver30List = buildTaskReminderList(overdueOver30Items,
                "Tarefas pendentes há mais de 30 dias. Duplo clique abre a tarefa.");
        overdueOver30List.setId("dashboard-overdue-over-30");
        overdueUpTo7Tab = new Tab(OverdueAgeBand.UP_TO_7_DAYS.label(), overdueUpTo7List);
        overdue8To30Tab = new Tab(OverdueAgeBand.DAYS_8_TO_30.label(), overdue8To30List);
        overdueOver30Tab = new Tab(OverdueAgeBand.OVER_30_DAYS.label(), overdueOver30List);
        TabPane overdueBands = new TabPane(overdueUpTo7Tab, overdue8To30Tab, overdueOver30Tab);
        overdueBands.setId("dashboard-overdue-bands");
        overdueBands.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label overdueHint = new Label(
                "Escolha uma faixa para revisar no seu ritmo. Nenhuma tarefa é iniciada automaticamente.");
        overdueHint.getStyleClass().add("dashboard-hint");
        VBox overdueTasksBox = UIHelper.createCardSection("Tarefas pendentes por período",
                new VBox(4, overdueHint, overdueBands));
        overdueTasksBox.getStyleClass().add("reduced-attention");

        ListView<ProtocolNowItem> protocolNowList = new ListView<>(protocolNowItems);
        protocolNowList.getStyleClass().add("clean-list");
        protocolNowList.setPrefHeight(150);
        protocolNowList.setMinHeight(105);
        VBox.setVgrow(protocolNowList, Priority.ALWAYS);
        protocolNowList.setPlaceholder(new Label("Nenhum protocolo urgente agora."));
        protocolNowList.setCellFactory(lv -> new ListCell<>() {
            private final Label nameLabel = new Label();
            private final Label reasonLabel = new Label();
            private final Button startBtn = new Button("▶ Iniciar");
            private final VBox textBox = new VBox(2, nameLabel, reasonLabel);
            private final Region spacer = new Region();
            private final HBox row = new HBox(8, textBox, spacer, startBtn);
            {
                nameLabel.getStyleClass().add("t-heading-sm");
                reasonLabel.getStyleClass().add("t-muted");
                startBtn.getStyleClass().add("secondary-button");
                startBtn.setStyle("-fx-font-size: 10.5px; -fx-padding: 2 8 2 8;");
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(ProtocolNowItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                int active = AppContextHolder.get().protocolRepository().countActiveExecutionsOf(item.protocol().id());
                nameLabel.setText(item.protocol().executionType().icon() + " " + item.protocol().name());
                reasonLabel.setText(item.reason());
                startBtn.setDisable(active > 0);
                startBtn.setText(active > 0 ? "● Em execução" : "▶ Iniciar");
                startBtn.setOnAction(e -> openProtocolExecution(item.protocol()));
                setGraphic(row);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        Tooltip.install(protocolNowList,
                new Tooltip("Protocolos mais acionáveis neste momento. Duplo clique ou Iniciar abre a execução."));
        protocolNowList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ProtocolNowItem selected = protocolNowList.getSelectionModel().getSelectedItem();
                if (selected != null) openProtocolExecution(selected.protocol());
            }
        });

        Label protocolNowHint = new Label("💡 Aqui entram protocolos ligados ao agora: saída, reunião, remédios, tarefa de hoje ou execução ativa. Gatilhos por relógio valem para a categoria Horários.");
        protocolNowHint.getStyleClass().add("dashboard-hint");
        VBox protocolNowBox = UIHelper.createCardSection("⏰ Protocolos de agora",
                new VBox(4,
                        new VBox(4, new Label("Ações rápidas para o momento presente."), protocolNowHint),
                        protocolNowList));

        ListView<Protocol> frequentProtocolsList = new ListView<>(frequentProtocolItems);
        frequentProtocolsList.getStyleClass().add("clean-list");
        frequentProtocolsList.setPrefHeight(160);
        frequentProtocolsList.setMinHeight(110);
        VBox.setVgrow(frequentProtocolsList, Priority.ALWAYS);
        frequentProtocolsList.setCellFactory(lv -> new ListCell<>() {
            private final Label nameLabel = new Label();
            private final Label metaLabel = new Label();
            private final Button startBtn = new Button("▶ Iniciar");
            private final VBox textBox = new VBox(2, nameLabel, metaLabel);
            private final Region spacer = new Region();
            private final HBox row = new HBox(8, textBox, spacer, startBtn);
            {
                nameLabel.getStyleClass().add("t-heading-sm");
                metaLabel.getStyleClass().add("t-muted");
                startBtn.getStyleClass().add("secondary-button");
                startBtn.setStyle("-fx-font-size: 10.5px; -fx-padding: 2 8 2 8;");
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.setPadding(new Insets(2, 0, 2, 0));
            }

            @Override
            protected void updateItem(Protocol item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                int active = AppContextHolder.get().protocolRepository().countActiveExecutionsOf(item.id());
                nameLabel.setText(item.executionType().icon() + " " + item.name());
                metaLabel.setText(active > 0
                        ? "Execução ativa em andamento"
                        : "Categoria: " + (item.category() != null ? item.category() : "Geral"));
                startBtn.setDisable(active > 0);
                startBtn.setText(active > 0 ? "● Em execução" : "▶ Iniciar");
                startBtn.setOnAction(e -> openProtocolExecution(item));
                setGraphic(row);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        Tooltip.install(frequentProtocolsList,
                new Tooltip("Protocolos mais usados no dia a dia. Use Iniciar para abrir execução imediatamente."));
        frequentProtocolsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Protocol selected = frequentProtocolsList.getSelectionModel().getSelectedItem();
                if (selected != null) openProtocolExecution(selected);
            }
        });

        Label frequentHint = new Label("💡 Inicie direto daqui sem precisar navegar até a aba de protocolos");
        frequentHint.getStyleClass().add("dashboard-hint");

        VBox frequentProtocolsBox = UIHelper.createCardSection("🏠 Protocolos mais recorrentes",
                new VBox(4,
                        new VBox(4, new Label("Atalhos para rotinas críticas: saída de casa, reunião, remédios..."), frequentHint),
                        frequentProtocolsList));

        ListView<String> upcomingList = new ListView<>(ctx.upcomingItems);
        upcomingList.getStyleClass().add("clean-list");
        upcomingList.setPrefHeight(180);
        upcomingList.setMinHeight(140);
        VBox.setVgrow(upcomingList, Priority.ALWAYS);
        Tooltip.install(upcomingList,
                new Tooltip("Duplo clique para navegar até a aba correspondente ao item selecionado."));
        upcomingList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String item = upcomingList.getSelectionModel().getSelectedItem();
                if (item == null) return;
                // Formato: "due_date | Tarefa | title"  ou  "due_date | Pagamento | title"
                if (item.contains("| Tarefa |") || item.contains("|Tarefa|")) {
                    extractFirstIsoDate(item).ifPresentOrElse(
                            date -> openTaskByDate(date, null),
                            () -> {
                                if (tabNavigator != null) tabNavigator.accept(1);
                            });
                } else if (item.contains("| Pagamento |") || item.contains("|Pagamento|")) {
                    if (tabNavigator != null) tabNavigator.accept(3);
                }
            }
        });

        ListView<String> alertsList = new ListView<>(ctx.alertItems);
        alertsList.getStyleClass().add("clean-list");
        alertsList.setPrefHeight(180);
        alertsList.setMinHeight(140);
        VBox.setVgrow(alertsList, Priority.ALWAYS);
        Tooltip.install(alertsList,
                new Tooltip("Duplo clique para navegar até a aba correspondente ao alerta selecionado."));
        alertsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String item = alertsList.getSelectionModel().getSelectedItem();
                if (item == null) return;
                // Formato: "Tarefa pendente há ..." ou "Pagamento pendente: ..."
                if (item.startsWith("Tarefa pendente há ")) {
                    extractFirstIsoDate(item).ifPresentOrElse(
                            date -> openTaskByDate(date, null),
                            () -> {
                                if (tabNavigator != null) tabNavigator.accept(1);
                            });
                } else if (item.startsWith("Pagamento pendente:")) {
                    if (tabNavigator != null) tabNavigator.accept(3);
                }
            }
        });

        Label upcomingHint = new Label("💡 Duplo clique para navegar à aba correspondente");
        upcomingHint.getStyleClass().add("dashboard-hint");

        Label alertsHint = new Label("💡 Duplo clique para navegar à aba correspondente");
        alertsHint.getStyleClass().add("dashboard-hint");

        VBox upcomingBox = UIHelper.createCardSection("Próximos prazos críticos",
                new VBox(4,
                        new VBox(4, new Label("Visão consolidada de tarefas e pagamentos."), upcomingHint),
                        upcomingList));
        VBox alertsBox = UIHelper.createCardSection("Pendências recentes",
                new VBox(4,
                        new VBox(4, new Label("Pendências vencidas disponíveis para revisão."), alertsHint),
                        alertsList));
        upcomingBox.setMinHeight(230);
        alertsBox.setMinHeight(230);
        VBox.setVgrow(upcomingBox, Priority.ALWAYS);
        VBox.setVgrow(alertsBox, Priority.ALWAYS);

        HBox bottom = new HBox(12, upcomingBox, alertsBox);
        bottom.setFillHeight(true);
        HBox.setHgrow(upcomingBox, Priority.ALWAYS);
        HBox.setHgrow(alertsBox,   Priority.ALWAYS);

        VBox todayView = new VBox(10, dailyPlanPanel, todayTasksBox, studyTodayBox, protocolNowBox);
        VBox organizeView = new VBox(10, quickIdeasBox, expiringProtocolsBox, frequentProtocolsBox);
        localMetricsPanel = new LocalMetricsPanel(localMetricsService);
        VBox reviewView = new VBox(10, highlightedTasksBox, overdueTasksBox,
                localMetricsPanel, bottom);

        Tab todayTab = new Tab("Hoje", todayView);
        Tab organizeTab = new Tab("Organizar", organizeView);
        Tab reviewTab = new Tab("Revisar", reviewView);
        TabPane workspace = new TabPane(todayTab, organizeTab, reviewTab);
        workspace.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        workspace.getStyleClass().add("dashboard-workspace");

        TitledPane overview = new TitledPane("Visão geral e indicadores", cards);
        overview.setExpanded(false);
        overview.getStyleClass().add("dashboard-overview");

        dashboardContent = new VBox(12, focusNowBox, workspace, overview);
        dashboardContent.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(dashboardContent);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("edge-to-edge");

        tab.setContent(scroll);
        return tab;
    }

    // ── Atualização de KPIs ────────────────────────────────────────────────

    public void refreshKpis(YearMonth month) {
        // Mantém listas da própria dashboard sincronizadas no mesmo ciclo de refresh.
        ctx.alertItems.setAll(db.listDeadlineAlerts());
        ctx.upcomingItems.setAll(db.listUpcomingDeadlines(10));

        ctx.openTasksValue.setText(String.valueOf(db.countOpenTasks()));
        int overdueCount = db.countOverdueTasks();
        ctx.overdueTasksValue.setText(String.valueOf(overdueCount));
        ctx.pendingPaymentsValue.setText(String.valueOf(db.countPendingPayments()));
        ctx.pendingAmountValue.setText("R$ %.2f".formatted(db.sumPendingPayments()));
        ctx.checklistPendingValue.setText(String.valueOf(db.countPendingChecklistItems()));
        int studyMins = AppContextHolder.get().studyEntryRepository().totalMinutesInMonth(month);
        ctx.studyHoursValue.setText("%.1f h".formatted(studyMins / 60.0));
        ctx.lowStockValue.setText(String.valueOf(db.countLowStockItems()));
        ctx.ideasInProgressValue.setText(String.valueOf(db.countIdeasInProgress()));

        // ── TDAH: Tarefas de hoje + Protocolos vencendo ────────────────────
        updateTodayTasks();
        updateExpiringProtocols();
        updateIdeaInbox();
        updateStudyToday();
        updateTaskReminderPanels();
        updateProtocolsNow();
        updateFrequentProtocols();
        updateDailyPlanPanel();
        updateFocusNowPanel();
        if (localMetricsPanel != null) localMetricsPanel.refresh();

        boolean hasAlertState = overdueCount > 0
                || !ctx.todayTaskItems.isEmpty()
                || !ctx.expiringProtocolItems.isEmpty();
        PendencyNotificationService.getInstance().setHasAlerts(hasAlertState);
    }

    private void updateDailyPlanPanel() {
        if (dailyPlanPanel == null) return;
        dailyPlanPanel.showLoading();
        currentDailyPlan = null;
        dailyPlanEssentialTask = null;
        try {
            LocalDate today = LocalDate.now();
            var review = dayReviewService.summary(today);
            if (review.isEmpty()) {
                applyDailyPlanCapacityMode(DailyPlanCapacity.NORMAL);
                dailyPlanPanel.showEmpty();
                return;
            }

            DailyPlan restored = review.get().plan();
            currentDailyPlan = restored.closedAt() == null
                    ? dailyPlanService.findByDate(today).orElse(null)
                    : null;
            dailyPlanEssentialTask = restored.essentialItem()
                    .flatMap(item -> taskRepository.findById(item.taskId()))
                    .orElse(null);
            List<String> supportTitles = restored.supportItems().stream()
                    .map(item -> taskRepository.findById(item.taskId())
                            .map(Task::title)
                            .orElse("Tarefa indisponível"))
                    .toList();
            String capacity = restored.capacity() == DailyPlanCapacity.REDUCED
                    ? "Capacidade reduzida"
                    : "Ritmo normal";
            applyDailyPlanCapacityMode(restored.capacity());
            String essentialTitle = dailyPlanEssentialTask != null
                    ? dailyPlanEssentialTask.title()
                    : "Tarefa essencial indisponível";
            dailyPlanPanel.setEssentialAvailable(dailyPlanEssentialTask != null);
            if (restored.closedAt() != null) {
                dailyPlanPanel.showClosedPlan(
                        capacity, essentialTitle, supportTitles);
            } else {
                dailyPlanPanel.showPlan(
                        capacity, essentialTitle, supportTitles);
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            applyDailyPlanCapacityMode(DailyPlanCapacity.NORMAL);
            dailyPlanPanel.showError();
        }
    }

    private void openDayReview() {
        new DayReviewWindow(dayReviewService, LocalDate.now(), () -> {
            updateDailyPlanPanel();
            updateFocusNowPanel();
            ctx.triggerInboxRefresh();
        }).show();
    }

    private void applyDailyPlanCapacityMode(DailyPlanCapacity capacity) {
        if (dashboardContent == null) return;
        boolean reduced = capacity == DailyPlanCapacity.REDUCED;
        if (reduced && !dashboardContent.getStyleClass().contains("reduced-capacity-dashboard")) {
            dashboardContent.getStyleClass().add("reduced-capacity-dashboard");
        } else if (!reduced) {
            dashboardContent.getStyleClass().remove("reduced-capacity-dashboard");
        }
    }

    private void beginDailyPlanning() {
        try {
            LocalDate today = LocalDate.now();
            List<DailyPlanPanel.TaskOption> candidates = taskRepository.findOpenTasks().stream()
                    .map(task -> new DailyPlanPanel.TaskOption(
                            task.id(), task.title(), formatPlanningTaskDetail(task, today)))
                    .toList();
            List<String> todayItems = taskRepository.findByDay(today, null).stream()
                    .filter(task -> !task.done())
                    .map(task -> {
                        String time = task.startTime() == null || task.startTime().isBlank()
                                ? "Sem horário"
                                : task.startTime();
                        return time + " · " + task.title();
                    })
                    .toList();
            Long essentialTaskId = currentDailyPlan == null
                    ? null
                    : currentDailyPlan.essentialItem().map(item -> item.taskId()).orElse(null);
            List<Long> supportTaskIds = currentDailyPlan == null
                    ? List.of()
                    : currentDailyPlan.supportItems().stream().map(item -> item.taskId()).toList();
            DailyPlanCapacity capacity = currentDailyPlan == null
                    ? DailyPlanCapacity.NORMAL
                    : currentDailyPlan.capacity();

            dailyPlanPanel.beginPlanning(new DailyPlanPanel.PlanningRequest(
                    candidates, todayItems, capacity, essentialTaskId, supportTaskIds));
            ctx.setStatus(candidates.isEmpty()
                    ? "Crie uma tarefa aberta antes de montar o plano do dia."
                    : "Planejamento do dia iniciado. Nada será salvo antes da confirmação.");
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            dailyPlanPanel.showError();
        }
    }

    private void saveDailyPlan(DailyPlanPanel.PlanSelection selection) {
        try {
            DailyPlan saved = dailyPlanService.savePlan(
                    LocalDate.now(), selection.capacity(),
                    selection.essentialTaskId(), selection.supportTaskIds());
            updateDailyPlanPanel();
            updateFocusNowPanel();
            ctx.setStatus("Plano de hoje salvo.");
            if (selection.openAfterSave()) {
                taskRepository.findById(saved.essentialItem().orElseThrow().taskId())
                        .ifPresent(task -> openTaskByDate(task.effectiveEndDate(), task.id()));
            }
        } catch (IllegalArgumentException ex) {
            dailyPlanPanel.showPlanningError(ex.getMessage());
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            dailyPlanPanel.showPlanningError("Não foi possível salvar o plano. Tente novamente.");
        }
    }

    private String formatPlanningTaskDetail(Task task, LocalDate today) {
        String date;
        if (task.isActiveOn(today)) {
            date = "Hoje";
        } else if (task.effectiveEndDate().isBefore(today)) {
            date = "Pendente há " + ChronoUnit.DAYS.between(task.effectiveEndDate(), today) + " dia(s)";
        } else {
            date = task.effectiveEndDate().toString();
        }
        String priority = task.priority() == null ? "Normal" : task.priority().label();
        String category = task.category() == null || task.category().isBlank() ? "Geral" : task.category();
        return date + " · " + priority + " · " + category;
    }

    private void openDailyPlanEssential() {
        Task task = dailyPlanEssentialTask;
        if (task == null) {
            updateDailyPlanPanel();
            return;
        }
        openTaskByDate(task.effectiveEndDate(), task.id());
    }

    private void updateTodayTasks() {
        try {
            LocalDate today = LocalDate.now();
            var tasks = AppContextHolder.get().taskRepository().findByDay(today, null);
            ctx.todayTaskItems.clear();
            int count = 0;
            for (Task task : tasks) {
                String text = "📌 " + task.title();
                if (task.linkedProtocolId() != null) {
                    String protocolName = AppContextHolder.get().protocolRepository()
                            .findProtocolById(task.linkedProtocolId())
                            .map(Protocol::name)
                            .orElse(null);
                    text = appendProtocolHint(text, protocolName);
                }
                ctx.todayTaskItems.add(text);
                count++;
            }
            ctx.tasksDueCountLabel.setText(String.valueOf(count));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateIdeaInbox() {
        try {
            ProjectIdeaRepository repo = AppContextHolder.get().projectIdeaRepository();
            ArrayList<IdeaInboxItem> items = new ArrayList<>();
            for (ProjectIdea idea : repo.findInboxIdeas(8)) {
                String parentTitle = idea.parentIdeaId() != null ? repo.findTitleById(idea.parentIdeaId()) : null;
                items.add(new IdeaInboxItem(
                        idea.id(),
                        idea.title(),
                        idea.category() != null ? idea.category() : "Geral",
                        idea.priorityLabel(),
                        parentTitle
                ));
            }
            ideaInboxItems.setAll(items);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateExpiringProtocols() {
        try {
            // Busca protocolos que vencem em até 3 dias
            var repo = AppContextHolder.get().protocolRepository();
            var allProtocols = repo.findAllProtocols(null, null, null);

            ctx.expiringProtocolItems.clear();
            int count = 0;
            // Mostra protocolos periódicos (que precisam ser executados novamente)
            for (var p : allProtocols) {
                if (p.hasValidity()) {
                    // Protocolo periódico — sugerindo execução
                    ctx.expiringProtocolItems.add("📌 " + p.name() + " (" + p.validityDays() + " dias)");
                    count++;
                    if (count >= 5) break; // Limita a 5 sugestões
                }
            }
            ctx.protocolsExpiringCountLabel.setText(String.valueOf(count));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateFocusNowPanel() {
        if (focusNowTitleLabel == null || focusNowDetailLabel == null || focusOpenBtn == null) {
            return;
        }

        Optional<ResumeFocus> resumeFocus = findResumeFocus();
        Optional<FocusedTask> focusTask = resumeFocus.isEmpty()
                ? chooseFocusTask()
                : Optional.empty();
        String title;
        String detail;
        String actionText;

        if (resumeFocus.isPresent()) {
            ResumeFocus resume = resumeFocus.get();
            TaskReminderItem item = resume.item();
            currentFocusTask = item;
            currentFocusOrigin = null;
            currentResumeContext = resume.context();
            focusNowModeLabel.setText("Retomada pendente");
            title = item.title();
            detail = "Onde você parou: " + resume.context().resumeNote();
            actionText = "Abrir tarefa";
            focusNowAction = () -> openTaskReminder(item);
        } else if (focusTask.isPresent()) {
            FocusedTask focused = focusTask.get();
            TaskReminderItem item = focused.item();
            currentFocusTask = item;
            currentFocusOrigin = focused.origin();
            currentResumeContext = null;
            focusNowModeLabel.setText(switch (focused.origin()) {
                case TIMER -> "Timer em andamento";
                case MANUAL -> "Escolhido por você";
                case DAILY_PLAN -> "Plano de hoje";
                case AUTOMATIC -> "Sugestão automática";
            });
            title = item.title();
            detail = formatTaskReminderDetail(item);
            actionText = "Abrir tarefa";
            focusNowAction = () -> openTaskReminder(item);
        } else if (!ctx.alertItems.isEmpty() && ctx.alertItems.get(0).startsWith("Pagamento pendente:")) {
            currentFocusTask = null;
            currentFocusOrigin = null;
            currentResumeContext = null;
            focusNowModeLabel.setText("Sugestão automática");
            title = "Resolver pagamento pendente";
            detail = ctx.alertItems.get(0);
            actionText = "Ir para Financeiro";
            focusNowAction = () -> {
                if (tabNavigator != null) tabNavigator.accept(3);
            };
        } else if (!ctx.todayTaskItems.isEmpty()) {
            currentFocusTask = null;
            currentFocusOrigin = null;
            currentResumeContext = null;
            focusNowModeLabel.setText("Sugestão automática");
            title = "Sua próxima tarefa de hoje";
            detail = ctx.todayTaskItems.get(0);
            actionText = "Abrir Agenda";
            focusNowAction = () -> openTaskByDate(LocalDate.now(), null);
        } else if (!ctx.expiringProtocolItems.isEmpty()) {
            currentFocusTask = null;
            currentFocusOrigin = null;
            currentResumeContext = null;
            focusNowModeLabel.setText("Sugestão automática");
            title = "Protocolo para revisar";
            detail = ctx.expiringProtocolItems.get(0);
            actionText = "Abrir Protocolos";
            focusNowAction = () -> {
                if (tabNavigator != null) tabNavigator.accept(2);
            };
        } else {
            currentFocusTask = null;
            currentFocusOrigin = null;
            currentResumeContext = null;
            focusNowModeLabel.setText("Sem urgências agora");
            title = "Tudo está em ordem agora";
            detail = "Ótimo momento para capturar novas ideias ou revisar o plano do dia sem pressão.";
            actionText = "Abrir Agenda";
            focusNowAction = () -> {
                if (tabNavigator != null) tabNavigator.accept(1);
            };
        }

        focusNowTitleLabel.setText(title);
        focusNowDetailLabel.setText(detail);
        focusOpenBtn.setText(actionText);
        boolean resumePending = currentResumeContext != null;
        if (resumePending && !focusNowBox.getStyleClass().contains("resume-pending")) {
            focusNowBox.getStyleClass().add("resume-pending");
        } else if (!resumePending) {
            focusNowBox.getStyleClass().remove("resume-pending");
        }
        focusStartBtn.setText(resumePending
                ? "Retomar"
                : currentFocusOrigin == FocusSelectionService.Origin.TIMER
                        ? "Continuar foco"
                        : "Iniciar foco");
        focusStartBtn.setTooltip(new Tooltip(resumePending
                ? "Retoma a tarefa e remove esta pista do bloco Agora."
                : "Abre o timer diretamente para a tarefa escolhida."));
        focusOpenBtn.setDisable(tabNavigator == null && taskNavigator == null);
        focusStartBtn.setDisable(currentFocusTask == null);
        UIHelper.setConditionalVisible(focusStartBtn, currentFocusTask != null);
        focusChooseBtn.setDisable(focusCandidates.isEmpty());
        UIHelper.setConditionalVisible(focusChooseBtn, !resumePending);
        UIHelper.setConditionalVisible(focusAutomaticBtn,
                !resumePending && pinnedFocusTaskId() > 0);
    }

    public void refreshFocusNow() {
        updateFocusNowPanel();
    }

    private Optional<ResumeFocus> findResumeFocus() {
        try {
            return focusContextService.current().flatMap(context ->
                    taskRepository.findById(context.taskId())
                            .map(task -> new ResumeFocus(context, toTaskReminderItem(task))));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private void updateTaskReminderPanels() {
        updateTaskReminderPanels(taskRepository.findOpenTasks(), LocalDate.now());
    }

    void updateTaskReminderPanels(List<Task> tasks, LocalDate today) {
        try {
            ArrayList<TaskReminderItem> active = new ArrayList<>();
            Map<OverdueAgeBand, ArrayList<TaskReminderItem>> overdueGroups = new java.util.EnumMap<>(
                    OverdueAgeBand.class);
            for (OverdueAgeBand band : OverdueAgeBand.values()) {
                overdueGroups.put(band, new ArrayList<>());
            }
            ArrayList<TaskReminderItem> candidates = new ArrayList<>();
            for (Task task : tasks) {
                LocalDate anchorDate = task.effectiveEndDate();
                boolean overdue = anchorDate.isBefore(today);
                boolean dueToday = task.isDueToday();

                String linkedProtocolName = null;
                if (task.linkedProtocolId() != null) {
                    linkedProtocolName = AppContextHolder.get().protocolRepository()
                            .findProtocolById(task.linkedProtocolId())
                            .map(Protocol::name)
                            .orElse(null);
                }

                long overdueDays = overdue ? ChronoUnit.DAYS.between(anchorDate, today) : 0;
                TaskReminderItem item = new TaskReminderItem(
                        task.id(),
                        task.title(),
                        anchorDate,
                        task.priority(),
                        overdueDays,
                        dueToday,
                        overdue,
                        overdue && overdueDays > 30,
                        task.category(),
                        linkedProtocolName
                );
                candidates.add(item);
                if (overdue) {
                    overdueGroups.get(OverdueAgeBand.fromPendingDays(overdueDays)).add(item);
                } else if (dueToday) {
                    active.add(item);
                }
            }

            active.sort(Comparator
                    .comparingInt(this::taskReminderScore)
                    .reversed()
                    .thenComparing(TaskReminderItem::anchorDate, Comparator.reverseOrder())
                    .thenComparing(TaskReminderItem::title));

            Comparator<TaskReminderItem> reviewOrder = Comparator
                    .comparing(TaskReminderItem::anchorDate, Comparator.reverseOrder())
                    .thenComparing(TaskReminderItem::title);
            overdueGroups.values().forEach(group -> group.sort(reviewOrder));

            candidates.sort(Comparator
                    .comparingInt(this::taskReminderScore)
                    .reversed()
                    .thenComparing(TaskReminderItem::anchorDate)
                    .thenComparing(TaskReminderItem::title));

            highlightedTaskItems.setAll(active.stream().limit(8).toList());
            overdueUpTo7Items.setAll(overdueGroups.get(OverdueAgeBand.UP_TO_7_DAYS));
            overdue8To30Items.setAll(overdueGroups.get(OverdueAgeBand.DAYS_8_TO_30));
            overdueOver30Items.setAll(overdueGroups.get(OverdueAgeBand.OVER_30_DAYS));
            updateOverdueTabLabels();
            focusCandidates = List.copyOf(candidates);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Optional<FocusedTask> chooseFocusTask() {
        Map<Long, TaskReminderItem> available = new LinkedHashMap<>();
        focusCandidates.forEach(item -> available.put(item.taskId(), item));

        Long activeTimerTaskId = TaskTimerService.get().getActiveTaskId();
        if (activeTimerTaskId != null && !available.containsKey(activeTimerTaskId)) {
            taskRepository.findById(activeTimerTaskId)
                    .map(this::toTaskReminderItem)
                    .ifPresent(item -> available.put(item.taskId(), item));
        }

        long pinnedTaskId = pinnedFocusTaskId();
        if (pinnedTaskId > 0 && !available.containsKey(pinnedTaskId)) {
            preferences.remove(FOCUS_TASK_PREF);
            pinnedTaskId = -1;
        }

        TaskReminderItem criticalOverdue = overdueReviewItems().stream()
                .filter(item -> item.priority() == TaskPriority.CRITICA)
                .findFirst()
                .orElse(null);
        ArrayList<Long> automaticIds = new ArrayList<>();
        if (!highlightedTaskItems.isEmpty()) automaticIds.add(highlightedTaskItems.getFirst().taskId());
        if (criticalOverdue != null) automaticIds.add(criticalOverdue.taskId());
        focusCandidates.stream()
                .filter(item -> !item.overdue() || item.priority() == TaskPriority.CRITICA)
                .map(TaskReminderItem::taskId)
                .filter(id -> !automaticIds.contains(id))
                .forEach(automaticIds::add);

        Long dailyPlanTaskId = currentDailyPlan == null
                ? null
                : currentDailyPlan.essentialItem().map(item -> item.taskId()).orElse(null);
        if (dailyPlanEssentialTask != null && dailyPlanTaskId != null
                && !available.containsKey(dailyPlanTaskId)) {
            TaskReminderItem item = toTaskReminderItem(dailyPlanEssentialTask);
            available.put(item.taskId(), item);
        }

        return focusSelectionService.select(
                        activeTimerTaskId,
                        pinnedTaskId > 0 ? pinnedTaskId : null,
                        dailyPlanTaskId,
                        automaticIds,
                        Set.copyOf(available.keySet()))
                .map(selection -> new FocusedTask(
                        available.get(selection.taskId()), selection.origin()));
    }

    private TaskReminderItem toTaskReminderItem(Task task) {
        LocalDate today = LocalDate.now();
        LocalDate anchorDate = task.effectiveEndDate();
        boolean overdue = anchorDate.isBefore(today);
        long overdueDays = overdue ? ChronoUnit.DAYS.between(anchorDate, today) : 0;
        String linkedProtocolName = task.linkedProtocolId() == null
                ? null
                : AppContextHolder.get().protocolRepository()
                        .findProtocolById(task.linkedProtocolId())
                        .map(Protocol::name)
                        .orElse(null);
        return new TaskReminderItem(
                task.id(), task.title(), anchorDate, task.priority(), overdueDays,
                task.isDueToday(), overdue, overdue && overdueDays > 30,
                task.category(), linkedProtocolName);
    }

    private long pinnedFocusTaskId() {
        return preferences.getLong(FOCUS_TASK_PREF, -1L);
    }

    private void chooseFocusManually() {
        if (focusCandidates.isEmpty()) {
            Dialogs.info("Escolher foco", "Não há tarefas abertas disponíveis para escolher.");
            return;
        }

        TaskReminderItem initial = currentFocusTask != null && focusCandidates.contains(currentFocusTask)
                ? currentFocusTask
                : focusCandidates.getFirst();
        ChoiceDialog<TaskReminderItem> dialog = new ChoiceDialog<>(initial, focusCandidates);
        Dialogs.prepare(dialog);
        dialog.setTitle("Escolher foco");
        dialog.setHeaderText("Qual tarefa merece o seu campo de atenção agora?");
        dialog.setContentText(null);
        dialog.getDialogPane().setPrefWidth(560);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Selecionar");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        dialog.showAndWait().ifPresent(item -> {
            preferences.putLong(FOCUS_TASK_PREF, item.taskId());
            updateFocusNowPanel();
            ctx.setStatus("Foco definido: " + item.title());
        });
    }

    private void clearManualFocus() {
        preferences.remove(FOCUS_TASK_PREF);
        updateFocusNowPanel();
        ctx.setStatus("Seleção automática de foco restaurada.");
    }

    private void startCurrentFocus() {
        TaskReminderItem item = currentFocusTask;
        if (item == null) return;
        boolean completingResume = currentResumeContext != null
                && currentResumeContext.taskId() == item.taskId();
        int resumeActions = completingResume ? registerResumeAttempt(item.taskId()) : 0;
        taskRepository.findById(item.taskId()).ifPresentOrElse(
                task -> {
                    try {
                        TaskTimerService timerService = TaskTimerService.get();
                        if (!Long.valueOf(task.id()).equals(timerService.getActiveTaskId())) {
                            timerService.start(task.id());
                        } else if (!timerService.isRunning()) {
                            timerService.resume();
                        }
                        timerWindowOpener.accept(task);
                        localMetricsService.recordFocusAction();
                        if (completingResume) {
                            focusContextService.completeResume(task.id());
                            localMetricsService.recordInterruptionResume(resumeActions);
                            resumeAttemptTaskId = null;
                            resumeActionAttempts = 0;
                            ctx.setStatus("Retomada iniciada: " + task.title());
                        }
                        updateFocusNowPanel();
                        if (localMetricsPanel != null) localMetricsPanel.refresh();
                    } catch (RuntimeException error) {
                        Dialogs.error("Não foi possível iniciar o foco",
                                "A pista foi preservada. Tente novamente.");
                    }
                },
                () -> {
                    if (!completingResume) preferences.remove(FOCUS_TASK_PREF);
                    Dialogs.warning("Tarefa não encontrada",
                            "A tarefa escolhida já não está disponível.");
                    ctx.triggerDashboardRefresh();
                });
    }

    private ListView<TaskReminderItem> buildTaskReminderList(ObservableList<TaskReminderItem> items, String tooltipText) {
        ListView<TaskReminderItem> list = new ListView<>(items);
        list.getStyleClass().add("clean-list");
        list.setPrefHeight(135);
        list.setMinHeight(96);
        list.setPlaceholder(new Label("Nenhuma tarefa para mostrar aqui agora."));
        VBox.setVgrow(list, Priority.ALWAYS);
        Tooltip.install(list, new Tooltip(tooltipText));
        list.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLabel = new Label();
            private final Label metaLabel = new Label();
            private final VBox box = new VBox(2, titleLabel, metaLabel);
            {
                titleLabel.getStyleClass().add("t-heading-sm");
                metaLabel.getStyleClass().add("t-muted");
                titleLabel.setWrapText(true);
                metaLabel.setWrapText(true);
            }

            @Override
            protected void updateItem(TaskReminderItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                titleLabel.setText(item.title());
                metaLabel.setText(formatTaskReminderMeta(item));
                setGraphic(box);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TaskReminderItem item = list.getSelectionModel().getSelectedItem();
                if (item != null) openTaskReminder(item);
            }
        });
        return list;
    }

    private String formatTaskReminderMeta(TaskReminderItem item) {
        String priority = item.priority() != null ? item.priority().label() : "Normal";
        String category = item.category() != null && !item.category().isBlank() ? item.category() : "Geral";
        if (item.dueToday()) {
            return appendProtocolHint("%s · %s · hoje".formatted(priority, category), item.linkedProtocolName());
        }
        if (item.overdue()) {
            return appendProtocolHint("%s · %s · %s · pendente há %d dia(s)".formatted(
                    priority,
                    category,
                    item.anchorDate(),
                    item.overdueDays()), item.linkedProtocolName());
        }
        return appendProtocolHint("%s · %s · %s".formatted(priority, category, item.anchorDate()), item.linkedProtocolName());
    }

    private String formatTaskReminderDetail(TaskReminderItem item) {
        String priority = item.priority() != null ? item.priority().label() : "Normal";
        if (item.dueToday()) {
            return appendProtocolHint("%s · acontece hoje · %s · %s".formatted(
                    item.anchorDate(), item.category(), priority), item.linkedProtocolName());
        }
        if (!item.overdue()) {
            return appendProtocolHint("%s · programada · %s · %s".formatted(
                    item.anchorDate(), item.category(), priority), item.linkedProtocolName());
        }
        return appendProtocolHint("%s · pendente há %d dia(s) · %s · %s".formatted(
                item.anchorDate(), item.overdueDays(), item.category(), priority), item.linkedProtocolName());
    }

    private static String appendProtocolHint(String base, String protocolName) {
        return protocolName == null || protocolName.isBlank() ? base : base + " · 🔗 " + protocolName;
    }

    private int taskReminderScore(TaskReminderItem item) {
        int score = priorityWeight(item.priority());
        if (item.dueToday()) score += 500;
        if (item.overdue()) {
            if (item.overdueDays() <= 3) score += 320;
            else if (item.overdueDays() <= 7) score += 250;
            else if (item.overdueDays() <= 30) score += 180;
            else if (item.overdueDays() <= 45) score += 120;
        }
        if (item.longPending()) score -= 240;
        return score;
    }

    private List<TaskReminderItem> overdueReviewItems() {
        return java.util.stream.Stream.of(
                        overdueUpTo7Items, overdue8To30Items, overdueOver30Items)
                .flatMap(List::stream)
                .toList();
    }

    private void updateOverdueTabLabels() {
        if (overdueUpTo7Tab == null) return;
        overdueUpTo7Tab.setText(OverdueAgeBand.UP_TO_7_DAYS.label()
                + " (" + overdueUpTo7Items.size() + ")");
        overdue8To30Tab.setText(OverdueAgeBand.DAYS_8_TO_30.label()
                + " (" + overdue8To30Items.size() + ")");
        overdueOver30Tab.setText(OverdueAgeBand.OVER_30_DAYS.label()
                + " (" + overdueOver30Items.size() + ")");
    }

    private static int priorityWeight(TaskPriority priority) {
        if (priority == null) return 200;
        return switch (priority) {
            case CRITICA -> 400;
            case ALTA -> 300;
            case NORMAL -> 200;
            case BAIXA -> 100;
        };
    }

    private void openTaskReminder(TaskReminderItem item) {
        if (item == null) return;
        openTaskByDate(item.anchorDate(), item.taskId());
    }

    private void openCurrentFocusDetails() {
        focusNowAction.run();
        if (currentFocusTask != null) localMetricsService.recordFocusAction();
        if (localMetricsPanel != null) localMetricsPanel.refresh();
    }

    private int registerResumeAttempt(long taskId) {
        if (!Long.valueOf(taskId).equals(resumeAttemptTaskId)) {
            resumeAttemptTaskId = taskId;
            resumeActionAttempts = 0;
        }
        return ++resumeActionAttempts;
    }

    private void openTaskByDate(LocalDate date, Long taskId) {
        if (taskNavigator != null) {
            taskNavigator.accept(date, taskId);
            return;
        }
        if (tabNavigator != null) {
            tabNavigator.accept(1);
        }
    }

    private void openIdeaReview(Long ideaId) {
        new IdeaInboxReviewWindow(
                () -> {
                    updateIdeaInbox();
                    ctx.triggerDashboardRefresh();
                },
                ctx::setStatus
        ).show(ideaId);
    }

    private void updateStudyToday() {
        try {
            var studyPlanRepo = AppContextHolder.get().studyPlanRepository();
            var attSvc = AppContextHolder.get().studyAttendanceService();
            LocalDate today = LocalDate.now();
            java.util.List<StudyTodayItem> items = new ArrayList<>();
            for (var plan : studyPlanRepo.findActiveForToday()) {
                com.pessoal.agenda.service.StudyAttendanceService.Summary sum =
                        attSvc.getSummary(plan.id(), today.withDayOfMonth(1), today);
                items.add(new StudyTodayItem(
                        plan.id(),
                        plan.title(),
                        plan.category() != null ? plan.category() : "Geral",
                        plan.progressDisplay(),
                        sum.presenceRate()));
            }
            studyTodayItems.setAll(items);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openStudyDiary(long planId) {
        AppContextHolder.get().studyPlanRepository().findById(planId).ifPresent(plan ->
                new com.pessoal.agenda.ui.view.StudyDiaryWindow(
                        plan,
                        AppContextHolder.get().studyPlanRepository(),
                        AppContextHolder.get().studyEntryRepository(),
                        ctx::triggerDashboardRefresh,
                        ctx
                ).show());
    }

    private void saveQuickIdeaCapture() {
        String raw = quickIdeaNotesArea != null ? quickIdeaNotesArea.getText() : null;
        String titleHint = quickIdeaTitleField != null ? quickIdeaTitleField.getText().trim() : "";
        List<String> blocks = splitCaptureBlocks(raw);
        if (blocks.isEmpty()) {
            ctx.setStatus("Escreva pelo menos uma ideia para capturar.");
            return;
        }

        ProjectIdeaRepository repo = AppContextHolder.get().projectIdeaRepository();
        int created = 0;
        Long parentId = null;

        if (blocks.size() > 1) {
            String parentTitle = !titleHint.isBlank()
                    ? titleHint
                    : "Captura rápida " + java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").format(LocalDateTime.now());
            ProjectIdea group = new ProjectIdea(
                    0,
                    parentTitle,
                    "Captura agrupada gerada na dashboard para triagem posterior.",
                    "nova",
                    "Caixa de entrada",
                    "NORMAL",
                    "GERAL",
                    "MEDIO",
                    3,
                    0,
                    LocalDate.now(),
                    null,
                    null,
                    "Revisar subtópicos, reparentear se necessário e priorizar.",
                    "captura-rápida, dashboard",
                    null,
                    null
            );
            parentId = repo.saveFullIdea(group);
            created++;
        }

        for (int i = 0; i < blocks.size(); i++) {
            String block = blocks.get(i);
            String derivedTitle = (blocks.size() == 1)
                    ? deriveCaptureTitle(titleHint, block, 1)
                    : deriveCaptureTitle("", block, i + 1);
            ProjectIdea captured = new ProjectIdea(
                    0,
                    derivedTitle,
                    block,
                    "nova",
                    "Caixa de entrada",
                    "NORMAL",
                    "GERAL",
                    "MEDIO",
                    3,
                    0,
                    LocalDate.now(),
                    null,
                    null,
                    "Revisar, priorizar e transformar em projeto/tarefa se fizer sentido.",
                    "captura-rápida, dashboard",
                    null,
                    parentId
            );
            repo.saveFullIdea(captured);
            created++;
        }

        if (quickIdeaTitleField != null) quickIdeaTitleField.clear();
        if (quickIdeaNotesArea != null) quickIdeaNotesArea.clear();
        updateIdeaInbox();
        ctx.triggerDashboardRefresh();
        ctx.setStatus(created == 1
                ? "Ideia salva para revisão."
                : "Ideias salvas e agrupadas para revisão (" + created + " itens)." );
    }

    private static List<String> splitCaptureBlocks(String raw) {
        ArrayList<String> blocks = new ArrayList<>();
        if (raw == null || raw.isBlank()) return blocks;
        for (String block : raw.trim().split("(?:\\R\\s*){2,}")) {
            String normalized = block.trim();
            if (!normalized.isBlank()) blocks.add(normalized);
        }
        return blocks;
    }

    private static String deriveCaptureTitle(String titleHint, String block, int index) {
        if (titleHint != null && !titleHint.isBlank()) return truncateIdeaTitle(titleHint.trim());
        String firstLine = block.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("Captura " + index);
        return truncateIdeaTitle(firstLine);
    }

    private static String truncateIdeaTitle(String title) {
        if (title == null || title.isBlank()) return "Captura rápida";
        String normalized = title.length() > 80 ? title.substring(0, 80).trim() + "…" : title.trim();
        return normalized.isBlank() ? "Captura rápida" : normalized;
    }

    private Optional<LocalDate> extractFirstIsoDate(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
        if (matcher.find()) {
            return Optional.of(LocalDate.parse(matcher.group(1)));
        }
        return Optional.empty();
    }

    private void updateProtocolsNow() {
        try {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now().withSecond(0).withNano(0);
            var repo = AppContextHolder.get().protocolRepository();
            var taskRepo = AppContextHolder.get().taskRepository();
            java.util.Map<Long, Task> todayTasksByProtocol = new java.util.HashMap<>();
            for (Task task : taskRepo.findByDay(today, null)) {
                if (task.linkedProtocolId() != null) {
                    todayTasksByProtocol.putIfAbsent(task.linkedProtocolId(), task);
                }
            }
            ArrayList<ProtocolNowItem> items = new ArrayList<>();
            for (Protocol protocol : repo.findAllProtocols(null, null, null)) {
                int score = 0;
                String reason = null;
                int active = repo.countActiveExecutionsOf(protocol.id());
                if (active > 0) {
                    score += 500;
                    reason = "Execução ativa em andamento";
                }

                Task linkedTask = null;

                if (protocol.linkedTaskId() != null) {
                    var maybeLinkedTask = taskRepo.findById(protocol.linkedTaskId());
                    if (maybeLinkedTask.isPresent()) {
                        linkedTask = maybeLinkedTask.get();
                    }
                    if (maybeLinkedTask.isPresent() && maybeLinkedTask.get().isDueToday()) {
                        score += 350;
                        reason = reason == null ? "Ligado a tarefa de hoje" : reason + " · tarefa de hoje";
                    }
                }

                Task todayTaskByAssociation = todayTasksByProtocol.get(protocol.id());
                if (todayTaskByAssociation != null) {
                    score += 340;
                    reason = reason == null
                            ? "Associado à tarefa de hoje: " + todayTaskByAssociation.title()
                            : reason + " · tarefa de hoje: " + todayTaskByAssociation.title();
                }

                Task timingReferenceTask = todayTaskByAssociation != null ? todayTaskByAssociation : linkedTask;
                if (isTimingCategory(protocol.category())) {
                    TimedProtocolSignal signal = timedProtocolSignal(protocol, timingReferenceTask, now);
                    if (signal.score() > 0) {
                        score += signal.score();
                        reason = reason == null ? signal.reason() : reason + " · " + signal.reason();
                    }
                }

                int keywordBoost = protocolKeywordBoost(protocol.name());
                if (keywordBoost > 0) {
                    score += keywordBoost;
                    String keywordReason = protocolKeywordReason(protocol.name());
                    reason = reason == null ? keywordReason : reason + " · " + keywordReason;
                }

                if (protocol.hasValidity()) {
                    LocalDate nextDue = repo.nextDueDate(protocol.id(), protocol.validityDays());
                    if (nextDue != null) {
                        long days = ChronoUnit.DAYS.between(today, nextDue);
                        if (days <= 1) {
                            score += 280;
                            reason = reason == null ? "Expira hoje/amanhã" : reason + " · expira hoje/amanhã";
                        } else if (days <= 3) {
                            score += 180;
                            reason = reason == null ? "Expira em breve" : reason + " · expira em breve";
                        }
                    }
                }

                if (score > 0) {
                    items.add(new ProtocolNowItem(protocol, reason != null ? reason : "Útil agora", score));
                }
            }
            items.sort(Comparator.comparingInt(ProtocolNowItem::score).reversed()
                    .thenComparing(item -> item.protocol().name()));
            protocolNowItems.setAll(items.stream().limit(6).toList());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static boolean isTimingCategory(String category) {
        if (category == null || category.isBlank()) return false;
        String normalized = category
                .toLowerCase()
                .replace('á', 'a').replace('à', 'a').replace('â', 'a').replace('ã', 'a')
                .replace('é', 'e').replace('ê', 'e')
                .replace('í', 'i')
                .replace('ó', 'o').replace('ô', 'o').replace('õ', 'o')
                .replace('ú', 'u')
                .replace('ç', 'c');
        return normalized.contains("horario");
    }

    private TimedProtocolSignal timedProtocolSignal(Protocol protocol, Task referenceTask, LocalTime now) {
        int bestScore = 0;
        String bestReason = null;

        java.util.List<LocalTime> fixedTimes = new java.util.ArrayList<>();
        if (protocol.hasFixedTime()) {
            try {
                fixedTimes.add(LocalTime.parse(protocol.fixedTime()));
            } catch (Exception ignored) {
                // fallback abaixo para protocolos antigos/valor inválido
            }
        }
        if (fixedTimes.isEmpty()) {
            fixedTimes.addAll(extractFixedTimes(protocol));
        }

        for (LocalTime fixedTime : fixedTimes) {
            long delta = ChronoUnit.MINUTES.between(now, fixedTime);
            if (Math.abs(delta) <= 10) {
                int score = 520;
                String reason = "Janela do horário " + fixedTime;
                if (score > bestScore) {
                    bestScore = score;
                    bestReason = reason;
                }
            } else if (delta > 0 && delta <= 60) {
                int score = 310;
                String reason = "Dispara às " + fixedTime + " (em " + delta + " min)";
                if (score > bestScore) {
                    bestScore = score;
                    bestReason = reason;
                }
            }
        }

        Integer leadMinutes = protocol.hasLeadMinutes() ? protocol.leadMinutes() : extractLeadMinutes(protocol);
        if (leadMinutes != null && referenceTask != null && referenceTask.startTime() != null) {
            try {
                LocalTime eventStart = LocalTime.parse(referenceTask.startTime());
                LocalTime triggerTime = eventStart.minusMinutes(leadMinutes);
                long delta = ChronoUnit.MINUTES.between(now, triggerTime);
                if (Math.abs(delta) <= 10) {
                    int score = 560;
                    String reason = "Hora de preparar: " + referenceTask.title() + " (evento às " + eventStart + ")";
                    if (score > bestScore) {
                        bestScore = score;
                        bestReason = reason;
                    }
                } else if (delta > 0 && delta <= 90) {
                    int score = 300;
                    String reason = "Preparar em " + delta + " min para " + referenceTask.title();
                    if (score > bestScore) {
                        bestScore = score;
                        bestReason = reason;
                    }
                }
            } catch (Exception ignored) {
                // Horário inválido na tarefa vinculada: ignora gatilho por antecedência.
            }
        }

        return new TimedProtocolSignal(bestScore, bestReason != null ? bestReason : "Horário configurado");
    }

    private static java.util.List<LocalTime> extractFixedTimes(Protocol protocol) {
        java.util.ArrayList<LocalTime> times = new java.util.ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");
        String source = (protocol.name() != null ? protocol.name() : "") + " "
                + (protocol.description() != null ? protocol.description() : "");
        java.util.regex.Matcher m = p.matcher(source);
        while (m.find()) {
            try {
                LocalTime t = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                if (!times.contains(t)) times.add(t);
            } catch (Exception ignored) {
                // Continua buscando outros horários válidos.
            }
        }
        return times;
    }

    private static Integer extractLeadMinutes(Protocol protocol) {
        String source = ((protocol.name() != null ? protocol.name() : "") + " "
                + (protocol.description() != null ? protocol.description() : "")).toLowerCase();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*(h|hora|horas|min|minuto|minutos)\\s+antes")
                .matcher(source);
        if (!m.find()) return null;
        int value = Integer.parseInt(m.group(1));
        String unit = m.group(2);
        return unit.startsWith("h") ? value * 60 : value;
    }

    private static int protocolKeywordBoost(String name) {
        if (name == null) return 0;
        String lower = name.toLowerCase();
        if (lower.contains("reméd") || lower.contains("remed") || lower.contains("medic")) return 260;
        if (lower.contains("reuni")) return 230;
        if (lower.contains("saída") || lower.contains("saida") || lower.contains("casa")) return 220;
        return 0;
    }

    private static String protocolKeywordReason(String name) {
        if (name == null) return "rotina do momento";
        String lower = name.toLowerCase();
        if (lower.contains("reméd") || lower.contains("remed") || lower.contains("medic")) return "rotina de remédio";
        if (lower.contains("reuni")) return "apoio a reunião/saída";
        if (lower.contains("saída") || lower.contains("saida") || lower.contains("casa")) return "saída de casa";
        return "rotina do momento";
    }

    private void updateFrequentProtocols() {
        try {
            var repo = AppContextHolder.get().protocolRepository();
            var all = repo.findAllProtocols(null, null, null);
            all.sort(Comparator
                    .comparingInt((Protocol p) -> protocolPriorityBoost(p.name()))
                    .reversed()
                    .thenComparingInt((Protocol p) -> repo.countActiveExecutionsOf(p.id()))
                    .reversed()
                    .thenComparingInt((Protocol p) -> repo.findExecutions(p.id(), null).size())
                    .reversed()
                    .thenComparing(Protocol::name));
            frequentProtocolItems.setAll(all.stream().limit(6).toList());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static int protocolPriorityBoost(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase();
        if (n.contains("saída") || n.contains("saida")) return 3;
        if (n.contains("reuni") || n.contains("reméd") || n.contains("remed")) return 2;
        return 0;
    }

    private void openProtocolExecution(Protocol protocol) {
        if (protocol == null) return;
        new ProtocolExecutionWindow(protocol, AppContextHolder.get().protocolRepository(),
                ctx::triggerDashboardRefresh).show();
    }
}
