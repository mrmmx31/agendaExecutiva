package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.model.Protocol;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import com.pessoal.agenda.ui.view.IdeaInboxReviewWindow;
import com.pessoal.agenda.ui.view.ProjectIdeaDetailWindow;
import javafx.scene.control.Button;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.view.ProtocolExecutionWindow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.Separator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * Controller da aba Dashboard.
 * Exibe KPIs consolidados, próximos prazos críticos e alertas de atraso.
 */
public class DashboardController {

    private final SharedContext    ctx;
    private final DatabaseService  db;

    /**
     * Callback para navegar até uma aba pelo índice.
     * Índices: 0=Dashboard, 1=Agenda, 2=Checklist, 3=Financeiro,
     *          4=Vendas, 5=Estudos, 6=Ideias, 7=Config.
     */
    private IntConsumer tabNavigator;
    private BiConsumer<LocalDate, Long> taskNavigator;

    private Label  focusNowTitleLabel;
    private Label  focusNowDetailLabel;
    private Button focusNowActionBtn;
    private final javafx.collections.ObservableList<Protocol> frequentProtocolItems = javafx.collections.FXCollections.observableArrayList();
    private final ObservableList<ProtocolNowItem> protocolNowItems = FXCollections.observableArrayList();
    private final ObservableList<TaskReminderItem> highlightedTaskItems = FXCollections.observableArrayList();
    private final ObservableList<TaskReminderItem> staleTaskItems = FXCollections.observableArrayList();
    private final ObservableList<IdeaInboxItem> ideaInboxItems = FXCollections.observableArrayList();
    private final ObservableList<StudyTodayItem> studyTodayItems = FXCollections.observableArrayList();
    private Runnable focusNowAction = () -> {};
    private int staleFocusRotation = 0;
    private TextField quickIdeaTitleField;
    private TextArea quickIdeaNotesArea;

    private record TaskReminderItem(long taskId,
                                    String title,
                                    LocalDate anchorDate,
                                    TaskPriority priority,
                                    long overdueDays,
                                    boolean dueToday,
                                    boolean overdue,
                                    boolean stale,
                                    String category,
                                    String linkedProtocolName) {}

    private record ProtocolNowItem(Protocol protocol, String reason, int score) {}

    private record TimedProtocolSignal(int score, String reason) {}

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

    public DashboardController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
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
        cards.getStyleClass().add("dashboard-cards");
        cards.setHgap(12); cards.setVgap(12);
        cards.getChildren().addAll(
                UIHelper.createKpiCard("📋 Tarefas de HOJE",    ctx.tasksDueCountLabel,   "kpi-red"),
                UIHelper.createKpiCard("⚠️ Protocolos vencendo", ctx.protocolsExpiringCountLabel, "kpi-orange"),
                UIHelper.createKpiCard("Tarefas abertas",       ctx.openTasksValue,       "kpi-blue"),
                UIHelper.createKpiCard("Tarefas atrasadas",     ctx.overdueTasksValue,    "kpi-red"),
                UIHelper.createKpiCard("Pagamentos pendentes",  ctx.pendingPaymentsValue, "kpi-orange"),
                UIHelper.createKpiCard("Valor pendente",        ctx.pendingAmountValue,   "kpi-purple"),
                UIHelper.createKpiCard("Checklist pendente",    ctx.checklistPendingValue,"kpi-cyan"),
                UIHelper.createKpiCard("Estudo no mês",         ctx.studyHoursValue,      "kpi-green"),
                UIHelper.createKpiCard("Estoque baixo",         ctx.lowStockValue,        "kpi-red"),
                UIHelper.createKpiCard("Ideias em progresso",   ctx.ideasInProgressValue, "kpi-indigo")
        );

        focusNowTitleLabel = new Label("Carregando foco do dia...");
        focusNowTitleLabel.getStyleClass().add("focus-now-title");

        focusNowDetailLabel = new Label("Assim que os dados forem atualizados, este bloco mostrará a próxima ação.");
        focusNowDetailLabel.getStyleClass().add("focus-now-detail");
        focusNowDetailLabel.setWrapText(true);

