package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import com.pessoal.agenda.ui.view.ProjectIdeaDetailWindow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Controller da aba Ideias e Projetos Científicos.
 *
 * Características avançadas:
 *   – Painel KPI com métricas do pipeline
 *   – Vista Kanban (colunas por status)
 *   – Vista Lista com cards detalhados
 *   – Filtros múltiplos: categoria, status, prioridade, tipo, busca textual
 *   – Detalhamento em janela completa com edição (duplo clique)
 *   – Ordenação por prioridade e impacto
 */
public class IdeasController {

    // ── Status pipeline (em ordem de kanban) ─────────────────────────────────
    private static final String[][] STATUS_DEF = {
        {"nova",         "Nova",          "#1565c0"},
        {"em_validacao", "Em Validação",  "#7b1fa2"},
        {"prototipagem", "Prototipagem",  "#e65100"},
        {"em_execucao",  "Em Execução",   "#1b5e20"},
        {"pausada",      "Pausada",       "#546e7a"},
        {"concluida",    "Concluída",     "#2e7d32"},
        {"abandonada",   "Abandonada",    "#b71c1c"},
    };

    private final SharedContext ctx;
    private ProjectIdeaRepository repo;

    // ── estado de filtros ─────────────────────────────────────────────────────
    private String filterCategory = null;
    private String filterStatus   = null;
    private String filterPriority = null;
    private String filterType     = null;
    private String filterSearch   = null;

    // ── modo de vista ─────────────────────────────────────────────────────────
    private boolean isKanban = false;

    // ── área de conteúdo ──────────────────────────────────────────────────────
    private StackPane contentPane;

    // ── KPI labels ────────────────────────────────────────────────────────────
    private Label kpiTotal, kpiActive, kpiRunning, kpiDone, kpiHighImpact;

    public IdeasController(SharedContext ctx, com.pessoal.agenda.DatabaseService db) {
        this.ctx  = ctx;
    }

    public Tab buildTab() {
        // Obtém repo do AppContext (já inicializado)
        repo = AppContextHolder.get().projectIdeaRepository();

        Tab tab = new Tab("Ideias e Projetos");
        tab.setClosable(false);

        VBox root = new VBox(0);

        // ── Barra de ferramentas superior ──────────────────────────────────
        root.getChildren().add(buildToolbar());

        // ── Barra de KPIs ─────────────────────────────────────────────────
        root.getChildren().add(buildKpiBar());

        // ── Área de conteúdo (Lista ou Kanban) ────────────────────────────
        contentPane = new StackPane();
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        root.getChildren().add(contentPane);

        tab.setContent(root);
        refreshView();
        return tab;
    }

    // ── Refresh público ───────────────────────────────────────────────────────

    public void refresh() { refreshView(); }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        // Toggle: Lista / Kanban
        ToggleGroup viewGroup = new ToggleGroup();
        ToggleButton listBtn   = new ToggleButton("☰  Lista");
        listBtn.setToggleGroup(viewGroup); listBtn.setSelected(true);
        listBtn.getStyleClass().add("view-toggle-btn");
        ToggleButton kanbanBtn = new ToggleButton("⬛  Kanban");
        kanbanBtn.setToggleGroup(viewGroup);
        kanbanBtn.getStyleClass().add("view-toggle-btn");
        HBox viewToggle = new HBox(2, listBtn, kanbanBtn);
        viewToggle.getStyleClass().add("view-toggle-bar");
        listBtn.setOnAction(e   -> { isKanban = false; refreshView(); });
        kanbanBtn.setOnAction(e -> { isKanban = true;  refreshView(); });

