package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.model.IdeaChecklistItem;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.repository.IdeaChecklistRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Janela dedicada de Checklist/Kanban para Ideia / Projeto Científico.
 *
 * Modos de visualização:
 *   • Lista  — flat list com filtros (Todos / Pendentes / Concluídos)
 *   • Kanban — board com 4 colunas: Backlog | Em Andamento | Em Revisão | Concluído
 */
public class ProjectChecklistWindow {

    // ── Colunas do Kanban ─────────────────────────────────────────────────────
    private static final String[][] KANBAN_COLS = {
        {"backlog",      "📋  Backlog",        "#546e7a"},
        {"em_andamento", "⚡  Em Andamento",   "#1565c0"},
        {"em_revisao",   "🔬  Em Revisão",     "#7b1fa2"},
        {"concluido",    "✅  Concluído",       "#2e7d32"},
    };

    // ── Prevenção de janela duplicada ─────────────────────────────────────────
    private static final Map<Long, Stage> openWindows = new HashMap<>();

    private final ProjectIdea idea;
    private final IdeaChecklistRepository repo;
    private final Runnable onChanged;

    // ── Estado em memória ─────────────────────────────────────────────────────
    private final List<MutableItem> items = new ArrayList<>();

    // ── Modo de visualização ──────────────────────────────────────────────────
    private boolean isKanban = false;

    // ── Filtro de exibição (lista) ────────────────────────────────────────────
    private enum Filter { ALL, PENDING, DONE }
    private Filter currentFilter = Filter.ALL;

    // ── Componentes da UI ─────────────────────────────────────────────────────
    private VBox itemsBox;
    private StackPane contentArea;
    private Label kpiTotal, kpiDone, kpiPending, kpiPercent;
    private ProgressBar progressBar;
    private Label progressLabel;

    // ── Context menu único reutilizável para itens ────────────────────────────
    private final ContextMenu itemContextMenu = new ContextMenu();

    // ── Modelo mutável de item ────────────────────────────────────────────────
    private static class MutableItem {
        long    id;
        String  text;
        boolean done;
        int     position;
        String  kanbanColumn;

        MutableItem(IdeaChecklistItem src) {
            this.id           = src.id();
            this.text         = src.text();
            this.done         = src.done();
            this.position     = src.position();
            this.kanbanColumn = src.kanbanColumn() != null ? src.kanbanColumn() : "backlog";
        }
    }

    // ── Construtor ────────────────────────────────────────────────────────────

    public ProjectChecklistWindow(ProjectIdea idea, IdeaChecklistRepository repo, Runnable onChanged) {
        this.idea      = idea;
        this.repo      = repo;
        this.onChanged = onChanged;
    }

    // ── Ponto de entrada ──────────────────────────────────────────────────────

    public void show() {
        if (openWindows.containsKey(idea.id())) {
            openWindows.get(idea.id()).toFront();
            openWindows.get(idea.id()).requestFocus();
            return;
        }

        loadItems();

        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.setTitle("Checklist — " + idea.title());
        stage.setMinWidth(640);
        stage.setMinHeight(540);
        openWindows.put(idea.id(), stage);
        stage.setOnHidden(e -> openWindows.remove(idea.id()));

        VBox root = new VBox(0);
        root.getStyleClass().add("app-root");

        contentArea = new StackPane();
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        root.getChildren().addAll(
                buildHeader(),
                buildKpiBar(),
                buildProgressSection(),
                buildViewToolbar(),
                contentArea,
                buildFooter(stage)
        );

        refreshContentArea();

        Scene scene = new Scene(root, 880, 660);
        ThemeManager.getInstance().applyTo(scene);
        stage.setScene(scene);
        stage.show();
    }

    // ── Carregamento ──────────────────────────────────────────────────────────

    private void loadItems() {
        items.clear();
        repo.findByIdeaId(idea.id()).forEach(i -> items.add(new MutableItem(i)));
    }

    // ── Cabeçalho ─────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        Label icon = new Label("☑");
        icon.setStyle("-fx-font-size: 28px; -fx-text-fill: #00b4d8;");

        Label title = new Label("Checklist do Projeto");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label(idea.title()
                + "  ·  " + idea.typeLabel()
                + "  ·  " + idea.priorityLabel());
        subtitle.getStyleClass().add("page-subtitle");

        VBox texts = new VBox(2, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(idea.statusLabel());
        statusBadge.setStyle(
                "-fx-background-color: #1565c0; -fx-text-fill: white;"
                + " -fx-font-size: 11px; -fx-font-weight: 700;"
                + " -fx-padding: 4 10 4 10; -fx-background-radius: 12;");

        HBox hdr = new HBox(14, icon, texts, spacer, statusBadge);
        hdr.getStyleClass().add("header-bar");
        hdr.setPadding(new Insets(14, 20, 14, 20));
        hdr.setAlignment(Pos.CENTER_LEFT);
        return hdr;
    }

