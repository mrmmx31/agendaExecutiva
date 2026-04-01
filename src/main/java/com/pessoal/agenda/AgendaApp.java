package com.pessoal.agenda;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.ui.controller.*;
import com.pessoal.agenda.ui.view.WindowManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
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

    // ── Barra de status ────────────────────────────────────────────────────
    private final Label statusLabel = new Label("Sistema pronto para uso.");

    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        databaseService.initialize();

        // Contexto compartilhado com callback de status
        ctx = new SharedContext(statusLabel::setText);

        // Callbacks coordenados pelo AgendaApp
        ctx.setDashboardRefreshCallback(this::refreshDashboardKpis);
        ctx.setAlertRefreshCallback(this::refreshAlertsAndUpcoming);

        // Inicialização dos controllers
        dashboardCtrl = new DashboardController(ctx, databaseService);
        agendaCtrl    = new AgendaTabController(ctx, databaseService);
        checklistCtrl = new ChecklistController(ctx);
        financeCtrl   = new FinanceController(ctx, databaseService);
        salesCtrl     = new SalesController(ctx, databaseService);
        studyCtrl     = new StudyController(ctx);
        ideasCtrl     = new IdeasController(ctx, databaseService);
        configCtrl    = new ConfigController(ctx);

        // Montagem do TabPane
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tabs");
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
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());

        stage.setTitle("Agenda Científica Pessoal — Planejamento Integrado");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            WindowManager.closeAll();
            Platform.exit();
        });
        stage.show();

        // Carga inicial de todos os dados
        refreshAllData(YearMonth.now());
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
    }

    private void refreshDashboardKpis() {
        YearMonth month = YearMonth.from(agendaCtrl.getCurrentDate());
        dashboardCtrl.refreshKpis(month);
    }

    private void refreshAlertsAndUpcoming() {
        ctx.alertItems.setAll(databaseService.listDeadlineAlerts());
        ctx.upcomingItems.setAll(databaseService.listUpcomingDeadlines(10));
    }

    // ── Construção do cabeçalho e barra de status ──────────────────────────

    private Node buildHeader(TabPane tabPane) {
        Label title    = new Label("Painel de Operação Pessoal");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label(
                "Planejamento diário, financeiro, estudos e execução de projetos em um único fluxo");
        subtitle.getStyleClass().add("page-subtitle");

        Button refreshAllBtn = new Button("Atualizar tudo");
        refreshAllBtn.getStyleClass().add("primary-button");
        refreshAllBtn.setOnAction(e -> {
            refreshAllData(YearMonth.from(agendaCtrl.getCurrentDate()));
            ctx.setStatus("Dados atualizados com sucesso.");
        });

        Button focusBtn = new Button("Ir para Dashboard");
        focusBtn.getStyleClass().add("secondary-button");
        focusBtn.setOnAction(e -> tabPane.getSelectionModel().select(0));

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, new VBox(4, title, subtitle), spacer, focusBtn, refreshAllBtn);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 18, 14, 18));
        return header;
    }

    private Node buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        HBox bar = new HBox(statusLabel);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 18, 8, 18));
        return bar;
    }
}
