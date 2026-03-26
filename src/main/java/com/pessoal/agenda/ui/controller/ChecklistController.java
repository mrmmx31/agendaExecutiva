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
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller da aba Protocolos Operacionais.
 *
 * Layout Master-Detail:
 *   LEFT  — barra de filtros + lista de templates com badges
 *   RIGHT — formulário de definição (com vínculo a tarefa para tipo TAREFA) + passos
 */
public class ChecklistController {

    private final SharedContext ctx;

    private final ObservableList<Protocol> protocolItems = FXCollections.observableArrayList();

    // ── Estado dos filtros ─────────────────────────────────────────────────
    private String categoryFilter = null;
    private String typeFilter     = null;
    private String statusFilter   = null;

    // ── KPI labels (referência para refresh) ──────────────────────────────
    private Label kpiTotal;
    private Label kpiActive;

    // ── Campos do formulário ───────────────────────────────────────────────
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

    /** Opção para o ComboBox de tarefas vinculadas. */
    private record TaskOption(Long id, String title) {
        static final TaskOption NONE = new TaskOption(null, "— nenhuma tarefa vinculada —");
        @Override public String toString() { return title; }
    }

    /** Opção para o ComboBox de status de execução. */
    private record StatusOption(String key, String label) {
        @Override public String toString() { return label; }
    }

    public ChecklistController(SharedContext ctx) { this.ctx = ctx; }

    // ══════════════════════════════════════════════════════════════════════
    // Construção da aba
    // ══════════════════════════════════════════════════════════════════════

    public Tab buildTab() {
        Tab tab = new Tab("Protocolos Operacionais");
        tab.setClosable(false);

        kpiTotal  = new Label("0");
        kpiActive = new Label("0");
        HBox kpiBar = new HBox(10,
            UIHelper.createMiniKpi("Protocolos cadastrados", kpiTotal,  "kpi-blue"),
            UIHelper.createMiniKpi("Execuções ativas",       kpiActive, "kpi-orange"));
        kpiBar.setPadding(new Insets(0, 0, 8, 0));

        // ── Barra de filtros ──────────────────────────────────────────────
        HBox filterBar = buildFilterBar();

        // ── Lista de protocolos ───────────────────────────────────────────
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

        Label hint = new Label("⇥  Duplo clique → abrir janela de execução");
        hint.getStyleClass().add("study-dates-label");

        Button newBtn    = new Button("+ Novo");    newBtn.getStyleClass().add("primary-button");
        Button removeBtn = new Button("✕ Remover"); removeBtn.getStyleClass().add("danger-button");
        newBtn.setOnAction(e -> clearForm());
        removeBtn.setOnAction(e -> removeSelectedProtocol());

        HBox leftBtns = new HBox(8, newBtn, removeBtn); leftBtns.setAlignment(Pos.CENTER_LEFT);

        VBox leftPanel = new VBox(8, filterBar, protocolListView, hint, leftBtns);
        leftPanel.setPadding(new Insets(12));
        leftPanel.getStyleClass().add("section-card");

        VBox rightPanel = buildForm();
        rightPanel.setMaxWidth(520);
        SplitPane.setResizableWithParent(rightPanel, Boolean.FALSE);

        SplitPane split = new SplitPane(leftPanel, rightPanel);
        split.setDividerPositions(0.55);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox content = new VBox(8, kpiBar, split);
        content.setPadding(new Insets(14));
        tab.setContent(content);

        refresh();
        return tab;
    }

    // ── Barra de filtros ───────────────────────────────────────────────────