    // ── KPI bar ───────────────────────────────────────────────────────────────

    private HBox buildKpiBar() {
        kpiTotal   = kpiValue("0");
        kpiDone    = kpiValue("0");
        kpiPending = kpiValue("0");
        kpiPercent = kpiValue("0%");

        HBox bar = new HBox(10,
                kpiCard("TOTAL DE ITENS",   kpiTotal,   "kpi-blue"),
                kpiCard("CONCLUÍDOS",       kpiDone,    "kpi-green"),
                kpiCard("PENDENTES",        kpiPending, "kpi-indigo"),
                kpiCard("PROGRESSO",        kpiPercent, "kpi-cyan"));
        bar.setPadding(new Insets(12, 14, 4, 14));
        for (javafx.scene.Node n : bar.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        refreshKpiAndProgress();
        return bar;
    }

    private Label kpiValue(String v) {
        Label l = new Label(v); l.getStyleClass().add("kpi-value"); return l;
    }
    private VBox kpiCard(String title, Label value, String cls) {
        Label lbl = new Label(title); lbl.getStyleClass().add("kpi-title");
        VBox c = new VBox(4, lbl, value); c.getStyleClass().addAll("kpi-card", cls); return c;
    }

    // ── Barra de progresso ────────────────────────────────────────────────────

    private VBox buildProgressSection() {
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(14);

        progressLabel = new Label("0 de 0 itens concluídos");
        progressLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #5a7a9e;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        VBox section = new VBox(6, progressBar, progressLabel);
        section.setPadding(new Insets(8, 14, 8, 14));
        section.setStyle("-fx-background-color: white;"
                + " -fx-border-color: transparent transparent #dce8f5 transparent;"
                + " -fx-border-width: 0 0 1 0;");
        return section;
    }

    // ── Barra de ferramentas com toggle Lista/Kanban + filtros ─────────────────

    private HBox buildViewToolbar() {
        // Toggle: Lista / Kanban
        ToggleGroup viewGroup = new ToggleGroup();
        ToggleButton listBtn   = new ToggleButton("☰  Lista");
        ToggleButton kanbanBtn = new ToggleButton("⬛  Kanban");
        listBtn.setToggleGroup(viewGroup);   listBtn.setSelected(true);
        kanbanBtn.setToggleGroup(viewGroup);
        listBtn.getStyleClass().add("view-toggle-btn");
        kanbanBtn.getStyleClass().add("view-toggle-btn");
        HBox viewToggle = new HBox(2, listBtn, kanbanBtn);
        viewToggle.getStyleClass().add("view-toggle-bar");

        listBtn.setOnAction(e -> {
            if (!listBtn.isSelected()) { listBtn.setSelected(true); return; }
            isKanban = false; refreshContentArea();
        });
        kanbanBtn.setOnAction(e -> {
            if (!kanbanBtn.isSelected()) { kanbanBtn.setSelected(true); return; }
            isKanban = true; refreshContentArea();
        });

        // Separador
        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep.setPadding(new Insets(0, 4, 0, 4));

        // Filtros de lista
        ToggleGroup filterGroup = new ToggleGroup();
        ToggleButton allBtn   = filterToggle("Todos",      filterGroup, Filter.ALL);
        ToggleButton pendBtn  = filterToggle("Pendentes",  filterGroup, Filter.PENDING);
        ToggleButton doneBtn  = filterToggle("Concluídos", filterGroup, Filter.DONE);
        allBtn.setSelected(true);
        HBox filterBar = new HBox(2, allBtn, pendBtn, doneBtn);
        filterBar.getStyleClass().add("view-toggle-bar");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button checkAllBtn   = new Button("✔  Marcar Todos");
        Button uncheckAllBtn = new Button("☐  Desmarcar Todos");
        Button clearDoneBtn  = new Button("🗑  Limpar Concluídos");
        checkAllBtn.getStyleClass().add("secondary-button");
        uncheckAllBtn.getStyleClass().add("secondary-button");
        clearDoneBtn.getStyleClass().add("danger-button");
        checkAllBtn.setStyle("-fx-font-size: 11px;");
        uncheckAllBtn.setStyle("-fx-font-size: 11px;");
        clearDoneBtn.setStyle("-fx-font-size: 11px;");
        checkAllBtn.setOnAction(e -> markAll(true));
        uncheckAllBtn.setOnAction(e -> markAll(false));
        clearDoneBtn.setOnAction(e -> clearDoneItems());

        Button addBtn = new Button("＋  Novo Item");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setStyle("-fx-font-size: 11px;");
        addBtn.setOnAction(e -> showAddItemDialog());

        Button printBtn = new Button("🖨  Imprimir");
        printBtn.getStyleClass().add("secondary-button");
        printBtn.setStyle("-fx-font-size: 11px;");
        printBtn.setOnAction(e -> printChecklist());

        HBox bar = new HBox(8, viewToggle, sep, filterBar, spacer,
                checkAllBtn, uncheckAllBtn, clearDoneBtn, printBtn, addBtn);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #f2f7fc;"
                + " -fx-border-color: transparent transparent #dce8f5 transparent;"
                + " -fx-border-width: 0 0 1 0;");
        return bar;
    }

