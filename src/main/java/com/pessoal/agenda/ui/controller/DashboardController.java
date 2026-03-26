package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.YearMonth;

/**
 * Controller da aba Dashboard.
 * Exibe KPIs consolidados, próximos prazos críticos e alertas de atraso.
 */
public class DashboardController {

    private final SharedContext    ctx;
    private final DatabaseService  db;

    public DashboardController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
    }

    // ── Construção da aba ──────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("Dashboard");
        tab.setClosable(false);

        FlowPane cards = new FlowPane();
        cards.getStyleClass().add("dashboard-cards");
        cards.setHgap(12); cards.setVgap(12);
        cards.getChildren().addAll(
                UIHelper.createKpiCard("Tarefas abertas",       ctx.openTasksValue,       "kpi-blue"),
                UIHelper.createKpiCard("Tarefas atrasadas",     ctx.overdueTasksValue,    "kpi-red"),
                UIHelper.createKpiCard("Pagamentos pendentes",  ctx.pendingPaymentsValue, "kpi-orange"),
                UIHelper.createKpiCard("Valor pendente",        ctx.pendingAmountValue,   "kpi-purple"),
                UIHelper.createKpiCard("Checklist pendente",    ctx.checklistPendingValue,"kpi-cyan"),
                UIHelper.createKpiCard("Estudo no mês",         ctx.studyHoursValue,      "kpi-green"),
                UIHelper.createKpiCard("Estoque baixo",         ctx.lowStockValue,        "kpi-red"),
                UIHelper.createKpiCard("Ideias em progresso",   ctx.ideasInProgressValue, "kpi-indigo")
        );

        ListView<String> upcomingList = new ListView<>(ctx.upcomingItems);
        upcomingList.getStyleClass().add("clean-list");
        ListView<String> alertsList = new ListView<>(ctx.alertItems);
        alertsList.getStyleClass().add("clean-list");

        VBox upcomingBox = UIHelper.createCardSection("Próximos prazos críticos",
                new VBox(8, new Label("Visão consolidada de tarefas e pagamentos."), upcomingList));
        VBox alertsBox = UIHelper.createCardSection("Alertas de atraso",
                new VBox(8, new Label("Pendências vencidas que exigem ação imediata."), alertsList));

        HBox bottom = new HBox(12, upcomingBox, alertsBox);
        HBox.setHgrow(upcomingBox, Priority.ALWAYS);
        HBox.setHgrow(alertsBox,   Priority.ALWAYS);

        VBox content = new VBox(12, cards, bottom);
        content.setPadding(new Insets(16));
        tab.setContent(content);
        return tab;
    }

    // ── Atualização de KPIs ────────────────────────────────────────────────

    public void refreshKpis(YearMonth month) {
        ctx.openTasksValue.setText(String.valueOf(db.countOpenTasks()));
        ctx.overdueTasksValue.setText(String.valueOf(db.countOverdueTasks()));
        ctx.pendingPaymentsValue.setText(String.valueOf(db.countPendingPayments()));
        ctx.pendingAmountValue.setText("R$ %.2f".formatted(db.sumPendingPayments()));
        ctx.checklistPendingValue.setText(String.valueOf(db.countPendingChecklistItems()));
        int studyMins = AppContextHolder.get().studyEntryRepository().totalMinutesInMonth(month);
        ctx.studyHoursValue.setText("%.1f h".formatted(studyMins / 60.0));
        ctx.lowStockValue.setText(String.valueOf(db.countLowStockItems()));
        ctx.ideasInProgressValue.setText(String.valueOf(db.countIdeasInProgress()));
    }
}