    private HBox buildFilterBar() {
        // ── Categoria ─────────────────────────────────────────────────────
        ComboBox<String> catFilterCombo = new ComboBox<>();
        catFilterCombo.getStyleClass().add("input-control");
        catFilterCombo.getItems().add("Todas");
        ctx.checklistCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = catFilterCombo.getValue();
            catFilterCombo.getItems().setAll("Todas");
            catFilterCombo.getItems().addAll(ctx.checklistCatNames);
            catFilterCombo.setValue(cur != null ? cur : "Todas");
        });
        catFilterCombo.getItems().addAll(ctx.checklistCatNames);
        catFilterCombo.setValue("Todas");
        catFilterCombo.setOnAction(e -> {
            String v = catFilterCombo.getValue();
            categoryFilter = "Todas".equals(v) ? null : v;
            refresh();
        });

        // ── Tipo ──────────────────────────────────────────────────────────
        ComboBox<String> typeFilterCombo = new ComboBox<>();
        typeFilterCombo.getStyleClass().add("input-control");
        typeFilterCombo.getItems().add("Todos os tipos");
        for (ProtocolExecutionType t : ProtocolExecutionType.values())
            typeFilterCombo.getItems().add(t.name());
        typeFilterCombo.setValue("Todos os tipos");
        typeFilterCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String s) {
                if (s == null || "Todos os tipos".equals(s)) return "Todos os tipos";
                try { return ProtocolExecutionType.valueOf(s).icon() + " " + ProtocolExecutionType.valueOf(s).label(); }
                catch (Exception e) { return s; }
            }
            @Override public String fromString(String s) { return s; }
        });
        typeFilterCombo.setOnAction(e -> {
            String v = typeFilterCombo.getValue();
            typeFilter = "Todos os tipos".equals(v) ? null : v;
            refresh();
        });

        // ── Status de execução ─────────────────────────────────────────────
        List<StatusOption> statusOpts = List.of(
            new StatusOption(null,             "Todos os status"),
            new StatusOption("COM_ATIVA",      "● Com execução ativa"),
            new StatusOption("SEM_ATIVA",      "○ Sem execução ativa"),
            new StatusOption("VALIDADE_VENCIDA","⚠ Validade vencida"),
            new StatusOption("VENCE_7DIAS",    "⏰ Vence em 7 dias")
        );
        ComboBox<StatusOption> statusFilterCombo = new ComboBox<>();
        statusFilterCombo.getStyleClass().add("input-control");
        statusFilterCombo.getItems().addAll(statusOpts);
        statusFilterCombo.setValue(statusOpts.get(0));
        statusFilterCombo.setOnAction(e -> {
            StatusOption sel = statusFilterCombo.getValue();
            statusFilter = sel != null ? sel.key() : null;
            refresh();
        });

        // ── Botão limpar filtros ──────────────────────────────────────────
        Button clearFiltersBtn = new Button("✕ Limpar");
        clearFiltersBtn.getStyleClass().add("secondary-button");
        clearFiltersBtn.setOnAction(e -> {
            categoryFilter = null; typeFilter = null; statusFilter = null;
            catFilterCombo.setValue("Todas");
            typeFilterCombo.setValue("Todos os tipos");
            statusFilterCombo.setValue(statusOpts.get(0));
            refresh();
        });

        Label catLbl    = new Label("Cat:");    catLbl.getStyleClass().add("form-label");
        Label typeLbl   = new Label("Tipo:");   typeLbl.getStyleClass().add("form-label");
        Label statusLbl = new Label("Status:"); statusLbl.getStyleClass().add("form-label");

        HBox bar = new HBox(6,
            catLbl, catFilterCombo,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            typeLbl, typeFilterCombo,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            statusLbl, statusFilterCombo,
            UIHelper.createSpacer(),
            clearFiltersBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 4, 0));
        return bar;
    }

    // ── Célula customizada ─────────────────────────────────────────────────

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

                // Tarefa vinculada
                if (p.hasLinkedTask() && p.linkedTaskTitle() != null) {
                    taskLbl.setText("  📌 " + p.linkedTaskTitle());
                    taskLbl.setVisible(true); taskLbl.setManaged(true);
                } else {
                    taskLbl.setVisible(false); taskLbl.setManaged(false);
                }

                // Badge execução ativa
                int active = AppContextHolder.get().protocolRepository().countActiveExecutionsOf(p.id());
                execBadge.setVisible(active > 0); execBadge.setManaged(active > 0);
                execBadge.setText("  ● " + active + " ativa  ");

                // Badge de validade
                validityBadge.getStyleClass().removeAll("deadline-overdue", "deadline-warn", "deadline-ok", "study-dates-label");
                if (p.hasValidity()) {
                    java.time.LocalDate nextDue = AppContextHolder.get().protocolRepository()
                            .nextDueDate(p.id(), p.validityDays());
                    if (nextDue != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), nextDue);
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
                        ? "  —  " + p.description().substring(0, Math.min(50, p.description().length()))
                          + (p.description().length() > 50 ? "…" : "") : "";
                descLbl.setText(d);
            }
        };
    }

    // ── Formulário ─────────────────────────────────────────────────────────

    private VBox buildForm() {
        formModeLabel = new Label("Novo protocolo");
        formModeLabel.getStyleClass().add("section-title");

        nameField = new TextField();
        nameField.getStyleClass().add("input-control");
        nameField.setPromptText("Ex.: Ligar Motor X, Pré-voo, Calibração do espectrômetro...");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        typeCombo = new ComboBox<>();
        typeCombo.getStyleClass().add("input-control");
        typeCombo.getItems().addAll(ProtocolExecutionType.values());
        typeCombo.setValue(ProtocolExecutionType.RECORRENTE);
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        catFormCombo = new ComboBox<>();
        catFormCombo.getStyleClass().add("input-control");
        catFormCombo.setItems(ctx.checklistCatNames);
        catFormCombo.setMaxWidth(Double.MAX_VALUE);

        descArea = new TextArea();
        descArea.getStyleClass().add("input-control");
        descArea.setPromptText("Objetivo, contexto e quando utilizar este protocolo...");
        descArea.setPrefRowCount(2); descArea.setWrapText(true);

        validityDaysField = new TextField();
        validityDaysField.getStyleClass().add("input-control");
        validityDaysField.setPromptText("0 = sem validade");
        validityDaysField.setMinWidth(90);
        validityDaysField.setPrefWidth(130);
        validityDaysField.setMaxWidth(160);

        // ── ComboBox de tarefas vinculadas ─────────────────────────────────
        linkedTaskCombo = new ComboBox<>();
        linkedTaskCombo.getStyleClass().add("input-control");
        linkedTaskCombo.setMaxWidth(Double.MAX_VALUE);
        linkedTaskCombo.setPromptText("Selecionar tarefa da agenda...");

        Label taskLinkLbl = new Label("Tarefa vinculada:");
        taskLinkLbl.getStyleClass().add("form-label");
        linkedTaskRow = new HBox(8, taskLinkLbl, linkedTaskCombo);
        HBox.setHgrow(linkedTaskCombo, Priority.ALWAYS);
        linkedTaskRow.setAlignment(Pos.CENTER_LEFT);
        linkedTaskRow.setVisible(false); linkedTaskRow.setManaged(false);

        Label typeHint = new Label(ProtocolExecutionType.RECORRENTE.description());
        typeHint.getStyleClass().add("study-dates-label"); typeHint.setWrapText(true);

        typeCombo.setOnAction(e -> {
            ProtocolExecutionType t = typeCombo.getValue();
            if (t != null) typeHint.setText(t.description());
            boolean isTarefa = (t == ProtocolExecutionType.TAREFA);
            linkedTaskRow.setVisible(isTarefa); linkedTaskRow.setManaged(isTarefa);
            if (isTarefa) reloadTaskOptions();
        });

        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid"); grid.setHgap(10); grid.setVgap(8);

        Label nLbl = new Label("Nome *:");          nLbl.getStyleClass().add("form-label");
        Label tLbl = new Label("Tipo:");             tLbl.getStyleClass().add("form-label");
        Label cLbl = new Label("Categoria:");        cLbl.getStyleClass().add("form-label");
        Label dLbl = new Label("Descrição:");        dLbl.getStyleClass().add("form-label");
        Label vLbl = new Label("Validade (dias):"); vLbl.getStyleClass().add("form-label");
        Label vHnt = new Label("Nº de dias que o resultado fica válido após a última conclusão (ex: 180 = semestral). 0 = sem validade.");
        vHnt.getStyleClass().add("study-dates-label"); vHnt.setWrapText(true);

        grid.add(nLbl, 0, 0); grid.add(nameField,    1, 0, 3, 1); GridPane.setHgrow(nameField, Priority.ALWAYS);
        grid.add(tLbl, 0, 1); grid.add(typeCombo,    1, 1, 3, 1); GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        grid.add(cLbl, 0, 2); grid.add(catFormCombo, 1, 2, 3, 1); GridPane.setHgrow(catFormCombo, Priority.ALWAYS);
        grid.add(dLbl, 0, 3); grid.add(descArea,     1, 3, 3, 1);
        grid.add(vLbl, 0, 4); grid.add(new HBox(8, validityDaysField, vHnt), 1, 4, 3, 1);

        // ── Passos ──────────────────────────────────────────────────────
        Label stepsTitle = new Label("Passos do Protocolo");
        stepsTitle.getStyleClass().add("form-label");

        stepsEditorBox = new VBox(4);

        Button addStepBtn = new Button("+ Passo");
        addStepBtn.getStyleClass().add("secondary-button");
        addStepBtn.setOnAction(e -> addStepRow(null));

        ScrollPane stepsScroll = new ScrollPane(stepsEditorBox);
        stepsScroll.setFitToWidth(true); stepsScroll.setPrefHeight(180);
        stepsScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(stepsScroll, Priority.ALWAYS);

        // ── Botões ────────────────────────────────────────────────────────
        saveBtn      = new Button("Salvar protocolo"); saveBtn.getStyleClass().add("primary-button");
        cancelFormBtn = new Button("Limpar");          cancelFormBtn.getStyleClass().add("secondary-button");
        executeBtn   = new Button("▶  Executar");      executeBtn.getStyleClass().add("secondary-button");
        executeBtn.setVisible(false); executeBtn.setManaged(false);

        saveBtn.setOnAction(e -> saveProtocol());
        cancelFormBtn.setOnAction(e -> clearForm());
        executeBtn.setOnAction(e -> {
            Protocol sel = protocolListView.getSelectionModel().getSelectedItem();
            if (sel != null) openExecutionWindow(sel);
        });

        HBox btnRow = new HBox(8, saveBtn, cancelFormBtn, executeBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(8,
            formModeLabel, grid, linkedTaskRow, typeHint,
            new Separator(),
            new HBox(8, stepsTitle, UIHelper.createSpacer(), addStepBtn),
            stepsScroll, btnRow);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("section-card");
        return panel;
    }

    /** Popula o ComboBox de tarefas com as tarefas abertas. */
    private void reloadTaskOptions() {
        TaskOption current = linkedTaskCombo.getValue();
        linkedTaskCombo.getItems().clear();
        linkedTaskCombo.getItems().add(TaskOption.NONE);
        AppContextHolder.get().taskRepository().findOpenTasks().stream()
            .map(t -> new TaskOption(t.id(), t.title()))
            .forEach(linkedTaskCombo.getItems()::add);
        // Restaurar seleção anterior, se ainda existir
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

    // ── Linhas de passo ────────────────────────────────────────────────────

    private void addStepRow(ProtocolStep existing) {
        int order = stepRows.size() + 1;

        Label orderLbl = new Label(order + "."); orderLbl.getStyleClass().add("form-label"); orderLbl.setMinWidth(26);
        TextField stepField = new TextField(existing != null ? existing.stepText() : "");
        stepField.getStyleClass().add("input-control"); stepField.setPromptText("Descrição do passo...");
        HBox.setHgrow(stepField, Priority.ALWAYS);
        CheckBox criticalCb = new CheckBox("⚠");
        criticalCb.setSelected(existing != null && existing.critical());
        criticalCb.setTooltip(new Tooltip("Passo crítico/obrigatório"));

        Button upBtn   = new Button("▲"); upBtn.getStyleClass().add("icon-button");
        Button downBtn = new Button("▼"); downBtn.getStyleClass().add("icon-button");
        Button delBtn  = new Button("✕"); delBtn.getStyleClass().addAll("icon-button", "danger");
        upBtn.setTooltip(new Tooltip("Mover passo para cima"));
        downBtn.setTooltip(new Tooltip("Mover passo para baixo"));
        delBtn.setTooltip(new Tooltip("Remover este passo"));

        HBox row = new HBox(5, orderLbl, stepField, criticalCb, upBtn, downBtn, delBtn);
        row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(2, 4, 2, 4));

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

    // ══════════════════════════════════════════════════════════════════════
    // Lógica
    // ══════════════════════════════════════════════════════════════════════

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

        // Vínculo com tarefa
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

        formModeLabel.setText("Editando: \"" + p.name() + "\"");
        saveBtn.setText("Salvar alterações");
        cancelFormBtn.setText("✕ Cancelar edição");
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
        formModeLabel.setText("Novo protocolo");
        saveBtn.setText("Salvar protocolo");
        cancelFormBtn.setText("Limpar");
        cancelFormBtn.getStyleClass().removeAll("secondary-button", "danger-button");
        cancelFormBtn.getStyleClass().add("secondary-button");
        executeBtn.setVisible(false); executeBtn.setManaged(false);
        if (protocolListView != null) protocolListView.getSelectionModel().clearSelection();
    }

    private void saveProtocol() {
        String name = nameField.getText().trim();
        if (name.isBlank())    { ctx.setStatus("Nome do protocolo é obrigatório."); return; }
        if (stepRows.isEmpty()){ ctx.setStatus("Adicione ao menos um passo ao protocolo."); return; }

        ProtocolExecutionType type = typeCombo.getValue() != null ? typeCombo.getValue() : ProtocolExecutionType.RECORRENTE;
        String cat  = catFormCombo.getValue() != null ? catFormCombo.getValue() : "Geral";
        String desc = descArea.getText();
        int    validityDays = safeParseInt(validityDaysField.getText());

        // Tarefa vinculada (só para tipo TAREFA)
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
        String extra = active > 0 ? "\n⚠ " + active + " execução(ões) ativa(s) serão canceladas!" : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remover Protocolo Operacional");
        confirm.setHeaderText("Remover \"" + sel.name() + "\"?");
        confirm.setContentText(
            "Todos os passos e o histórico completo de execuções serão removidos permanentemente." + extra);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            AppContextHolder.get().protocolRepository().deleteProtocol(sel.id());
            clearForm(); refresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Protocolo \"" + sel.name() + "\" removido.");
        });
    }

    private void openExecutionWindow(Protocol p) {
        new ProtocolExecutionWindow(
            p,
            AppContextHolder.get().protocolRepository(),
            this::refresh
        ).show();
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