    private ToggleButton filterToggle(String label, ToggleGroup tg, Filter filter) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(tg);
        btn.getStyleClass().add("view-toggle-btn");
        btn.setOnAction(e -> {
            if (!btn.isSelected()) { btn.setSelected(true); return; }
            currentFilter = filter;
            if (!isKanban) refreshContentArea();
        });
        return btn;
    }

    // ── Impressão do checklist ────────────────────────────────────────────────

    private void printChecklist() {
        // ── Diálogo de filtros ────────────────────────────────────────────
        ComboBox<String> dlgItemsFilter = new ComboBox<>();
        dlgItemsFilter.getItems().addAll("Todos os itens", "Apenas Pendentes", "Apenas Concluídos");
        dlgItemsFilter.setValue(switch (currentFilter) {
            case PENDING -> "Apenas Pendentes";
            case DONE    -> "Apenas Concluídos";
            default      -> "Todos os itens";
        });
        dlgItemsFilter.getStyleClass().add("input-control");
        dlgItemsFilter.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> dlgColFilter = new ComboBox<>();
        dlgColFilter.getItems().add("Todas as colunas");
        for (String[] c : KANBAN_COLS) dlgColFilter.getItems().add(c[1].replace("  ", " ").trim());
        dlgColFilter.setValue("Todas as colunas");
        dlgColFilter.getStyleClass().add("input-control");
        dlgColFilter.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(10, 0, 0, 0));
        grid.add(new Label("Mostrar:"), 0, 0); grid.add(dlgItemsFilter, 1, 0);
        grid.add(new Label("Coluna:"),  0, 1); grid.add(dlgColFilter,   1, 1);
        javafx.scene.layout.GridPane.setHgrow(dlgItemsFilter, Priority.ALWAYS);
        javafx.scene.layout.GridPane.setHgrow(dlgColFilter,   Priority.ALWAYS);

        Dialog<ButtonType> printDlg = new Dialog<>();
        printDlg.setTitle("Opções de Impressão");
        printDlg.setHeaderText("Checklist — " + idea.title());
        printDlg.getDialogPane().setContent(grid);
        printDlg.getDialogPane().setMinWidth(380);
        printDlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) printDlg.getDialogPane().lookupButton(ButtonType.OK)).setText("🖨 Gerar Checklist");
        ((Button) printDlg.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");

        var dlgResult = printDlg.showAndWait();
        if (dlgResult.isEmpty() || dlgResult.get() != ButtonType.OK) return;

        // ── Determinar coluna selecionada ─────────────────────────────────
        String colSel = dlgColFilter.getValue();
        String colKey = "Todas as colunas".equals(colSel) ? null :
                java.util.Arrays.stream(KANBAN_COLS)
                        .filter(c -> c[1].replace("  ", " ").trim().equals(colSel))
                        .map(c -> c[0])
                        .findFirst().orElse(null);

        // ── Filtrar itens conforme seleção ────────────────────────────────
        String itemsSel = dlgItemsFilter.getValue();
        List<com.pessoal.agenda.model.IdeaChecklistItem> checkItems = items.stream()
                .filter(mi -> switch (itemsSel) {
                    case "Apenas Pendentes"  -> !mi.done;
                    case "Apenas Concluídos" ->  mi.done;
                    default                  -> true;
                })
                .filter(mi -> colKey == null || colKey.equals(mi.kanbanColumn))
                .map(mi -> new com.pessoal.agenda.model.IdeaChecklistItem(
                        mi.id, idea.id(), mi.text, mi.done, mi.position, mi.kanbanColumn))
                .toList();

        String html = com.pessoal.agenda.ui.util.PrintReportService
                .generateProjectChecklistReport(idea, checkItems);
        PrintPreviewWindow.open(html, "Checklist — " + idea.title());
    }

    // ── Diálogo de adição de item ─────────────────────────────────────────────

    private void showAddItemDialog() {
        TextField field = new TextField();
        field.getStyleClass().add("input-control");
        field.setPromptText("Descreva o item...");
        field.setPrefWidth(380);

        ComboBox<String> colCombo = new ComboBox<>();
        colCombo.getStyleClass().add("input-control");
        for (String[] c : KANBAN_COLS) colCombo.getItems().add(c[1].replace("  ", " ").trim());
        colCombo.setValue(KANBAN_COLS[0][1].replace("  ", " ").trim());

        VBox content = new VBox(8,
                new Label("Texto do item:"), field,
                new Label("Coluna inicial:"), colCombo);
        content.setPadding(new Insets(10));

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Novo Item");
        dlg.setHeaderText("Adicionar item ao checklist");
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Platform.runLater(field::requestFocus);

        dlg.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String text = field.getText().trim();
            if (text.isBlank()) return;
            int colIdx = colCombo.getSelectionModel().getSelectedIndex();
            String colKey = KANBAN_COLS[Math.max(0, colIdx)][0];
            IdeaChecklistItem saved = repo.addItem(idea.id(), text);
            // Atualiza a coluna se não for backlog
            if (!"backlog".equals(colKey)) repo.updateColumn(saved.id(), colKey);
            MutableItem mi = new MutableItem(saved);
            mi.kanbanColumn = colKey;
            mi.done = "concluido".equals(colKey);
            items.add(mi);
            refreshAll();
            if (onChanged != null) onChanged.run();
        });
    }

    // ── Área de conteúdo principal ────────────────────────────────────────────

    private void refreshContentArea() {
        if (contentArea == null) return;
        contentArea.getChildren().setAll(isKanban ? buildKanbanView() : buildListView());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VISTA LISTA
    // ──────────────────────────────────────────────────────────────────────────

    private javafx.scene.Node buildListView() {
        itemsBox = new VBox(0);
        itemsBox.setFillWidth(true);

        ScrollPane sp = new ScrollPane(itemsBox);
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("edge-to-edge");
        sp.setStyle("-fx-background-color: #f7fbff;");

        refreshItemsUI();
        return sp;
    }

    private void refreshItemsUI() {
        if (itemsBox == null) return;
        itemsBox.getChildren().clear();

        List<MutableItem> visible = items.stream()
                .filter(it -> switch (currentFilter) {
                    case ALL     -> true;
                    case PENDING -> !it.done;
                    case DONE    -> it.done;
                }).toList();

        if (visible.isEmpty()) {
            Label empty = new Label(switch (currentFilter) {
                case PENDING -> "Nenhum item pendente. 🎉 Todos os itens foram concluídos!";
                case DONE    -> "Nenhum item concluído ainda.";
                default      -> "Nenhum item no checklist.\nUse o botão ＋ Novo Item para adicionar.";
            });
            empty.setStyle("-fx-text-fill: #9eafc0; -fx-font-size: 12px; -fx-padding: 30 20 30 20;");
            empty.setWrapText(true); empty.setMaxWidth(Double.MAX_VALUE);
            empty.setAlignment(Pos.CENTER);
            itemsBox.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < visible.size(); i++) itemsBox.getChildren().add(buildItemRow(visible.get(i), i));
    }

    private VBox buildItemRow(MutableItem item, int index) {
        CheckBox cb = new CheckBox();
        cb.setSelected(item.done);
        cb.setStyle("-fx-cursor: hand;");

        int globalIdx = items.indexOf(item) + 1;
        Label numLbl = new Label(String.format("%02d", globalIdx));
        numLbl.setStyle("-fx-font-family:'JetBrains Mono','Consolas',monospace;"
                + " -fx-font-size:10px; -fx-text-fill:#9eafc0; -fx-min-width:24px;");

        Label textLbl = new Label(item.text);
        textLbl.setWrapText(true); textLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLbl, Priority.ALWAYS);
        applyDoneStyle(textLbl, item.done);

        // Badge de coluna
        String colLabel = columnLabel(item.kanbanColumn);
        String colColor = columnColor(item.kanbanColumn);
        Label colBadge = new Label(colLabel);
        colBadge.setStyle("-fx-background-color:" + colColor + "22; -fx-text-fill:" + colColor + ";"
                + " -fx-font-size:9px; -fx-font-weight:700; -fx-padding:2 6 2 6; -fx-background-radius:8;");

        TextField editField = new TextField(item.text);
        editField.getStyleClass().add("input-control");
        editField.setStyle("-fx-font-size:12px;");
        editField.setVisible(false); editField.setManaged(false);
        HBox.setHgrow(editField, Priority.ALWAYS);

        Button editBtn = new Button("✏");
        editBtn.setTooltip(new Tooltip("Editar (duplo clique)"));
        editBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#5a7a9e;"
                + " -fx-font-size:12px; -fx-cursor:hand; -fx-padding:2 6 2 6; -fx-background-radius:4;");
        editBtn.setOnAction(e -> enterEditMode(textLbl, editField, item, editBtn));

        Button delBtn = new Button("✕");
        delBtn.setTooltip(new Tooltip("Excluir item"));
        delBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#9eafc0;"
                + " -fx-font-size:12px; -fx-cursor:hand; -fx-padding:2 6 2 6; -fx-background-radius:4;");
        delBtn.setOnMouseEntered(e -> delBtn.setStyle(
                "-fx-background-color:#fdecea; -fx-text-fill:#c62828;"
                + " -fx-font-size:12px; -fx-cursor:hand; -fx-padding:2 6 2 6; -fx-background-radius:4;"));
        delBtn.setOnMouseExited(e -> delBtn.setStyle(
                "-fx-background-color:transparent; -fx-text-fill:#9eafc0;"
                + " -fx-font-size:12px; -fx-cursor:hand; -fx-padding:2 6 2 6; -fx-background-radius:4;"));
        delBtn.setOnAction(e -> deleteItem(item));

        cb.selectedProperty().addListener((obs, o, n) -> {
            item.done = n;
            item.kanbanColumn = n ? "concluido" : (item.kanbanColumn.equals("concluido") ? "backlog" : item.kanbanColumn);
            repo.updateDone(item.id, n);
            applyDoneStyle(textLbl, n);
            colBadge.setText(columnLabel(item.kanbanColumn));
            colBadge.setStyle("-fx-background-color:" + columnColor(item.kanbanColumn) + "22;"
                    + " -fx-text-fill:" + columnColor(item.kanbanColumn) + ";"
                    + " -fx-font-size:9px; -fx-font-weight:700;"
                    + " -fx-padding:2 6 2 6; -fx-background-radius:8;");
            refreshKpiAndProgress();
            if (onChanged != null) onChanged.run();
        });

        HBox contentRow = new HBox(8, numLbl, textLbl, editField, colBadge);
        contentRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(contentRow, Priority.ALWAYS);
        contentRow.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) enterEditMode(textLbl, editField, item, editBtn);
        });

        HBox row = new HBox(10, cb, contentRow, editBtn, delBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 12, 9, 14));

        String baseColor = index % 2 == 0 ? "#ffffff" : "#f7fbff";
        row.setStyle("-fx-background-color:" + baseColor
                + "; -fx-border-color:transparent transparent #e8f0fa transparent; -fx-border-width:0 0 1 0;");
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:#eef6ff;"
                + " -fx-border-color:transparent transparent #e8f0fa transparent; -fx-border-width:0 0 1 0;"));
        row.setOnMouseExited(e  -> row.setStyle("-fx-background-color:" + baseColor
                + "; -fx-border-color:transparent transparent #e8f0fa transparent; -fx-border-width:0 0 1 0;"));

        Region stripe = new Region();
        stripe.setMinWidth(4); stripe.setMaxWidth(4);
        stripe.setStyle("-fx-background-color:" + columnColor(item.kanbanColumn)
                + "; -fx-background-radius:2 0 0 2;");

        HBox outerRow = new HBox(0, stripe, row);
        HBox.setHgrow(row, Priority.ALWAYS);
        outerRow.setAlignment(Pos.CENTER_LEFT);
        outerRow.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                showItemContextMenu(item, textLbl, editField, editBtn, outerRow, e.getScreenX(), e.getScreenY());
                e.consume();
            } else if (e.getButton() == MouseButton.PRIMARY) {
                itemContextMenu.hide();
            }
        });

        return new VBox(outerRow);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VISTA KANBAN
    // ──────────────────────────────────────────────────────────────────────────

    private javafx.scene.Node buildKanbanView() {
        HBox board = new HBox(10);
        board.setPadding(new Insets(12, 14, 14, 14));
        board.setFillHeight(true);

        for (String[] colDef : KANBAN_COLS) {
            List<MutableItem> colItems = items.stream()
                    .filter(it -> colDef[0].equals(it.kanbanColumn))
                    .toList();
            board.getChildren().add(buildKanbanColumn(colDef, colItems));
        }

        ScrollPane sp = new ScrollPane(board);
        sp.setFitToHeight(true);
        sp.setFitToWidth(false);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("edge-to-edge");
        return sp;
    }

    private VBox buildKanbanColumn(String[] colDef, List<MutableItem> colItems) {
        String key = colDef[0], label = colDef[1].replace("  ", " ").trim(), color = colDef[2];

        Label headerLbl = new Label(label + "  (" + colItems.size() + ")");
        headerLbl.setStyle("-fx-font-size:12px; -fx-font-weight:700; -fx-text-fill:white;"
                + " -fx-padding:6 10 6 10; -fx-background-radius:6 6 0 0;"
                + " -fx-background-color:" + color + ";");
        headerLbl.setMaxWidth(Double.MAX_VALUE);

        // Botão + rápido
        Button addColBtn = new Button("+");
        addColBtn.setStyle("-fx-background-color:rgba(255,255,255,0.3); -fx-text-fill:white;"
                + " -fx-background-radius:4; -fx-font-weight:bold; -fx-font-size:14; -fx-cursor:hand;"
                + " -fx-padding:1 7 1 7;");
        addColBtn.setTooltip(new Tooltip("Adicionar item nesta coluna"));
        addColBtn.setOnAction(e -> showAddItemDialogForColumn(key));

        HBox colHeader = new HBox(headerLbl, addColBtn);
        colHeader.setAlignment(Pos.CENTER_LEFT);
        colHeader.setStyle("-fx-background-color:" + color + "; -fx-background-radius:6 6 0 0;");
        HBox.setHgrow(headerLbl, Priority.ALWAYS);

        VBox cards = new VBox(6);
        cards.setPadding(new Insets(8));
        if (colItems.isEmpty()) {
            Label empty = new Label("Sem itens");
            empty.setStyle("-fx-text-fill:#aabbcc; -fx-font-size:11px; -fx-padding:12;");
            cards.getChildren().add(empty);
        } else {
            for (MutableItem it : colItems) cards.getChildren().add(buildKanbanCard(it, color));
        }

        ScrollPane cardScroll = new ScrollPane(cards);
        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.setStyle("-fx-background-color:#f7fbff; -fx-border-color:transparent;");
        VBox.setVgrow(cardScroll, Priority.ALWAYS);

        VBox col = new VBox(0, colHeader, cardScroll);
        col.setPrefWidth(210); col.setMinWidth(190); col.setMaxWidth(240);
        col.setStyle("-fx-background-color:#f7fbff;"
                + " -fx-border-color:#b8cfe8; -fx-border-radius:6; -fx-background-radius:6;"
                + " -fx-effect:dropshadow(gaussian,rgba(3,24,62,0.07),8,0.2,0,2);");
        VBox.setVgrow(col, Priority.ALWAYS);
        return col;
    }

    private VBox buildKanbanCard(MutableItem item, String accentColor) {
        Label numLbl = new Label(String.format("#%02d", items.indexOf(item) + 1));
        numLbl.setStyle("-fx-font-family:'JetBrains Mono','Consolas',monospace;"
                + " -fx-font-size:9px; -fx-text-fill:#9eafc0;");

        Label textLbl = new Label(item.text);
        textLbl.setWrapText(true); textLbl.setMaxWidth(Double.MAX_VALUE);
        textLbl.setStyle((item.done
                ? "-fx-text-fill:#9eafc0; -fx-strikethrough:true;"
                : "-fx-text-fill:#0d1b2a;") + " -fx-font-size:12px; -fx-wrap-text:true;");

        HBox topRow = new HBox(4, numLbl);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        // Ações rápidas no card
        Button cbBtn = new Button(item.done ? "☑" : "☐");
        cbBtn.setStyle("-fx-background-color:transparent; -fx-cursor:hand;"
                + " -fx-font-size:14px; -fx-padding:0 2 0 2;");
        cbBtn.setTooltip(new Tooltip(item.done ? "Marcar como pendente" : "Marcar como concluído"));
        cbBtn.setOnAction(e -> {
            item.done = !item.done;
            item.kanbanColumn = item.done ? "concluido" : "backlog";
            repo.updateDone(item.id, item.done);
            refreshAll();
            if (onChanged != null) onChanged.run();
        });

        topRow.getChildren().addAll(spacer, cbBtn);

        VBox card = new VBox(4, topRow, textLbl);
        card.setStyle("-fx-background-color:white; -fx-background-radius:5;"
                + " -fx-border-color:" + accentColor + "; -fx-border-width:0 0 0 3;"
                + " -fx-border-radius:5; -fx-padding:8 10 8 10; -fx-cursor:hand;"
                + " -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.06),4,0.2,0,1);");

        // Hover
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color:#f0f7ff; -fx-background-radius:5;"
                + " -fx-border-color:" + accentColor + "; -fx-border-width:0 0 0 3;"
                + " -fx-border-radius:5; -fx-padding:8 10 8 10; -fx-cursor:hand;"
                + " -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.12),6,0.3,0,2);"));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color:white; -fx-background-radius:5;"
                + " -fx-border-color:" + accentColor + "; -fx-border-width:0 0 0 3;"
                + " -fx-border-radius:5; -fx-padding:8 10 8 10; -fx-cursor:hand;"
                + " -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.06),4,0.2,0,1);"));

        // Tooltip com texto completo
        if (item.text.length() > 60) {
            Tooltip tp = new Tooltip(item.text);
            tp.setWrapText(true); tp.setMaxWidth(280);
            Tooltip.install(card, tp);
        }

        // Clique direito → context menu
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                showItemContextMenu(item, textLbl, null, null, card, e.getScreenX(), e.getScreenY());
                e.consume();
            } else if (e.getButton() == MouseButton.PRIMARY) {
                itemContextMenu.hide();
            }
        });

        return card;
    }

    private void showAddItemDialogForColumn(String colKey) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Novo Item");
        dlg.setHeaderText("Adicionar item na coluna \u201c" + columnLabel(colKey) + "\u201d");
        dlg.setContentText("Texto:");
        dlg.showAndWait().ifPresent(text -> {
            if (text.isBlank()) return;
            IdeaChecklistItem saved = repo.addItem(idea.id(), text);
            if (!"backlog".equals(colKey)) repo.updateColumn(saved.id(), colKey);
            MutableItem mi = new MutableItem(saved);
            mi.kanbanColumn = colKey;
            mi.done = "concluido".equals(colKey);
            items.add(mi);
            refreshAll();
            if (onChanged != null) onChanged.run();
        });
    }

    // ── Modo de edição inline ─────────────────────────────────────────────────

    private void enterEditMode(Label textLbl, TextField editField, MutableItem item, Button editBtn) {
        if (editField == null) return; // kanban card — usa diálogo
        textLbl.setVisible(false); textLbl.setManaged(false);
        editField.setText(item.text);
        editField.setVisible(true); editField.setManaged(true);
        editField.requestFocus(); editField.selectAll();
        if (editBtn != null) editBtn.setText("💾");

        Runnable save = () -> {
            String newText = editField.getText().trim();
            if (!newText.isBlank() && !newText.equals(item.text)) {
                item.text = newText; repo.updateText(item.id, newText);
                if (onChanged != null) onChanged.run();
            }
            textLbl.setText(item.text);
            textLbl.setVisible(true); textLbl.setManaged(true);
            editField.setVisible(false); editField.setManaged(false);
            if (editBtn != null) editBtn.setText("✏");
        };
        Runnable cancel = () -> {
            textLbl.setVisible(true); textLbl.setManaged(true);
            editField.setVisible(false); editField.setManaged(false);
            if (editBtn != null) editBtn.setText("✏");
        };
        editField.setOnAction(e -> save.run());
        editField.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) cancel.run(); });
        if (editBtn != null) editBtn.setOnAction(e -> {
            if ("💾".equals(editBtn.getText())) save.run();
            else enterEditMode(textLbl, editField, item, editBtn);
        });
    }

    // ── Context menu ──────────────────────────────────────────────────────────

    private void showItemContextMenu(MutableItem item, Label textLbl,
                                     TextField editField, Button editBtn,
                                     javafx.scene.Node anchor, double sx, double sy) {
        itemContextMenu.hide();
        itemContextMenu.getItems().clear();
        itemContextMenu.setAutoHide(true);

        MenuItem copyItem = new MenuItem("📋  Copiar Texto");
        copyItem.setOnAction(e -> copyToClipboard(item.text));

        MenuItem editItem = new MenuItem("✏  Editar");
        if (editField != null) {
            editItem.setOnAction(e -> enterEditMode(textLbl, editField, item, editBtn));
        } else {
            // No Kanban — usa TextInputDialog
            editItem.setOnAction(e -> {
                TextInputDialog dlg = new TextInputDialog(item.text);
                dlg.setTitle("Editar Item"); dlg.setHeaderText(null);
                dlg.setContentText("Texto:");
                dlg.showAndWait().ifPresent(t -> {
                    if (!t.isBlank() && !t.equals(item.text)) {
                        item.text = t; repo.updateText(item.id, t);
                        refreshAll();
                        if (onChanged != null) onChanged.run();
                    }
                });
            });
        }

        MenuItem toggleItem = item.done
                ? new MenuItem("☐  Marcar como Pendente")
                : new MenuItem("✔  Marcar como Concluído");
        toggleItem.setOnAction(e -> {
            item.done = !item.done;
            item.kanbanColumn = item.done ? "concluido"
                    : (item.kanbanColumn.equals("concluido") ? "backlog" : item.kanbanColumn);
            repo.updateDone(item.id, item.done);
            refreshAll();
            if (onChanged != null) onChanged.run();
        });

        // Submenu "Mover para..."
        Menu moveMenu = new Menu("🔄  Mover para...");
        for (String[] c : KANBAN_COLS) {
            String cKey = c[0], cLabel = c[1].replace("  ", " ").trim();
            MenuItem mi = new MenuItem(cLabel);
            if (cKey.equals(item.kanbanColumn)) mi.setStyle("-fx-font-weight:bold; -fx-text-fill:#1565c0;");
            mi.setOnAction(e -> moveItemToColumn(item, cKey));
            moveMenu.getItems().add(mi);
        }

        SeparatorMenuItem sep = new SeparatorMenuItem();

        MenuItem deleteItem = new MenuItem("🗑  Excluir Item");
        deleteItem.setStyle("-fx-text-fill:#c62828;");
        deleteItem.setOnAction(e -> deleteItem(item));

        itemContextMenu.getItems().addAll(copyItem, editItem, toggleItem, moveMenu, sep, deleteItem);
        itemContextMenu.show(anchor, sx, sy);
    }

    private void moveItemToColumn(MutableItem item, String colKey) {
        item.kanbanColumn = colKey;
        item.done = "concluido".equals(colKey);
        repo.updateColumn(item.id, colKey);
        refreshAll();
        if (onChanged != null) onChanged.run();
    }

    private void deleteItem(MutableItem item) {
        repo.deleteItem(item.id);
        items.remove(item);
        refreshAll();
        if (onChanged != null) onChanged.run();
    }

    // ── Ações em lote ─────────────────────────────────────────────────────────

    private void markAll(boolean done) {
        for (MutableItem it : items) {
            if (it.done != done) {
                it.done = done;
                it.kanbanColumn = done ? "concluido"
                        : (it.kanbanColumn.equals("concluido") ? "backlog" : it.kanbanColumn);
                repo.updateDone(it.id, done);
            }
        }
        refreshAll();
        if (onChanged != null) onChanged.run();
    }

    private void clearDoneItems() {
        long count = items.stream().filter(it -> it.done).count();
        if (count == 0) { new Alert(Alert.AlertType.INFORMATION, "Sem itens concluídos para remover.", ButtonType.OK).showAndWait(); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Limpar Concluídos");
        confirm.setHeaderText("Remover itens concluídos?");
        confirm.setContentText(count + " item(ns) serão excluídos permanentemente.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                List<MutableItem> done = items.stream().filter(it -> it.done).toList();
                done.forEach(it -> repo.deleteItem(it.id));
                items.removeAll(done);
                refreshAll();
                if (onChanged != null) onChanged.run();
            }
        });
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private void refreshAll() {
        refreshKpiAndProgress();
        refreshContentArea();
    }

    private void refreshKpiAndProgress() {
        int total   = items.size();
        int done    = (int) items.stream().filter(it -> it.done).count();
        int pending = total - done;
        double pct  = total == 0 ? 0.0 : (double) done / total;

        if (kpiTotal   != null) kpiTotal.setText(String.valueOf(total));
        if (kpiDone    != null) kpiDone.setText(String.valueOf(done));
        if (kpiPending != null) kpiPending.setText(String.valueOf(pending));
        if (kpiPercent != null) kpiPercent.setText(Math.round(pct * 100) + "%");

        if (progressBar != null) {
            progressBar.setProgress(pct);
            String accent = pct == 1.0 ? "#1b5e20" : pct >= 0.7 ? "#2e7d32" : pct >= 0.4 ? "#f57f17" : "#c62828";
            progressBar.setStyle("-fx-accent:" + accent
                    + "; -fx-background-color:#d6e4f5; -fx-background-radius:7; -fx-border-radius:7;");
        }
        if (progressLabel != null) progressLabel.setText(
                done + " de " + total + " itens concluídos"
                + (total > 0 ? "  (" + Math.round(pct * 100) + "%)" : ""));
    }

    // ── Rodapé ────────────────────────────────────────────────────────────────

    private HBox buildFooter(Stage stage) {
        Label hint = new Label("💡  Duplo clique para editar  ·  Botão direito para mais opções  ·  ⬛ Kanban mostra colunas de progresso");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#7a9abc;"
                + " -fx-font-family:'JetBrains Mono','Consolas',monospace;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button printBtn = new Button("🖨  Imprimir Checklist");
        printBtn.getStyleClass().add("secondary-button");
        printBtn.setOnAction(e -> printChecklist());

        Button closeBtn = new Button("✕  Fechar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> stage.close());
        HBox footer = new HBox(12, hint, spacer, printBtn, closeBtn);
        footer.setPadding(new Insets(10, 18, 10, 18)); footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color:#edf1f8; -fx-border-color:#b8cfe8; -fx-border-width:1 0 0 0;");
        return footer;
    }


    // ── Helpers de Kanban ─────────────────────────────────────────────────────

    private String columnLabel(String key) {
        for (String[] c : KANBAN_COLS) if (c[0].equals(key)) return c[1].replace("  ", " ").trim();
        return key;
    }
    private String columnColor(String key) {
        for (String[] c : KANBAN_COLS) if (c[0].equals(key)) return c[2];
        return "#546e7a";
    }

    // ── Helpers visuais ───────────────────────────────────────────────────────

    private void applyDoneStyle(Label lbl, boolean done) {
        lbl.setStyle(done
                ? "-fx-text-fill:#9eafc0; -fx-strikethrough:true;  -fx-font-size:12px;"
                : "-fx-text-fill:#0d1b2a; -fx-strikethrough:false; -fx-font-size:12px;");
    }

    private void copyToClipboard(String text) {
        ClipboardContent c = new ClipboardContent(); c.putString(text);
        Clipboard.getSystemClipboard().setContent(c);
    }

    // ── Fábrica estática ──────────────────────────────────────────────────────

    public static void open(ProjectIdea idea, Runnable onChanged) {
        IdeaChecklistRepository repo = AppContextHolder.get().ideaChecklistRepository();
        new ProjectChecklistWindow(idea, repo, onChanged).show();
    }
}

