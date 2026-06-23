package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import com.pessoal.agenda.service.PendencyNotificationService;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.Separator;

import java.time.YearMonth;
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

    public DashboardController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
    }

    /** Define o callback de navegação entre abas (chamado pelo AgendaApp). */
    public void setTabNavigator(IntConsumer navigator) {
        this.tabNavigator = navigator;
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

        // ── ListView de tarefas de hoje ────────────────────────────────────────
        ListView<String> todayTasksList = new ListView<>(ctx.todayTaskItems);
        todayTasksList.getStyleClass().add("clean-list");
        Tooltip.install(todayTasksList,
                new Tooltip("Tarefas vencendo hoje. Duplo clique para navegar."));
        todayTasksList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tabNavigator != null) {
                tabNavigator.accept(1); // Agenda e Prioridades
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

        ListView<String> upcomingList = new ListView<>(ctx.upcomingItems);
        upcomingList.getStyleClass().add("clean-list");
        Tooltip.install(upcomingList,
                new Tooltip("Duplo clique para navegar até a aba correspondente ao item selecionado."));
        upcomingList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tabNavigator != null) {
                String item = upcomingList.getSelectionModel().getSelectedItem();
                if (item == null) return;
                // Formato: "due_date | Tarefa | title"  ou  "due_date | Pagamento | title"
                if (item.contains("| Tarefa |") || item.contains("|Tarefa|")) {
                    tabNavigator.accept(1); // Agenda e Prioridades
                } else if (item.contains("| Pagamento |") || item.contains("|Pagamento|")) {
                    tabNavigator.accept(3); // Financeiro e Pendências
                }
            }
        });

        ListView<String> alertsList = new ListView<>(ctx.alertItems);
        alertsList.getStyleClass().add("clean-list");
        Tooltip.install(alertsList,
                new Tooltip("Duplo clique para navegar até a aba correspondente ao alerta selecionado."));
        alertsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tabNavigator != null) {
                String item = alertsList.getSelectionModel().getSelectedItem();
                if (item == null) return;
                // Formato: "Tarefa atrasada: ..."  ou  "Pagamento pendente: ..."
                if (item.startsWith("Tarefa atrasada:")) {
                    tabNavigator.accept(1); // Agenda e Prioridades
                } else if (item.startsWith("Pagamento pendente:")) {
                    tabNavigator.accept(3); // Financeiro e Pendências
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

        HBox bottom = new HBox(12, upcomingBox, alertsBox);
        HBox.setHgrow(upcomingBox, Priority.ALWAYS);
        HBox.setHgrow(alertsBox,   Priority.ALWAYS);

        // ── Layout vertical com seções TDAH no topo ─────────────────────────
        VBox topAlerts = new VBox(8, todayTasksBox, expiringProtocolsBox);
        VBox.setVgrow(todayTasksBox, Priority.ALWAYS);
        VBox.setVgrow(expiringProtocolsBox, Priority.ALWAYS);

        HBox tdahSection = new HBox(12, topAlerts);
        HBox.setHgrow(topAlerts, Priority.ALWAYS);

        VBox content = new VBox(12, cards, bottom);
        // Insere a seção TDAH entre os KPIs e os alertas gerais
        content.getChildren().add(1, new Separator());
        content.getChildren().add(2, tdahSection);
        content.setPadding(new Insets(16));
        tab.setContent(content);
        return tab;
    }

    // ── Atualização de KPIs ────────────────────────────────────────────────

    public void refreshKpis(YearMonth month) {
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

        boolean hasAlertState = overdueCount > 0
                || !ctx.todayTaskItems.isEmpty()
                || !ctx.expiringProtocolItems.isEmpty();
        PendencyNotificationService.getInstance().setHasAlerts(hasAlertState);
    }

    private void updateTodayTasks() {
        try {
            var tasks = db.listTasksByDay(java.time.LocalDate.now(), null);
            ctx.todayTaskItems.clear();
            int count = 0;
            for (var t : tasks) {
                ctx.todayTaskItems.add("📌 " + t.text());
                count++;
            }
            ctx.tasksDueCountLabel.setText(String.valueOf(count));
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
}

