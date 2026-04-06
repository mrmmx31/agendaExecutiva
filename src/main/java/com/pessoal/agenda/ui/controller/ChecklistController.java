package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.*;
import com.pessoal.agenda.repository.ProtocolRepository;
import com.pessoal.agenda.ui.view.ProtocolExecutionWindow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller da aba Protocolos Operacionais.
 *
 * Layout:
 *   TOP    — toolbar com filtros completos e botão de novo protocolo
 *   MIDDLE — barra de KPIs (total, execuções ativas)
 *   BOTTOM — SplitPane: lista de protocolos (esq) | formulário científico (dir)
 */
public class ChecklistController {

    private final SharedContext ctx;

    private final ObservableList<Protocol> protocolItems = FXCollections.observableArrayList();

    // ── filtros ───────────────────────────────────────────────────────────────
    private String categoryFilter = null;
    private String typeFilter     = null;
    private String statusFilter   = null;

    // ── KPI labels ────────────────────────────────────────────────────────────
    private Label kpiTotal;
    private Label kpiActive;

    // ── campos do formulário ──────────────────────────────────────────────────
    private Long                            editingId = null;
    private Label                           formModeLabel;
    private Button                          saveBtn;
    private Button                          cancelFormBtn;
    private Button                          executeBtn;
    private TextField                       nameField;
    private TextField                       validityDaysField;
    private ComboBox<ProtocolExecutionType> typeCombo;
    private ComboBox<String>                catFormCombo;
    private ComboBox<TaskOption>            linkedTaskCombo;
    private HBox                            linkedTaskRow;
    private TextArea                        descArea;
    private VBox                            stepsEditorBox;
    private ListView<Protocol>              protocolListView;

    private final List<StepRow> stepRows = new ArrayList<>();

    private record TaskOption(Long id, String title) {
        static final TaskOption NONE = new TaskOption(null, "— nenhuma tarefa vinculada —");
        @Override public String toString() { return title; }
    }

    private record StatusOption(String key, String label) {
        @Override public String toString() { return label; }
    }

    public ChecklistController(SharedContext ctx) { this.ctx = ctx; }

    // ══════════════════════════════════════════════════════════════════════════
    // Construção da aba
    // ══════════════════════════════════════════════════════════════════════════