        // Busca
        TextField searchField = new TextField();
        searchField.getStyleClass().add("input-control");
        searchField.setPromptText("🔍 Buscar por título, descrição ou palavras-chave");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, o, n) -> { filterSearch = n.isBlank() ? null : n; refreshView(); });

        // Filtro Status
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getStyleClass().add("input-control");
        statusFilter.getItems().add("Todos os status");
        for (String[] s : STATUS_DEF) statusFilter.getItems().add(s[1]);
        statusFilter.setValue("Todos os status");
        statusFilter.setOnAction(e -> {
            String v = statusFilter.getValue();
            if ("Todos os status".equals(v)) { filterStatus = null; refreshView(); return; }
            for (String[] s : STATUS_DEF) { if (s[1].equals(v)) { filterStatus = s[0]; break; } }
            refreshView();
        });

        // Filtro Prioridade
        ComboBox<String> prioFilter = new ComboBox<>();
        prioFilter.getStyleClass().add("input-control");
        prioFilter.getItems().addAll("Todas as prioridades","🔴 Crítica","🟠 Alta","🔵 Normal","🟢 Baixa");
        prioFilter.setValue("Todas as prioridades");
        prioFilter.setOnAction(e -> {
            switch (prioFilter.getValue()) {
                case "🔴 Crítica" -> filterPriority = "CRITICA";
                case "🟠 Alta"    -> filterPriority = "ALTA";
                case "🔵 Normal"  -> filterPriority = "NORMAL";
                case "🟢 Baixa"   -> filterPriority = "BAIXA";
                default           -> filterPriority = null;
            }
            refreshView();
        });

        // Filtro Tipo
        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getStyleClass().add("input-control");
        typeFilter.getItems().addAll("Todos os tipos","📋 Geral","🔬 Pesquisa","⚙ Engenharia",
                "💡 Hipótese","🧪 Experimento","💻 Software","📐 Metodologia","🚀 Inovação");
        typeFilter.setValue("Todos os tipos");
        typeFilter.setOnAction(e -> {
            filterType = switch (typeFilter.getValue()) {
                case "🔬 Pesquisa"    -> "PESQUISA";
                case "⚙ Engenharia"  -> "ENGENHARIA";
                case "💡 Hipótese"   -> "HIPOTESE";
                case "🧪 Experimento"-> "EXPERIMENTO";
                case "💻 Software"   -> "SOFTWARE";
                case "📐 Metodologia"-> "METODOLOGIA";
                case "🚀 Inovação"   -> "INOVACAO";
                default              -> null;
            };
            refreshView();
        });

        // Filtro Categoria
        ComboBox<String> catFilter = new ComboBox<>();
        catFilter.getStyleClass().add("input-control");
        catFilter.getItems().add("Todas as categorias");
        ctx.ideaCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = catFilter.getValue();
            catFilter.getItems().setAll("Todas as categorias");
            catFilter.getItems().addAll(ctx.ideaCatNames);
            catFilter.setValue(cur != null ? cur : "Todas as categorias");
        });
        catFilter.setValue("Todas as categorias");
        catFilter.setOnAction(e -> {
            String v = catFilter.getValue();
            filterCategory = "Todas as categorias".equals(v) ? null : v;
            refreshView();
        });

        // Botão nova ideia
        Button newBtn = new Button("＋  Nova Ideia");
        newBtn.getStyleClass().add("primary-button");
        newBtn.setOnAction(e -> ProjectIdeaDetailWindow.showNew(repo,
                catFilter.getValue() != null && !catFilter.getValue().startsWith("Todas") ? catFilter.getValue() : "Geral",
                () -> { refreshView(); ctx.triggerDashboardRefresh(); }));

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8, viewToggle, searchField, statusFilter, prioFilter, typeFilter, catFilter, spacer, newBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("agenda-top-bar");
        return bar;
    }

    // ── Barra KPI ─────────────────────────────────────────────────────────────

    private HBox buildKpiBar() {
        kpiTotal      = new Label("0"); kpiTotal.getStyleClass().addAll("kpi-value");
        kpiActive     = new Label("0"); kpiActive.getStyleClass().add("kpi-value");
        kpiRunning    = new Label("0"); kpiRunning.getStyleClass().add("kpi-value");
        kpiDone       = new Label("0"); kpiDone.getStyleClass().add("kpi-value");
        kpiHighImpact = new Label("0"); kpiHighImpact.getStyleClass().add("kpi-value");

        HBox bar = new HBox(10,
                kpiCard("TOTAL DE IDEIAS",    kpiTotal,      "kpi-blue"),
                kpiCard("ATIVAS NO PIPELINE", kpiActive,     "kpi-indigo"),
                kpiCard("EM EXECUÇÃO",        kpiRunning,    "kpi-green"),
                kpiCard("CONCLUÍDAS",         kpiDone,       "kpi-cyan"),
                kpiCard("ALTO IMPACTO",       kpiHighImpact, "kpi-purple"));
        bar.setPadding(new Insets(0, 14, 10, 14));
        for (Node n : bar.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return bar;
    }

    private VBox kpiCard(String title, Label value, String colorClass) {
        Label lbl = new Label(title);
        lbl.getStyleClass().add("kpi-title");
        VBox card = new VBox(4, lbl, value);
        card.getStyleClass().addAll("kpi-card", colorClass);
        return card;
    }

    // ── Vista Lista ───────────────────────────────────────────────────────────

    private Node buildListView(List<ProjectIdea> ideas) {
        if (ideas.isEmpty()) {
            Label empty = new Label("Nenhuma ideia encontrada com os filtros aplicados.");
            empty.getStyleClass().add("empty-state-label");
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #7a9abc; -fx-padding: 40;");
            VBox box = new VBox(empty); box.setAlignment(Pos.CENTER);
            return box;
        }

        ListView<ProjectIdea> list = new ListView<>();
        list.getStyleClass().addAll("clean-list", "ideas-list");
        list.getItems().addAll(ideas);
        list.setCellFactory(lv -> new IdeaListCell());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ProjectIdea sel = list.getSelectionModel().getSelectedItem();
                if (sel != null) openDetail(sel);
            }
        });
        list.setPrefHeight(Integer.MAX_VALUE);
        VBox box = new VBox(list); VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    // ── Vista Kanban ──────────────────────────────────────────────────────────

    private Node buildKanbanView(List<ProjectIdea> ideas) {
        HBox kanban = new HBox(8);
        kanban.setPadding(new Insets(4, 14, 14, 14));
        kanban.setFillHeight(true);

        for (String[] sd : STATUS_DEF) {
            String statusKey = sd[0]; String statusLabel = sd[1]; String color = sd[2];
            List<ProjectIdea> colIdeas = ideas.stream()
                    .filter(i -> normalizeStatus(i.status()).equals(statusKey))
                    .toList();
            kanban.getChildren().add(buildKanbanColumn(statusKey, statusLabel, color, colIdeas));
        }

        ScrollPane scroll = new ScrollPane(kanban);
        scroll.setFitToHeight(true); scroll.setFitToWidth(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    private VBox buildKanbanColumn(String statusKey, String statusLabel, String hexColor, List<ProjectIdea> ideas) {
        Label headerLabel = new Label(statusLabel + "  (" + ideas.size() + ")");
        headerLabel.setStyle(
                "-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: white;"
                + " -fx-padding: 6 10 6 10; -fx-background-radius: 6 6 0 0;"
                + " -fx-background-color: " + hexColor + ";");
        headerLabel.setMaxWidth(Double.MAX_VALUE);

        // Botão + rápido
        Button addBtn = new Button("+");
        addBtn.setStyle("-fx-background-color: rgba(255,255,255,0.3); -fx-text-fill: white;"
                + " -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand;"
                + " -fx-padding: 1 7 1 7;");
        addBtn.setOnAction(e -> {
            ProjectIdea blank = new ProjectIdea(0, "", "", statusKey, filterCategory != null ? filterCategory : "Geral");
            new ProjectIdeaDetailWindow(blank, repo, () -> { refreshView(); ctx.triggerDashboardRefresh(); }).show();
        });

        HBox colHeader = new HBox(headerLabel, addBtn);
        colHeader.setAlignment(Pos.CENTER_LEFT);
        colHeader.setStyle("-fx-background-color: " + hexColor + "; -fx-background-radius: 6 6 0 0;");
        HBox.setHgrow(headerLabel, Priority.ALWAYS);

        VBox cards = new VBox(6);
        cards.setPadding(new Insets(8));
        for (ProjectIdea idea : ideas) {
            cards.getChildren().add(buildKanbanCard(idea, hexColor));
        }
        if (ideas.isEmpty()) {
            Label empty = new Label("Sem ideias");
            empty.setStyle("-fx-text-fill: #aabbcc; -fx-font-size: 11px; -fx-padding: 12;");
            cards.getChildren().add(empty);
        }

        ScrollPane cardScroll = new ScrollPane(cards);
        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.setStyle("-fx-background-color: #f7fbff; -fx-border-color: transparent;");
        VBox.setVgrow(cardScroll, Priority.ALWAYS);

        VBox col = new VBox(0, colHeader, cardScroll);
        col.setPrefWidth(200); col.setMinWidth(180); col.setMaxWidth(220);
        col.setStyle("-fx-background-color: #f7fbff;"
                + " -fx-border-color: #b8cfe8; -fx-border-width: 0 0 0 0;"
                + " -fx-border-radius: 6; -fx-background-radius: 6;"
                + " -fx-effect: dropshadow(gaussian, rgba(3,24,62,0.07), 8, 0.2, 0, 2);");
        VBox.setVgrow(col, Priority.ALWAYS);
        return col;
    }

    private VBox buildKanbanCard(ProjectIdea idea, String accentColor) {
        Label titleLbl = new Label(idea.title());
        titleLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #0d1b2a; -fx-wrap-text: true;");
        titleLbl.setMaxWidth(Double.MAX_VALUE); titleLbl.setWrapText(true);

        Label typeLbl = new Label(idea.typeLabel());
        typeLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #5a7a9e;");

        Label prioLbl = new Label(idea.priorityLabel());
        prioLbl.setStyle("-fx-font-size: 10px;");

        HBox chips = new HBox(4, typeLbl, prioLbl);

        Label feasLbl = new Label("Viab: " + idea.feasibilityDots());
        feasLbl.setStyle("-fx-font-size: 10px; -fx-font-family: monospace; -fx-text-fill: #1565c0;");

        Label impactLbl = new Label(idea.impactLabel());
        impactLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #7b1fa2;");

        VBox card = new VBox(4, titleLbl, chips, new HBox(8, feasLbl, impactLbl));
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 5;"
                + " -fx-border-color: " + accentColor + ";"
                + " -fx-border-width: 0 0 0 3; -fx-border-radius: 5;"
                + " -fx-padding: 8 10 8 10;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 4, 0.2, 0, 1);"
                + " -fx-cursor: hand;");
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) openDetail(idea); });

        // Tooltip com descrição
        if (idea.description() != null && !idea.description().isBlank()) {
            Tooltip tp = new Tooltip(truncate(idea.description(), 220));
            tp.setWrapText(true); tp.setMaxWidth(300);
            Tooltip.install(card, tp);
        }
        return card;
    }

    // ── Refresh da view ───────────────────────────────────────────────────────

    private void refreshView() {
        List<ProjectIdea> ideas = repo.findWithFilters(filterCategory, filterStatus, filterPriority, filterType, filterSearch);
        updateKpis();
        Node view = isKanban ? buildKanbanView(ideas) : buildListView(ideas);
        VBox.setVgrow(view, Priority.ALWAYS);
        StackPane.setAlignment(view, Pos.TOP_LEFT);
        contentPane.getChildren().setAll(view);
    }

    private void updateKpis() {
        kpiTotal.setText(String.valueOf(repo.countAll()));
        kpiActive.setText(String.valueOf(repo.countActive()));
        kpiRunning.setText(String.valueOf(repo.countInProgress()));
        kpiDone.setText(String.valueOf(repo.countDone()));
        kpiHighImpact.setText(String.valueOf(repo.countHighImpact()));
    }

    // ── Ações ─────────────────────────────────────────────────────────────────

    private void openDetail(ProjectIdea idea) {
        new ProjectIdeaDetailWindow(idea, repo, () -> {
            refreshView(); ctx.triggerDashboardRefresh();
        }).show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String normalizeStatus(String s) {
        if (s == null) return "nova";
        return switch (s.toLowerCase().trim()) {
            case "em execução", "em_execucao" -> "em_execucao";
            case "em validação", "em_validacao" -> "em_validacao";
            case "concluída", "concluida" -> "concluida";
            default -> s.toLowerCase().trim();
        };
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max) + "…";
    }

    // ── Cell customizada para lista ───────────────────────────────────────────

    private class IdeaListCell extends ListCell<ProjectIdea> {
        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

        @Override
        protected void updateItem(ProjectIdea idea, boolean empty) {
            super.updateItem(idea, empty);
            if (empty || idea == null) { setGraphic(null); setText(null); return; }

            // Faixa lateral colorida por status
            String accentHex = "#1565c0";
            for (String[] sd : STATUS_DEF) {
                if (normalizeStatus(idea.status()).equals(sd[0])) { accentHex = sd[2]; break; }
            }

            // Linha 1: título + badges
            Label titleLbl = new Label(idea.title());
            titleLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #0d1b2a;");
            titleLbl.setWrapText(false);

            Label statusBadge  = badge(idea.statusLabel(),  accentHex, "white");
            Label priorityBadge= badge(idea.priorityLabel(), "#eef4fc","#1565c0");
            Label typeBadge    = badge(idea.typeLabel(),     "#f3eeff","#7b1fa2");
            Label impactBadge  = badge(idea.impactLabel(),   "#fff4ec","#e65100");

            Region hSpacer = new Region(); HBox.setHgrow(hSpacer, Priority.ALWAYS);
            HBox row1 = new HBox(8, titleLbl, hSpacer, statusBadge, priorityBadge, typeBadge, impactBadge);
            row1.setAlignment(Pos.CENTER_LEFT);

            // Linha 2: viabilidade + horas + datas + keywords
            Label feasLbl = new Label("Viab: " + idea.feasibilityDots());
            feasLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #1565c0;");

            Label hoursLbl = new Label();
            if (idea.estimatedHours() > 0)
                hoursLbl.setText("⏱ " + idea.estimatedHours() + "h estimadas");
            hoursLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #546e7a;");

            Label dateLbl = new Label();
            if (idea.targetDate() != null) {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), idea.targetDate());
                String dateStr = idea.targetDate().format(FMT);
                String dayInfo = days < 0 ? " ⚠ " + Math.abs(days) + "d atraso"
                               : days == 0 ? " ⏰ hoje"
                               : " · " + days + "d restantes";
                dateLbl.setText("📅 " + dateStr + dayInfo);
                dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (days < 0 ? "#b71c1c" : "#2e7d32") + ";");
            }

            Label kwLbl = new Label();
            if (idea.keywords() != null && !idea.keywords().isBlank())
                kwLbl.setText("🏷 " + idea.keywords());
            kwLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #7a9abc;");

            HBox row2 = new HBox(12, feasLbl, hoursLbl, dateLbl, kwLbl);
            row2.setAlignment(Pos.CENTER_LEFT);

            // Linha 3: trecho da descrição
            Label descLbl = new Label();
            if (idea.description() != null && !idea.description().isBlank())
                descLbl.setText(truncate(idea.description().replace("\n", " "), 140));
            descLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a7a9e;");
            descLbl.setWrapText(false);

            VBox textBox = new VBox(3, row1, row2, descLbl);
            textBox.setPadding(new Insets(4, 8, 4, 0));
            HBox.setHgrow(textBox, Priority.ALWAYS);

            // Faixa lateral de cor
            Region stripe = new Region();
            stripe.setMinWidth(4); stripe.setMaxWidth(4);
            stripe.setStyle("-fx-background-color: " + accentHex + "; -fx-background-radius: 2 0 0 2;");

            HBox cell = new HBox(0, stripe, textBox);
            cell.setAlignment(Pos.CENTER_LEFT);
            cell.setStyle("-fx-padding: 6 8 6 0;");

            setGraphic(cell); setText(null);
        }

        private Label badge(String text, String bg, String fg) {
            Label l = new Label(text);
            l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";"
                    + " -fx-font-size: 10px; -fx-font-weight: 600; -fx-padding: 2 6 2 6;"
                    + " -fx-background-radius: 10;");
            return l;
        }
    }
}