        focusNowActionBtn = new Button("Abrir Agenda");
        focusNowActionBtn.getStyleClass().addAll("secondary-button", "focus-now-action");
        focusNowActionBtn.setMaxWidth(Double.MAX_VALUE);
        focusNowActionBtn.setOnAction(e -> {
            if (tabNavigator != null) tabNavigator.accept(1);
        });

        VBox focusNowContent = new VBox(8, focusNowTitleLabel, focusNowDetailLabel, focusNowActionBtn);
        focusNowContent.setFillWidth(true);
        VBox focusNowBox = UIHelper.createCardSection("🎯 1 tarefa principal do dia", focusNowContent);
        focusNowBox.getStyleClass().add("focus-now-card");

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
        ideaInboxList.setPlaceholder(new Label("Nenhuma captura pendente de revisão."));
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
        Tooltip.install(ideaInboxList, new Tooltip("Capturas novas e ideias em caixa de entrada. Duplo clique para abrir a fila dedicada de revisão."));

        Label quickIdeaHint = new Label("💡 Capture sem parar o que está fazendo. Depois você revisa, prioriza e pode ligar uma nota à outra.");
        quickIdeaHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");
        HBox quickIdeaButtons = new HBox(8, saveQuickIdeaBtn, reviewIdeasBtn);
        VBox quickIdeasBox = UIHelper.createCardSection("🧠 Captura rápida de ideias",
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
        studyTodayHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");
        VBox studyTodayBox = UIHelper.createCardSection("📚 Estudos do dia",
                new VBox(6,
                        new VBox(4, new Label("Estudos com frequência programada para hoje."), studyTodayHint),
                        studyTodayList));

        // ── ListView de tarefas de hoje ────────────────────────────────────────
        ListView<String> todayTasksList = new ListView<>(ctx.todayTaskItems);
        todayTasksList.getStyleClass().add("clean-list");
        todayTasksList.setPrefHeight(120);
        todayTasksList.setMinHeight(96);
        VBox.setVgrow(todayTasksList, Priority.ALWAYS);
        Tooltip.install(todayTasksList,
                new Tooltip("Tarefas vencendo hoje. Duplo clique para navegar."));
        todayTasksList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openTaskByDate(LocalDate.now(), null);
            }
        });

        Label todayHint = new Label("💡 Duplo clique para abrir Agenda e Prioridades");
        todayHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");

        VBox todayTasksBox = UIHelper.createCardSection("📋 Tarefas de Hoje",
                new VBox(4,
                        new VBox(4, new Label("Foco nas tarefas de hoje — não deixe escapar!"), todayHint),
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
        protocolHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");

        VBox expiringProtocolsBox = UIHelper.createCardSection("⚠️ Protocolos Vencendo",
                new VBox(4,
                        new VBox(4, new Label("Protocolos próximos do vencimento — execute em breve!"), protocolHint),
                        expiringProtocolsList));

        ListView<TaskReminderItem> highlightedTasksList = buildTaskReminderList(highlightedTaskItems,
                "Tarefas mais recentes/importantes. Duplo clique abre a data exata na Agenda.");
        Label highlightedHint = new Label("💡 Aqui entram primeiro as tarefas mais recentes e prioritárias — as muito antigas ficam separadas.");
        highlightedHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");
        VBox highlightedTasksBox = UIHelper.createCardSection("🎯 Tarefas em destaque",
                new VBox(4,
                        new VBox(4, new Label("Foco do agora: sem deixar tarefas antigas sufocarem o que é mais acionável."), highlightedHint),
                        highlightedTasksList));

        ListView<TaskReminderItem> staleTasksListView = buildTaskReminderList(staleTaskItems,
                "Pendências antigas que continuam existindo. Duplo clique abre a tarefa no dia exato.");
        Label staleHint = new Label("💡 Essas tarefas continuam visíveis, mas em um canto separado; pendências antigas e prioritárias voltam ao foco de tempos em tempos.");
        staleHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");
        VBox staleTasksBox = UIHelper.createCardSection("🕰 Pendências antigas esquecidas",
                new VBox(4,
                        new VBox(4, new Label("Itens antigos para não sumirem, sem atropelar o que é mais recente."), staleHint),
                        staleTasksListView));

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
        protocolNowHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");
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
        frequentHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");

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
                // Formato: "Tarefa atrasada: ..."  ou  "Pagamento pendente: ..."
                if (item.startsWith("Tarefa atrasada:")) {
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
        upcomingHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");

        Label alertsHint = new Label("💡 Duplo clique para navegar à aba correspondente");
        alertsHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");

        VBox upcomingBox = UIHelper.createCardSection("Próximos prazos críticos",
                new VBox(4,
                        new VBox(4, new Label("Visão consolidada de tarefas e pagamentos."), upcomingHint),
                        upcomingList));
        VBox alertsBox = UIHelper.createCardSection("Alertas de atraso",
                new VBox(4,
                        new VBox(4, new Label("Pendências vencidas que exigem ação imediata."), alertsHint),
                        alertsList));
        upcomingBox.setMinHeight(230);
        alertsBox.setMinHeight(230);
        VBox.setVgrow(upcomingBox, Priority.ALWAYS);
        VBox.setVgrow(alertsBox, Priority.ALWAYS);

        HBox bottom = new HBox(12, upcomingBox, alertsBox);
        bottom.setFillHeight(true);
        HBox.setHgrow(upcomingBox, Priority.ALWAYS);
        HBox.setHgrow(alertsBox,   Priority.ALWAYS);

        // ── Layout vertical com seções TDAH no topo ─────────────────────────
        VBox topAlerts = new VBox(8, quickIdeasBox, studyTodayBox, todayTasksBox, highlightedTasksBox, protocolNowBox, expiringProtocolsBox, frequentProtocolsBox, staleTasksBox);
        VBox.setVgrow(quickIdeasBox, Priority.ALWAYS);
        VBox.setVgrow(studyTodayBox, Priority.ALWAYS);
        VBox.setVgrow(todayTasksBox, Priority.ALWAYS);
        VBox.setVgrow(highlightedTasksBox, Priority.ALWAYS);
        VBox.setVgrow(protocolNowBox, Priority.ALWAYS);
        VBox.setVgrow(expiringProtocolsBox, Priority.ALWAYS);
        VBox.setVgrow(frequentProtocolsBox, Priority.ALWAYS);
        VBox.setVgrow(staleTasksBox, Priority.ALWAYS);
        topAlerts.setMinHeight(290);

        HBox tdahSection = new HBox(12, topAlerts);
        HBox.setHgrow(topAlerts, Priority.ALWAYS);

        VBox content = new VBox(12, cards, new Separator(), focusNowBox, tdahSection, bottom);
        VBox.setVgrow(bottom, Priority.ALWAYS);
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
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
        updateFocusNowPanel();

        boolean hasAlertState = overdueCount > 0
                || !ctx.todayTaskItems.isEmpty()
                || !ctx.expiringProtocolItems.isEmpty();
        PendencyNotificationService.getInstance().setHasAlerts(hasAlertState);
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
        if (focusNowTitleLabel == null || focusNowDetailLabel == null || focusNowActionBtn == null) {
            return;
        }

        Optional<TaskReminderItem> focusTask = chooseFocusTask();
        String title;
        String detail;
        String actionText;

        if (focusTask.isPresent()) {
            TaskReminderItem item = focusTask.get();
            title = item.stale() ? "Relembrar pendência esquecida" : item.dueToday() ? "Sua tarefa mais importante de hoje" : "Resolver atraso recente";
            detail = formatTaskReminderDetail(item);
            actionText = "Abrir na data exata";
            focusNowAction = () -> openTaskReminder(item);
        } else if (!ctx.alertItems.isEmpty() && ctx.alertItems.get(0).startsWith("Pagamento pendente:")) {
            title = "Resolver pagamento pendente";
            detail = ctx.alertItems.get(0);
            actionText = "Ir para Financeiro";
            focusNowAction = () -> {
                if (tabNavigator != null) tabNavigator.accept(3);
            };
        } else if (!ctx.todayTaskItems.isEmpty()) {
            title = "Sua próxima tarefa de hoje";
            detail = ctx.todayTaskItems.get(0);
            actionText = "Abrir Agenda";
            focusNowAction = () -> openTaskByDate(LocalDate.now(), null);
        } else if (!ctx.expiringProtocolItems.isEmpty()) {
            title = "Protocolo para revisar";
            detail = ctx.expiringProtocolItems.get(0);
            actionText = "Abrir Protocolos";
            focusNowAction = () -> {
                if (tabNavigator != null) tabNavigator.accept(2);
            };
        } else {
            title = "Tudo está em ordem agora";
            detail = "Ótimo momento para capturar novas ideias ou revisar o plano do dia sem pressão.";
            actionText = "Abrir Agenda";
            focusNowAction = () -> {
                if (tabNavigator != null) tabNavigator.accept(1);
            };
        }

        focusNowTitleLabel.setText(title);
        focusNowDetailLabel.setText(detail);
        focusNowActionBtn.setText(actionText);
        focusNowActionBtn.setDisable(tabNavigator == null && taskNavigator == null);
        focusNowActionBtn.setOnAction(e -> focusNowAction.run());
    }

    private void updateTaskReminderPanels() {
        try {
            LocalDate today = LocalDate.now();
            ArrayList<TaskReminderItem> active = new ArrayList<>();
            ArrayList<TaskReminderItem> stale = new ArrayList<>();
            for (Task task : AppContextHolder.get().taskRepository().findOpenTasks()) {
                LocalDate anchorDate = task.effectiveEndDate();
                boolean overdue = anchorDate.isBefore(today);
                boolean dueToday = task.isDueToday();
                if (!overdue && !dueToday) continue;

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
                        overdue && overdueDays > 45,
                        task.category(),
                        linkedProtocolName
                );
                if (item.stale()) {
                    stale.add(item);
                } else {
                    active.add(item);
                }
            }

            active.sort(Comparator
                    .comparingInt(this::taskReminderScore)
                    .reversed()
                    .thenComparing(TaskReminderItem::anchorDate, Comparator.reverseOrder())
                    .thenComparing(TaskReminderItem::title));

            stale.sort(Comparator
                    .comparingInt((TaskReminderItem item) -> priorityWeight(item.priority()))
                    .reversed()
                    .thenComparing(TaskReminderItem::anchorDate)
                    .thenComparing(TaskReminderItem::title));

            highlightedTaskItems.setAll(active.stream().limit(8).toList());
            staleTaskItems.setAll(stale.stream().limit(8).toList());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Optional<TaskReminderItem> chooseFocusTask() {
        TaskReminderItem stalePriority = staleTaskItems.stream()
                .filter(item -> priorityWeight(item.priority()) >= priorityWeight(TaskPriority.ALTA))
                .findFirst()
                .orElse(null);
        boolean shouldSurfaceStale = stalePriority != null
                && highlightedTaskItems.stream().noneMatch(TaskReminderItem::dueToday)
                && staleFocusRotation++ % 4 == 3;
        if (shouldSurfaceStale) {
            return Optional.of(stalePriority);
        }
        if (!highlightedTaskItems.isEmpty()) {
            return Optional.of(highlightedTaskItems.get(0));
        }
        if (stalePriority != null) {
            return Optional.of(stalePriority);
        }
        return Optional.empty();
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
            return appendProtocolHint("%s · %s · %s · atrasada há %d dia(s)".formatted(
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
            return appendProtocolHint("%s · %s · vence/acontece hoje · %s".formatted(item.title(), item.anchorDate(), priority), item.linkedProtocolName());
        }
        if (item.stale()) {
            return appendProtocolHint("%s · %s · esquecida há %d dia(s) · %s".formatted(
                    item.title(), item.anchorDate(), item.overdueDays(), priority), item.linkedProtocolName());
        }
        return appendProtocolHint("%s · %s · atrasada há %d dia(s) · %s".formatted(
                item.title(), item.anchorDate(), item.overdueDays(), priority), item.linkedProtocolName());
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
        if (item.stale()) score -= 240;
        return score;
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
                ? "Captura salva na caixa de entrada de ideias."
                : "Captura salva e agrupada para revisão posterior (" + created + " itens)." );
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