    public Tab buildTab() {
        Tab tab = new Tab("Protocolos Operacionais");
        tab.setClosable(false);

        kpiTotal  = new Label("0");
        kpiActive = new Label("0");

        // ── KPI bar ──────────────────────────────────────────────────────────
        HBox kpiBar = new HBox(10,
                UIHelper.createMiniKpi("TOTAL DE PROTOCOLOS", kpiTotal,  "kpi-blue"),
                UIHelper.createMiniKpi("EXECUÇÕES ATIVAS",    kpiActive, "kpi-orange"));
        kpiBar.setPadding(new Insets(10, 14, 10, 14));
        for (Node n : kpiBar.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // ── Lista de protocolos ───────────────────────────────────────────────
        protocolListView = new ListView<>(protocolItems);
        protocolListView.getStyleClass().add("clean-list");
        protocolListView.setCellFactory(lv -> buildProtocolCell());
        VBox.setVgrow(protocolListView, Priority.ALWAYS);

        protocolListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) loadProtocolIntoForm(sel); });

        protocolListView.setOnMouseClicked(e -> {
            javafx.scene.Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof ListCell)) node = node.getParent();
            if (node == null || ((ListCell<?>) node).isEmpty()) {
                protocolListView.getSelectionModel().clearSelection();
                clearForm();
            } else if (e.getClickCount() == 2) {
                Protocol sel = protocolListView.getSelectionModel().getSelectedItem();
                if (sel != null) openExecutionWindow(sel);
            }
        });

        Label hint = new Label("↵  Duplo clique → abrir janela de execução do protocolo");
        hint.getStyleClass().add("study-dates-label");

        Button removeBtn = new Button("🗑  Remover");
        removeBtn.getStyleClass().add("danger-button");
        removeBtn.setOnAction(e -> removeSelectedProtocol());

        HBox leftBottom = new HBox(UIHelper.createSpacer(), removeBtn);

        VBox leftPanel = new VBox(8, protocolListView, hint, leftBottom);
        leftPanel.setPadding(new Insets(12));
        leftPanel.getStyleClass().add("section-card");
        VBox.setVgrow(leftPanel, Priority.ALWAYS);

        // ── Formulário (painel direito) ───────────────────────────────────────
        VBox rightContent = buildForm();
        ScrollPane rightScroll = new ScrollPane(rightContent);
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(rightScroll, Priority.ALWAYS);

        SplitPane split = new SplitPane(leftPanel, rightScroll);
        split.setDividerPositions(0.52);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox content = new VBox(0, buildToolbar(), kpiBar, split);
        content.setPadding(new Insets(0, 14, 14, 14));
        tab.setContent(content);

        refresh();
        return tab;
    }

    // ── Toolbar com filtros ────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Button newBtn = new Button("＋  Novo Protocolo");
        newBtn.getStyleClass().add("primary-button");
        newBtn.setOnAction(e -> clearForm());

        // ── Filtro de categoria ───────────────────────────────────────────
        ComboBox<String> catFilterCombo = new ComboBox<>();
        catFilterCombo.getStyleClass().add("input-control");
        catFilterCombo.setPrefWidth(155);
        catFilterCombo.setPromptText("Categoria");
        catFilterCombo.getItems().add("Todas as categorias");
        ctx.checklistCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = catFilterCombo.getValue();
            catFilterCombo.getItems().setAll("Todas as categorias");
            catFilterCombo.getItems().addAll(ctx.checklistCatNames);
            catFilterCombo.setValue(cur != null ? cur : "Todas as categorias");
        });
        catFilterCombo.getItems().addAll(ctx.checklistCatNames);
        catFilterCombo.setValue("Todas as categorias");
        catFilterCombo.setOnAction(e -> {
            String v = catFilterCombo.getValue();
            categoryFilter = "Todas as categorias".equals(v) ? null : v;
            refresh();
        });

        // ── Filtro de tipo ────────────────────────────────────────────────
        ComboBox<String> typeFilterCombo = new ComboBox<>();
        typeFilterCombo.getStyleClass().add("input-control");
        typeFilterCombo.setPrefWidth(185);
        typeFilterCombo.setPromptText("Tipo de protocolo");
        typeFilterCombo.getItems().add("Todos os tipos");
        for (ProtocolExecutionType t : ProtocolExecutionType.values())
            typeFilterCombo.getItems().add(t.name());
        typeFilterCombo.setValue("Todos os tipos");
        typeFilterCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String s) {
                if (s == null || "Todos os tipos".equals(s)) return "Todos os tipos";
                try {
                    ProtocolExecutionType t = ProtocolExecutionType.valueOf(s);
                    return t.icon() + " " + t.label();
                } catch (Exception ignored) { return s; }
            }
            @Override public String fromString(String s) { return s; }
        });
        typeFilterCombo.setOnAction(e -> {
            String v = typeFilterCombo.getValue();
            typeFilter = "Todos os tipos".equals(v) ? null : v;
            refresh();
        });

        // ── Filtro de status ──────────────────────────────────────────────
        List<StatusOption> statusOpts = List.of(
                new StatusOption(null,              "Todos os status"),
                new StatusOption("COM_ATIVA",       "● Com execução ativa"),
                new StatusOption("SEM_ATIVA",       "○ Sem execução ativa"),
                new StatusOption("VALIDADE_VENCIDA","⚠ Validade vencida"),
                new StatusOption("VENCE_7DIAS",     "⏰ Vence em 7 dias")
        );
        ComboBox<StatusOption> statusFilterCombo = new ComboBox<>();
        statusFilterCombo.getStyleClass().add("input-control");
        statusFilterCombo.setPrefWidth(200);
        statusFilterCombo.getItems().addAll(statusOpts);
        statusFilterCombo.setValue(statusOpts.get(0));
        statusFilterCombo.setOnAction(e -> {
            StatusOption sel = statusFilterCombo.getValue();
            statusFilter = sel != null ? sel.key() : null;
            refresh();
        });

        Button clearBtn = new Button("✕  Limpar");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> {
            categoryFilter = null; typeFilter = null; statusFilter = null;
            catFilterCombo.setValue("Todas as categorias");
            typeFilterCombo.setValue("Todos os tipos");
            statusFilterCombo.setValue(statusOpts.get(0));
            refresh();
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, newBtn, spacer,
                catFilterCombo, typeFilterCombo, statusFilterCombo, clearBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("agenda-top-bar");
        return bar;
    }

    // ── Célula customizada da lista ────────────────────────────────────────────

    private ListCell<Protocol> buildProtocolCell() {
        return new ListCell<>() {
            private final Label typeBadge     = new Label();
            private final Label nameLbl       = new Label();
            private final Label catLbl        = new Label();
            private final Label execBadge     = new Label();
            private final Label validityBadge = new Label();
            private final Label taskLbl       = new Label();
            private final Label descLbl       = new Label();
            private final VBox  container;
            {
                typeBadge.getStyleClass().addAll("study-badge", "badge-type");
                nameLbl.getStyleClass().add("study-plan-title");
                catLbl.getStyleClass().add("study-dates-label");
                execBadge.getStyleClass().addAll("study-badge", "badge-status-em_andamento");
                taskLbl.getStyleClass().add("study-dates-label");
                descLbl.getStyleClass().add("study-plan-detail");
                descLbl.setWrapText(false);
                Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                HBox top  = new HBox(6, typeBadge, nameLbl, sp, validityBadge, execBadge);
                HBox bot  = new HBox(6, catLbl, taskLbl, descLbl);
                top.setAlignment(Pos.CENTER_LEFT);
                bot.setAlignment(Pos.CENTER_LEFT);
                container = new VBox(3, top, bot);
                container.setPadding(new Insets(4, 0, 4, 0));
                setGraphic(container); setText(null);
            }

            @Override protected void updateItem(Protocol p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); return; }
                setGraphic(container);
                typeBadge.setText("  " + p.executionType().icon() + " " + p.executionType().label() + "  ");
                nameLbl.setText(p.name());
                catLbl.setText(p.category() != null ? p.category() : "Geral");

                if (p.hasLinkedTask() && p.linkedTaskTitle() != null) {
                    taskLbl.setText("  📌 " + p.linkedTaskTitle());
                    taskLbl.setVisible(true); taskLbl.setManaged(true);
                } else {
                    taskLbl.setVisible(false); taskLbl.setManaged(false);
                }

                int active = AppContextHolder.get().protocolRepository().countActiveExecutionsOf(p.id());
                execBadge.setVisible(active > 0); execBadge.setManaged(active > 0);
                execBadge.setText("  ● " + active + " ativa  ");

                validityBadge.getStyleClass().removeAll(
                        "deadline-overdue", "deadline-warn", "deadline-ok", "study-dates-label");
                if (p.hasValidity()) {
                    java.time.LocalDate nextDue = AppContextHolder.get().protocolRepository()
                            .nextDueDate(p.id(), p.validityDays());
                    if (nextDue != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.now(), nextDue);
                        if (days < 0) {
                            validityBadge.setText("  ⚠ Vencido há " + (-days) + "d  ");
                            validityBadge.getStyleClass().add("deadline-overdue");
                        } else if (days <= 7) {
                            validityBadge.setText("  ⏰ Vence em " + days + "d  ");
                            validityBadge.getStyleClass().add("deadline-warn");
                        } else {
                            validityBadge.setText("  📅 " + days + "d restantes  ");
                            validityBadge.getStyleClass().add("deadline-ok");
                        }
                    } else {
                        validityBadge.setText("  📅 " + p.validityDays() + "d val.  ");
                        validityBadge.getStyleClass().add("study-dates-label");
                    }
                    validityBadge.setVisible(true); validityBadge.setManaged(true);
                } else {
                    validityBadge.setVisible(false); validityBadge.setManaged(false);
                }

                String d = p.description() != null && !p.description().isBlank()
                        ? "  —  " + p.description().substring(0, Math.min(60, p.description().length()))
                          + (p.description().length() > 60 ? "…" : "") : "";
                descLbl.setText(d);
            }
        };
    }

    // ── Formulário (section-cards científicos) ─────────────────────────────────

    private VBox buildForm() {
        // ── Cabeçalho do formulário ───────────────────────────────────────
        formModeLabel = new Label("📋 Novo Protocolo Operacional");
        formModeLabel.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #03183e;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0; -fx-padding: 0 0 8 0;");
        formModeLabel.setWrapText(true);
        formModeLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Campos ────────────────────────────────────────────────────────
        nameField = new TextField();
        nameField.getStyleClass().add("input-control");
        nameField.setPromptText("Ex.: Ligar Motor X, Pré-voo, Calibração do espectrômetro...");

        typeCombo = new ComboBox<>();
        typeCombo.getStyleClass().add("input-control");
        typeCombo.getItems().addAll(ProtocolExecutionType.values());
        typeCombo.setValue(ProtocolExecutionType.RECORRENTE);
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        catFormCombo = new ComboBox<>();
        catFormCombo.getStyleClass().add("input-control");
        catFormCombo.setItems(ctx.checklistCatNames);
        catFormCombo.setMaxWidth(Double.MAX_VALUE);
        if (!ctx.checklistCatNames.isEmpty()) catFormCombo.setValue(ctx.checklistCatNames.get(0));

        descArea = new TextArea();
        descArea.getStyleClass().add("input-control");
        descArea.setPromptText("Objetivo, contexto e quando utilizar este protocolo...");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        validityDaysField = new TextField();
        validityDaysField.getStyleClass().add("input-control");
        validityDaysField.setPromptText("0 = sem validade");
        validityDaysField.setPrefWidth(120);
        validityDaysField.setMaxWidth(160);

        Label vHint = new Label(
                "Nº de dias que o resultado permanece válido após a última conclusão.\n"
              + "Ex: 30 = mensal · 90 = trimestral · 180 = semestral · 365 = anual");
        vHint.getStyleClass().add("study-dates-label");
        vHint.setWrapText(true);
        HBox.setHgrow(vHint, Priority.ALWAYS);

        Label typeHint = new Label(ProtocolExecutionType.RECORRENTE.description());
        typeHint.getStyleClass().add("study-dates-label");
        typeHint.setWrapText(true);

        // ── Vínculo com tarefa ────────────────────────────────────────────
        linkedTaskCombo = new ComboBox<>();
        linkedTaskCombo.getStyleClass().add("input-control");
        linkedTaskCombo.setMaxWidth(Double.MAX_VALUE);
        linkedTaskCombo.setPromptText("Selecionar tarefa da agenda...");
        HBox.setHgrow(linkedTaskCombo, Priority.ALWAYS);

        linkedTaskRow = new HBox(0, linkedTaskCombo);
        linkedTaskRow.setAlignment(Pos.CENTER_LEFT);
        linkedTaskRow.setVisible(false);
        linkedTaskRow.setManaged(false);

        typeCombo.setOnAction(e -> {
            ProtocolExecutionType t = typeCombo.getValue();
            if (t != null) typeHint.setText(t.description());
            boolean isTarefa = (t == ProtocolExecutionType.TAREFA);
            linkedTaskRow.setVisible(isTarefa);
            linkedTaskRow.setManaged(isTarefa);
            if (isTarefa) reloadTaskOptions();
        });

        // ── Linha tipo + categoria ────────────────────────────────────────
        VBox typeCtrl = labeledControl("Tipo de Execução", typeCombo);
        VBox catCtrl  = labeledControl("Categoria do Protocolo", catFormCombo);
        HBox.setHgrow(typeCtrl, Priority.ALWAYS);
        HBox.setHgrow(catCtrl,  Priority.ALWAYS);
        HBox typeCatRow = new HBox(10, typeCtrl, catCtrl);

        // Linha de validade
        HBox validityRow = new HBox(10, validityDaysField, vHint);
        validityRow.setAlignment(Pos.TOP_LEFT);

        // ── Section card: Definição do Protocolo ──────────────────────────
        VBox definitionCard = buildSectionCard("📋 Definição do Protocolo",
                fieldRow("Nome *", nameField),
                typeCatRow,
                typeHint,
                linkedTaskRow,
                fieldRow("Descrição / Objetivo", descArea),
                namedRow("Validade (dias)", validityRow));

        // ── Section card: Passos do Protocolo ─────────────────────────────
        stepsEditorBox = new VBox(4);
        stepsEditorBox.setPadding(new Insets(2, 0, 2, 0));

        Button addStepBtn = new Button("＋  Adicionar Passo");
        addStepBtn.getStyleClass().add("secondary-button");
        addStepBtn.setStyle("-fx-font-size: 11px;");
        addStepBtn.setOnAction(e -> addStepRow(null));

        ScrollPane stepsScroll = new ScrollPane(stepsEditorBox);
        stepsScroll.setFitToWidth(true);
        stepsScroll.setPrefHeight(200);
        stepsScroll.setMaxHeight(350);
        stepsScroll.setStyle("-fx-background-color: #f8fafc;"
                + " -fx-border-color: #dce8f5; -fx-border-radius: 5;");

        VBox stepsCard = buildSectionCardWithAction("⚙ Passos do Protocolo", addStepBtn, stepsScroll);

        // ── Botões de ação ────────────────────────────────────────────────
        saveBtn       = new Button("💾  Salvar Protocolo");
        saveBtn.getStyleClass().add("primary-button");
        cancelFormBtn = new Button("✕  Cancelar");
        cancelFormBtn.getStyleClass().add("secondary-button");
        executeBtn    = new Button("▶  Executar Protocolo");
        executeBtn.getStyleClass().add("secondary-button");
        executeBtn.setVisible(false); executeBtn.setManaged(false);

        saveBtn.setOnAction(e -> saveProtocol());
        cancelFormBtn.setOnAction(e -> clearForm());
        executeBtn.setOnAction(e -> {
            Protocol sel = protocolListView.getSelectionModel().getSelectedItem();
            if (sel != null) openExecutionWindow(sel);
        });

        HBox btnRow = new HBox(8, saveBtn, cancelFormBtn,
                UIHelper.createSpacer(), executeBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));
        btnRow.setStyle("-fx-border-color: #d6e4f5; -fx-border-width: 1 0 0 0;");

        VBox panel = new VBox(12, formModeLabel, definitionCard, stepsCard, btnRow);
        panel.setPadding(new Insets(14));
        return panel;
    }

    // ── Linhas de passo ────────────────────────────────────────────────────────

    private void addStepRow(ProtocolStep existing) {
        int order = stepRows.size() + 1;

        Label orderLbl = new Label(order + ".");
        orderLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 12px;"
                + " -fx-text-fill: #5a7a9e; -fx-min-width: 26px;");

        TextField stepField = new TextField(existing != null ? existing.stepText() : "");
        stepField.getStyleClass().add("input-control");
        stepField.setPromptText("Descrição do passo...");
        HBox.setHgrow(stepField, Priority.ALWAYS);

        CheckBox criticalCb = new CheckBox("⚠ Crítico");
        criticalCb.setSelected(existing != null && existing.critical());
        criticalCb.setStyle("-fx-font-size: 11px; -fx-text-fill: #b71c1c;");
        criticalCb.setTooltip(new Tooltip("Passo obrigatório — bloqueia conclusão se não marcado"));

        Button upBtn   = new Button("▲"); upBtn.getStyleClass().add("icon-button");
        Button downBtn = new Button("▼"); downBtn.getStyleClass().add("icon-button");
        Button delBtn  = new Button("✕"); delBtn.getStyleClass().addAll("icon-button", "danger");
        upBtn.setTooltip(new Tooltip("Mover para cima"));
        downBtn.setTooltip(new Tooltip("Mover para baixo"));
        delBtn.setTooltip(new Tooltip("Remover passo"));

        HBox row = new HBox(6, orderLbl, stepField, criticalCb, upBtn, downBtn, delBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 4, 3, 6));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 4;"
                + " -fx-border-color: #e8eef4; -fx-border-radius: 4;"
                + " -fx-border-width: 1;");

        StepRow sr = new StepRow(existing != null ? existing.id() : null, stepField, criticalCb, row);
        stepRows.add(sr);
        stepsEditorBox.getChildren().add(row);

        upBtn.setOnAction(e -> moveStep(sr, -1));
        downBtn.setOnAction(e -> moveStep(sr, +1));
        delBtn.setOnAction(e -> removeStep(sr));
    }

    private void moveStep(StepRow sr, int dir) {
        int idx = stepRows.indexOf(sr), newIdx = idx + dir;
        if (newIdx < 0 || newIdx >= stepRows.size()) return;
        stepRows.remove(idx); stepRows.add(newIdx, sr);
        stepsEditorBox.getChildren().remove(sr.row());
        stepsEditorBox.getChildren().add(newIdx, sr.row());
        renumber();
    }

    private void removeStep(StepRow sr) {
        stepRows.remove(sr);
        stepsEditorBox.getChildren().remove(sr.row());
        renumber();
    }

    private void renumber() {
        for (int i = 0; i < stepRows.size(); i++)
            ((Label) stepRows.get(i).row().getChildren().get(0)).setText((i + 1) + ".");
    }

    private record StepRow(Long dbId, TextField field, CheckBox criticalCb, HBox row) {}

    // ══════════════════════════════════════════════════════════════════════════
    // Lógica
    // ══════════════════════════════════════════════════════════════════════════

    public void refresh() {
        ProtocolRepository repo = AppContextHolder.get().protocolRepository();
        protocolItems.setAll(repo.findAllProtocols(categoryFilter, typeFilter, statusFilter));
        if (kpiTotal  != null) kpiTotal.setText(String.valueOf(protocolItems.size()));
        if (kpiActive != null) kpiActive.setText(String.valueOf(repo.countActiveExecutions()));
    }

    private void loadProtocolIntoForm(Protocol p) {
        editingId = p.id();
        nameField.setText(p.name());
        typeCombo.setValue(p.executionType());
        catFormCombo.setValue(p.category());
        descArea.setText(p.description() != null ? p.description() : "");
        validityDaysField.setText(p.validityDays() > 0 ? String.valueOf(p.validityDays()) : "");

        boolean isTarefa = p.executionType() == ProtocolExecutionType.TAREFA;
        linkedTaskRow.setVisible(isTarefa); linkedTaskRow.setManaged(isTarefa);
        if (isTarefa) {
            reloadTaskOptions();
            if (p.linkedTaskId() != null) {
                linkedTaskCombo.getItems().stream()
                        .filter(o -> o.id() != null && o.id().equals(p.linkedTaskId()))
                        .findFirst().ifPresentOrElse(
                                linkedTaskCombo::setValue,
                                () -> linkedTaskCombo.setValue(TaskOption.NONE));
            } else {
                linkedTaskCombo.setValue(TaskOption.NONE);
            }
        }

        stepRows.clear(); stepsEditorBox.getChildren().clear();
        for (ProtocolStep s : AppContextHolder.get().protocolRepository().findSteps(p.id()))
            addStepRow(s);

        formModeLabel.setText("✏  Editando: " + p.name());
        saveBtn.setText("💾  Salvar Alterações");
        cancelFormBtn.setText("✕  Cancelar Edição");
        cancelFormBtn.getStyleClass().removeAll("secondary-button", "danger-button");
        cancelFormBtn.getStyleClass().add("danger-button");
        executeBtn.setVisible(true); executeBtn.setManaged(true);
    }

    private void clearForm() {
        editingId = null;
        nameField.clear(); validityDaysField.clear(); descArea.clear();
        typeCombo.setValue(ProtocolExecutionType.RECORRENTE);
        catFormCombo.setValue(ctx.checklistCatNames.isEmpty() ? null : ctx.checklistCatNames.get(0));
        linkedTaskRow.setVisible(false); linkedTaskRow.setManaged(false);
        linkedTaskCombo.getItems().clear(); linkedTaskCombo.setValue(null);
        stepRows.clear(); stepsEditorBox.getChildren().clear();
        formModeLabel.setText("📋 Novo Protocolo Operacional");
        saveBtn.setText("💾  Salvar Protocolo");
        cancelFormBtn.setText("✕  Cancelar");
        cancelFormBtn.getStyleClass().removeAll("secondary-button", "danger-button");
        cancelFormBtn.getStyleClass().add("secondary-button");
        executeBtn.setVisible(false); executeBtn.setManaged(false);
        if (protocolListView != null) protocolListView.getSelectionModel().clearSelection();
    }

    private void saveProtocol() {
        String name = nameField.getText().trim();
        if (name.isBlank())    { ctx.setStatus("Nome do protocolo é obrigatório."); return; }
        if (stepRows.isEmpty()){ ctx.setStatus("Adicione ao menos um passo ao protocolo."); return; }

        ProtocolExecutionType type = typeCombo.getValue() != null
                ? typeCombo.getValue() : ProtocolExecutionType.RECORRENTE;
        String cat  = catFormCombo.getValue() != null ? catFormCombo.getValue() : "Geral";
        String desc = descArea.getText();
        int    validityDays = safeParseInt(validityDaysField.getText());

        Long linkedTaskId = null;
        if (type == ProtocolExecutionType.TAREFA) {
            TaskOption sel = linkedTaskCombo.getValue();
            if (sel != null && sel.id() != null) linkedTaskId = sel.id();
        }

        ProtocolRepository repo = AppContextHolder.get().protocolRepository();
        long protId;
        if (editingId == null) {
            protId = repo.saveProtocol(name, type, cat, desc, linkedTaskId, validityDays);
            ctx.setStatus("Protocolo criado: \"" + name + "\".");
        } else {
            repo.updateProtocol(editingId, name, type, cat, desc, linkedTaskId, validityDays);
            repo.deleteAllSteps(editingId);
            protId = editingId;
            ctx.setStatus("Protocolo atualizado: \"" + name + "\".");
        }

        for (int i = 0; i < stepRows.size(); i++) {
            String text = stepRows.get(i).field().getText().trim();
            if (!text.isBlank())
                repo.saveStep(protId, i + 1, text, null, stepRows.get(i).criticalCb().isSelected());
        }
        clearForm(); refresh(); ctx.triggerDashboardRefresh();
    }

    private void removeSelectedProtocol() {
        Protocol sel = protocolListView.getSelectionModel().getSelectedItem();
        if (sel == null) { ctx.setStatus("Selecione um protocolo para remover."); return; }
        int active = AppContextHolder.get().protocolRepository().countActiveExecutionsOf(sel.id());
        String extra = active > 0
                ? "\n⚠ " + active + " execução(ões) ativa(s) serão canceladas!" : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remover Protocolo Operacional");
        confirm.setHeaderText("Remover \"" + sel.name() + "\"?");
        confirm.setContentText(
                "Todos os passos e o histórico de execuções serão removidos permanentemente." + extra);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            AppContextHolder.get().protocolRepository().deleteProtocol(sel.id());
            clearForm(); refresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Protocolo \"" + sel.name() + "\" removido.");
        });
    }

    private void reloadTaskOptions() {
        TaskOption current = linkedTaskCombo.getValue();
        linkedTaskCombo.getItems().clear();
        linkedTaskCombo.getItems().add(TaskOption.NONE);
        AppContextHolder.get().taskRepository().findOpenTasks().stream()
                .map(t -> new TaskOption(t.id(), t.title()))
                .forEach(linkedTaskCombo.getItems()::add);
        if (current != null && current.id() != null) {
            linkedTaskCombo.getItems().stream()
                    .filter(o -> o.id() != null && o.id().equals(current.id()))
                    .findFirst().ifPresentOrElse(
                            linkedTaskCombo::setValue,
                            () -> linkedTaskCombo.setValue(TaskOption.NONE));
        } else {
            linkedTaskCombo.setValue(TaskOption.NONE);
        }
    }

    private void openExecutionWindow(Protocol p) {
        new ProtocolExecutionWindow(p, AppContextHolder.get().protocolRepository(), this::refresh).show();
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── Builder helpers (estilo científico, igual a outras janelas) ────────────

    /** Controle com rótulo acima (coluna). */
    private static VBox labeledControl(String label, Node control) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a7a9e; -fx-font-weight: 600;");
        lbl.setMinWidth(Region.USE_PREF_SIZE);
        VBox box = new VBox(3, lbl, control);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Linha rótulo (esq.) + controle (dir., expande). */
    private static HBox fieldRow(String label, Node control) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #5a7a9e; -fx-font-weight: 600;");
        lbl.setMinWidth(160);
        HBox row = new HBox(10, lbl, control);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    /** Linha com rótulo curto + controle sem expansão. */
    private static HBox namedRow(String label, Node control) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #5a7a9e; -fx-font-weight: 600;");
        lbl.setMinWidth(160);
        HBox row = new HBox(10, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Card de seção estilo científico com múltiplos conteúdos. */
    private static VBox buildSectionCard(String title, Node... content) {
        Label hdr = new Label(title);
        hdr.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #03183e;"
                + " -fx-padding: 0 0 6 0;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0;");
        hdr.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(10, hdr);
        card.getChildren().addAll(content);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    /** Card de seção com botão de ação no cabeçalho. */
    private static VBox buildSectionCardWithAction(String title, Button action, Node... content) {
        Label hdr = new Label(title);
        hdr.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #03183e;");
        hdr.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(hdr, Priority.ALWAYS);
        HBox headerRow = new HBox(8, hdr, action);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setStyle("-fx-padding: 0 0 6 0;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0;");
        VBox card = new VBox(10, headerRow);
        card.getChildren().addAll(content);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }
}
