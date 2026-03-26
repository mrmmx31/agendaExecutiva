package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.*;
import com.pessoal.agenda.ui.view.StudyDiaryWindow;
import com.pessoal.agenda.ui.view.StudyMonitorWindow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;

/**
 * Controller da aba Estudos e Atividades.
 *
 * Gerencia:
 *   – Lista de planos de estudo com progress bars e badges
 *   – Formulário de criação/edição de planos
 *   – Abertura do Diário Científico (StudyDiaryWindow)
 *   – KPIs: total de planos, em andamento, concluídos, horas/mês
 */
public class StudyController {

    private final SharedContext ctx;

    // ── Dados ──────────────────────────────────────────────────────────────
    private final ObservableList<StudyPlan> planItems = FXCollections.observableArrayList();
    private String categoryFilter = null;
    private String statusFilter   = null;   // null = todos
    private String periodFilter   = null;   // null = todos os prazos

    // ── KPI labels (locais desta aba) ──────────────────────────────────────
    private final Label kpiTotalPlans = new Label("0");
    private final Label kpiInProgress = new Label("0");
    private final Label kpiCompleted  = new Label("0");

    // ── Campos do formulário ───────────────────────────────────────────────
    private Long                      editingId  = null;
    private Label                     formModeLabel;
    private Button                    submitBtn;
    private Button                    cancelBtn;
    private ListView<StudyPlan>       planListView;
    private TextField                 titleField;
    private ComboBox<String>          typeCombo;
    private ComboBox<String>          catFormCombo;
    private TextArea                  descArea;
    private DatePicker                startPicker;
    private DatePicker                targetPicker;
    private ComboBox<StudyPlanStatus> statusCombo;
    private TextField                 totalPagesField;
    private Slider                    progressSlider;
    private Label                     progressSliderLabel;
    private VBox                      pagesRow;
    private HBox                      sliderRow;

    // ── Grade horária semanal ──────────────────────────────────────────────
    private final EnumMap<DayOfWeek, CheckBox>   scheduleChecks = new EnumMap<>(DayOfWeek.class);
    private final EnumMap<DayOfWeek, TextField>  scheduleHours  = new EnumMap<>(DayOfWeek.class);
    private Button                               monitorBtn;

    public StudyController(SharedContext ctx) {
        this.ctx = ctx;
    }

    // ── Construção da aba ──────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("Estudos e Atividades");
        tab.setClosable(false);

        // KPI bar
        HBox kpiBar = new HBox(10,
                UIHelper.createMiniKpi("Planos cadastrados",  kpiTotalPlans,           "kpi-blue"),
                UIHelper.createMiniKpi("Em andamento",         kpiInProgress,           "kpi-orange"),
                UIHelper.createMiniKpi("Concluídos",           kpiCompleted,            "kpi-green"),
                UIHelper.createMiniKpi("Horas estudadas/mês",  ctx.studyHoursValue,     "kpi-cyan"));
        kpiBar.setPadding(new Insets(0, 0, 8, 0));
        kpiBar.setAlignment(Pos.CENTER_LEFT);

        // ── Filtro de categoria ──────────────────────────────────────────
        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getStyleClass().add("input-control");
        filterCombo.getItems().add("Todas as categorias");
        ctx.studyCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = filterCombo.getValue();
            filterCombo.getItems().setAll("Todas as categorias");
            filterCombo.getItems().addAll(ctx.studyCatNames);
            filterCombo.setValue(cur != null ? cur : "Todas as categorias");
        });
        filterCombo.setValue("Todas as categorias");
        filterCombo.setOnAction(e -> {
            String v = filterCombo.getValue();
            categoryFilter = "Todas as categorias".equals(v) ? null : v;
            refresh();
        });

        // ── Filtro de status (toggles) ───────────────────────────────────
        record StatusOpt(String label, String enumName) {}
        java.util.List<StatusOpt> statusOpts = java.util.List.of(
            new StatusOpt("Todos",     null),
            new StatusOpt("Ativo",     "EM_ANDAMENTO"),
            new StatusOpt("Pendente",  "PLANEJADO"),
            new StatusOpt("Inativo",   "PAUSADO"),
            new StatusOpt("Concluído", "CONCLUIDO"),
            new StatusOpt("Cancelado", "ABANDONADO")
        );
        ToggleGroup statusGroup = new ToggleGroup();
        HBox statusToggles = new HBox(4);
        statusToggles.setAlignment(Pos.CENTER_LEFT);
        for (StatusOpt opt : statusOpts) {
            ToggleButton tb = new ToggleButton(opt.label());
            tb.getStyleClass().add("filter-toggle");
            tb.setToggleGroup(statusGroup);
            if (opt.enumName() == null) tb.setSelected(true);
            tb.selectedProperty().addListener((obs, old, sel) -> {
                if (sel) { statusFilter = opt.enumName(); refresh(); }
            });
            statusToggles.getChildren().add(tb);
        }

        // ── Filtro temporal / período (toggles) ─────────────────────────
        record PeriodOpt(String label, String code) {}
        java.util.List<PeriodOpt> periodOpts = java.util.List.of(
            new PeriodOpt("Todos os prazos", null),
            new PeriodOpt("Hoje",            "HOJE"),
            new PeriodOpt("Esta semana",     "SEMANA"),
            new PeriodOpt("Este mês",        "MES"),
            new PeriodOpt("Este ano",        "ANO")
        );
        ToggleGroup periodGroup = new ToggleGroup();
        HBox periodToggles = new HBox(4);
        periodToggles.setAlignment(Pos.CENTER_LEFT);
        for (PeriodOpt opt : periodOpts) {
            ToggleButton tb = new ToggleButton(opt.label());
            tb.getStyleClass().addAll("filter-toggle", "period-toggle");
            tb.setToggleGroup(periodGroup);
            if (opt.code() == null) tb.setSelected(true);
            tb.selectedProperty().addListener((obs, old, sel) -> {
                if (sel) { periodFilter = opt.code(); refresh(); }
            });
            periodToggles.getChildren().add(tb);
        }

        Label catFilterLbl    = new Label("Categoria:");
        catFilterLbl.getStyleClass().add("form-label");
        catFilterLbl.setMinWidth(Region.USE_PREF_SIZE);
        Label statusFilterLbl = new Label("Status:");
        statusFilterLbl.getStyleClass().add("form-label");
        statusFilterLbl.setMinWidth(Region.USE_PREF_SIZE);
        Label periodFilterLbl = new Label("Período:");
        periodFilterLbl.getStyleClass().add("form-label");
        periodFilterLbl.setMinWidth(Region.USE_PREF_SIZE);

        HBox catBar    = new HBox(8, catFilterLbl, filterCombo);    catBar.setAlignment(Pos.CENTER_LEFT);
        HBox statusBar = new HBox(8, statusFilterLbl, statusToggles); statusBar.setAlignment(Pos.CENTER_LEFT);
        HBox periodBar = new HBox(8, periodFilterLbl, periodToggles); periodBar.setAlignment(Pos.CENTER_LEFT);
        VBox filterBox = new VBox(6, catBar, statusBar, periodBar);
        filterBox.getStyleClass().add("filter-box");

        // lista de planos com células customizadas
        planListView = new ListView<>(planItems);
        planListView.getStyleClass().add("clean-list");
        planListView.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLbl    = new Label();
            private final Label typeBadge   = new Label();
            private final Label statusBadge = new Label();
            private final ProgressBar pb    = new ProgressBar(0);
            private final Label progLbl     = new Label();
            private final Label detailLbl   = new Label();
            private final Label deadlineLbl = new Label();
            private final VBox container;
            {
                titleLbl.getStyleClass().add("study-plan-title");
                typeBadge.getStyleClass().addAll("study-badge", "badge-type");
                statusBadge.getStyleClass().add("study-badge");
                pb.setPrefWidth(160); pb.setPrefHeight(8);
                pb.getStyleClass().add("study-progress-bar");
                progLbl.getStyleClass().add("study-progress-label");
                detailLbl.getStyleClass().add("study-plan-detail");
                Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                HBox topRow = new HBox(6, typeBadge, titleLbl, sp, statusBadge, deadlineLbl);
                topRow.setAlignment(Pos.CENTER_LEFT);
                HBox midRow = new HBox(8, pb, progLbl); midRow.setAlignment(Pos.CENTER_LEFT);
                container = new VBox(4, topRow, midRow, detailLbl);
                container.setPadding(new Insets(4, 0, 4, 0));
                setGraphic(container); setText(null);
            }
            @Override protected void updateItem(StudyPlan plan, boolean empty) {
                super.updateItem(plan, empty);
                if (empty || plan == null) { setGraphic(null); return; }
                setGraphic(container);
                titleLbl.setText(plan.title());
                typeBadge.setText("  " + plan.studyTypeName() + "  ");
                statusBadge.getStyleClass().removeIf(s -> s.startsWith("badge-status-"));
                statusBadge.getStyleClass().add("badge-status-" + plan.status().name().toLowerCase());
                statusBadge.setText("  " + plan.status().label() + "  ");
                double prog = plan.computedProgress();
                pb.setProgress(prog / 100.0);
                progLbl.setText(plan.progressDisplay());
                pb.getStyleClass().removeAll("progress-complete", "progress-high", "progress-low");
                if      (prog >= 100) pb.getStyleClass().add("progress-complete");
                else if (prog >= 60)  pb.getStyleClass().add("progress-high");
                else if (prog <  25)  pb.getStyleClass().add("progress-low");
                String cat  = plan.category() != null ? plan.category() : "Geral";
                String desc = plan.description() != null && !plan.description().isBlank()
                        ? "  —  " + plan.description().substring(0, Math.min(50, plan.description().length()))
                          + (plan.description().length() > 50 ? "…" : "") : "";
                detailLbl.setText(cat + desc);
                deadlineLbl.getStyleClass().clear();
                if (plan.targetDate() != null) {
                    long days = plan.daysUntilTarget();
                    if      (days <  0) { deadlineLbl.setText("⚠ " + (-days) + "d vencido"); deadlineLbl.getStyleClass().add("deadline-overdue"); }
                    else if (days <= 7) { deadlineLbl.setText("⏰ " + days + "d");            deadlineLbl.getStyleClass().add("deadline-warn");    }
                    else                { deadlineLbl.setText("📅 " + days + "d");            deadlineLbl.getStyleClass().add("deadline-ok");      }
                } else { deadlineLbl.setText(""); }
            }
        });
        VBox.setVgrow(planListView, Priority.ALWAYS);

        planListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) loadPlanIntoForm(sel); });
        planListView.setOnMouseClicked(e -> {
            // deseleciona ao clicar em área vazia
            javafx.scene.Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof ListCell)) node = node.getParent();
            if (node == null || ((ListCell<?>) node).isEmpty()) {
                planListView.getSelectionModel().clearSelection();
                clearForm();
                return;
            }
            if (e.getClickCount() == 2) {
                StudyPlan sel = planListView.getSelectionModel().getSelectedItem();
                if (sel != null) openDiary(sel);
            }
        });

        Label hint = new Label("⇥  Duplo clique → abrir Diário Científico");
        hint.getStyleClass().add("study-dates-label");

        Button newBtn    = new Button("+ Novo plano");    newBtn.getStyleClass().add("primary-button");
        Button removeBtn = new Button("✕ Remover");       removeBtn.getStyleClass().add("danger-button");
        Button diaryBtn  = new Button("📖 Abrir Diário"); diaryBtn.getStyleClass().add("secondary-button");

        newBtn.setOnAction(e -> clearForm());
        removeBtn.setOnAction(e -> removeSelectedPlan());
        diaryBtn.setOnAction(e -> {
            StudyPlan sel = planListView.getSelectionModel().getSelectedItem();
            if (sel != null) openDiary(sel);
            else ctx.setStatus("Selecione um plano para abrir o Diário Científico.");
        });

        HBox leftBtns = new HBox(8, newBtn, removeBtn, diaryBtn); leftBtns.setAlignment(Pos.CENTER_LEFT);

        VBox leftPanel = new VBox(8, filterBox, planListView, hint, leftBtns);
        leftPanel.setPadding(new Insets(12)); leftPanel.getStyleClass().add("section-card");

        VBox rightPanel = buildForm();
        // Sem limite fixo de largura → usuário pode arrastar o divisor livremente
        SplitPane split = new SplitPane(leftPanel, rightPanel);
        split.setDividerPositions(0.55); VBox.setVgrow(split, Priority.ALWAYS);

        VBox content = new VBox(8, kpiBar, split);
        content.setPadding(new Insets(14));
        tab.setContent(content);
        return tab;
    }

    // ── Formulário ─────────────────────────────────────────────────────────

    private VBox buildForm() {
        formModeLabel = new Label("Novo plano de estudo");
        formModeLabel.getStyleClass().add("section-title");

        titleField = new TextField();
        titleField.getStyleClass().add("input-control");
        titleField.setPromptText("Ex.: Fundamentos de ML, Química Orgânica vol.2, Artigo X...");
        HBox.setHgrow(titleField, Priority.ALWAYS);

        typeCombo = new ComboBox<>();
        typeCombo.getStyleClass().add("input-control");
        typeCombo.setItems(ctx.studyTypeCatNames);
        ctx.studyTypeCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = typeCombo.getValue();
            typeCombo.setItems(ctx.studyTypeCatNames);
            typeCombo.setValue(cur != null && ctx.studyTypeCatNames.contains(cur) ? cur : "Geral");
        });
        if (!ctx.studyTypeCatNames.isEmpty()) typeCombo.setValue(ctx.studyTypeCatNames.contains("Geral") ? "Geral" : ctx.studyTypeCatNames.get(0));
        typeCombo.setOnAction(e -> updateFormVisibility());

        catFormCombo = new ComboBox<>();
        catFormCombo.getStyleClass().add("input-control");
        catFormCombo.setItems(ctx.studyCatNames);
        ctx.studyCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> catFormCombo.setItems(ctx.studyCatNames));

        statusCombo = new ComboBox<>();
        statusCombo.getStyleClass().add("input-control");
        statusCombo.getItems().addAll(StudyPlanStatus.values());
        statusCombo.setValue(StudyPlanStatus.PLANEJADO);

        startPicker = new DatePicker(LocalDate.now()); startPicker.getStyleClass().add("input-control");
        targetPicker = new DatePicker(); targetPicker.getStyleClass().add("input-control");
        targetPicker.setPromptText("Data-alvo (prazo)");

        descArea = new TextArea();
        descArea.getStyleClass().add("input-control");
        descArea.setPromptText("Objetivo científico, escopo, metodologia e referências principais...");
        descArea.setPrefRowCount(4); descArea.setWrapText(true);

        totalPagesField = new TextField();
        totalPagesField.getStyleClass().add("input-control"); totalPagesField.setPrefWidth(130);
        totalPagesField.setPromptText("Nº total de páginas");
        pagesRow = new VBox(4, new Label("Total de páginas:"), totalPagesField);

        progressSlider = new Slider(0, 100, 0);
        progressSlider.setBlockIncrement(5); progressSlider.setPrefWidth(200);
        progressSliderLabel = new Label("0%");
        progressSlider.valueProperty().addListener(
                (obs, o, n) -> progressSliderLabel.setText(String.format("%.0f%%", n.doubleValue())));
        sliderRow = new HBox(8, progressSlider, progressSliderLabel); sliderRow.setAlignment(Pos.CENTER_LEFT);
        VBox progressVRow = new VBox(4, new Label("Progresso inicial:"), sliderRow);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid"); grid.setHgap(10); grid.setVgap(8);

        // ── Restrições de coluna: labels não encolhem, controles crescem ──
        ColumnConstraints ccLabel0 = new ColumnConstraints();
        ccLabel0.setMinWidth(Region.USE_PREF_SIZE);
        ccLabel0.setHgrow(Priority.NEVER);

        ColumnConstraints ccCtrl1 = new ColumnConstraints();
        ccCtrl1.setHgrow(Priority.ALWAYS);
        ccCtrl1.setFillWidth(true);

        ColumnConstraints ccLabel2 = new ColumnConstraints();
        ccLabel2.setMinWidth(Region.USE_PREF_SIZE);
        ccLabel2.setHgrow(Priority.NEVER);

        ColumnConstraints ccCtrl3 = new ColumnConstraints();
        ccCtrl3.setHgrow(Priority.ALWAYS);
        ccCtrl3.setFillWidth(true);

        grid.getColumnConstraints().addAll(ccLabel0, ccCtrl1, ccLabel2, ccCtrl3);

        Label tLbl = new Label("Título *:");
        tLbl.getStyleClass().add("form-label");
        tLbl.setMinWidth(Region.USE_PREF_SIZE);
        tLbl.setTooltip(new Tooltip("Nome do plano de estudo ou atividade"));

        grid.add(tLbl, 0, 0); grid.add(titleField, 1, 0, 3, 1);
        GridPane.setHgrow(titleField, Priority.ALWAYS);

        Label typLbl = new Label("Tipo:");
        typLbl.getStyleClass().add("form-label");
        typLbl.setMinWidth(Region.USE_PREF_SIZE);
        typLbl.setTooltip(new Tooltip("Tipo de atividade: Livro, Curso, Artigo, etc."));

        Label catLbl = new Label("Categoria:");
        catLbl.getStyleClass().add("form-label");
        catLbl.setMinWidth(Region.USE_PREF_SIZE);
        catLbl.setTooltip(new Tooltip("Categoria para organização e filtros"));

        grid.add(typLbl, 0, 1); grid.add(typeCombo,   1, 1);
        grid.add(catLbl, 2, 1); grid.add(catFormCombo,3, 1);
        GridPane.setHgrow(typeCombo,    Priority.ALWAYS);
        GridPane.setHgrow(catFormCombo, Priority.ALWAYS);

        Label stLbl = new Label("Status:");
        stLbl.getStyleClass().add("form-label");
        stLbl.setMinWidth(Region.USE_PREF_SIZE);
        stLbl.setTooltip(new Tooltip("Estado atual do plano de estudo"));

        Label sdLbl = new Label("Início:");
        sdLbl.getStyleClass().add("form-label");
        sdLbl.setMinWidth(Region.USE_PREF_SIZE);
        sdLbl.setTooltip(new Tooltip("Data de início do estudo"));

        Label tdLbl = new Label("Prazo-alvo:");
        tdLbl.getStyleClass().add("form-label");
        tdLbl.setMinWidth(Region.USE_PREF_SIZE);
        tdLbl.setTooltip(new Tooltip("Data limite para conclusão do plano"));

        grid.add(stLbl, 0, 2); grid.add(statusCombo,  1, 2);
        grid.add(sdLbl, 0, 3); grid.add(startPicker,  1, 3);
        grid.add(tdLbl, 2, 3); grid.add(targetPicker, 3, 3);
        GridPane.setHgrow(statusCombo,  Priority.ALWAYS);
        GridPane.setHgrow(startPicker,  Priority.ALWAYS);
        GridPane.setHgrow(targetPicker, Priority.ALWAYS);

        Label dLbl = new Label("Descrição:");
        dLbl.getStyleClass().add("form-label");
        dLbl.setMinWidth(Region.USE_PREF_SIZE);
        dLbl.setTooltip(new Tooltip("Objetivo científico, escopo, metodologia e referências principais"));

        grid.add(dLbl,     0, 4);
        grid.add(descArea, 1, 4, 3, 1);
        grid.add(pagesRow, 0, 5, 2, 1);
        grid.add(progressVRow, 2, 5, 2, 1);

        updateFormVisibility();

        submitBtn = new Button("Salvar plano"); submitBtn.getStyleClass().add("primary-button");
        submitBtn.setOnAction(e -> savePlan());

        cancelBtn = new Button("Limpar"); cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> clearForm());

        monitorBtn = new Button("📊 Monitor de Frequência");
        monitorBtn.getStyleClass().add("secondary-button");
        monitorBtn.setDisable(true);  // só ativo ao editar um plano existente
        monitorBtn.setOnAction(e -> openMonitor());

        HBox btnRow = new HBox(8, submitBtn, cancelBtn, monitorBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        // ── Grade horária semanal ──────────────────────────────────────────
        VBox scheduleSection = buildScheduleSection();

        Label diaryHint = new Label(
                "ℹ  Duplo clique em um plano → abre o Diário Científico completo com\n" +
                "   anotações, índice, progresso de páginas e histórico de sessões.");
        diaryHint.getStyleClass().add("study-dates-label"); diaryHint.setWrapText(true);

        // Active session indicator (shared via SharedContext)
        Label activeSession = ctx.activeStudySessionLabel;
        activeSession.getStyleClass().add("study-active-session");
        UIHelper.setConditionalVisible(activeSession, !activeSession.getText().isBlank());
        // observe changes to update visibility
        activeSession.textProperty().addListener((obs, old, v) -> UIHelper.setConditionalVisible(activeSession, v != null && !v.isBlank()));

        VBox panel = new VBox(10, formModeLabel, activeSession, grid, btnRow,
                new Separator(), scheduleSection,
                new Separator(), diaryHint);
        panel.setPadding(new Insets(14)); panel.getStyleClass().add("section-card");
        return panel;
    }

    // ── Grade horária semanal ──────────────────────────────────────────────

    private VBox buildScheduleSection() {
        Label sectionLbl = new Label("📅  Frequência Semanal");
        sectionLbl.getStyleClass().add("section-title");

        Label hint = new Label("Marque os dias em que estudará esta atividade e defina o mínimo de horas por dia.");
        hint.getStyleClass().add("study-dates-label");
        hint.setWrapText(true);

        // Grade: 7 colunas (dias da semana), 2 linhas (checkbox + campo horas)
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(4);
        grid.setAlignment(Pos.TOP_LEFT);

        int col = 0;
        for (DayOfWeek day : DayOfWeek.values()) {
            CheckBox cb = new CheckBox(StudyScheduleDay.shortLabel(day));
            cb.getStyleClass().add("form-label");

            TextField tf = new TextField("1.0");
            tf.getStyleClass().add("input-control");
            tf.setPrefWidth(62);
            tf.setPromptText("horas");
            tf.setDisable(true);
            tf.setTooltip(new Tooltip("Horas mínimas de estudo neste dia"));
            cb.selectedProperty().addListener((obs, o, sel) -> tf.setDisable(!sel));

            scheduleChecks.put(day, cb);
            scheduleHours.put(day, tf);

            grid.add(cb, col, 0);
            grid.add(tf, col, 1);
            col++;
        }

        return new VBox(8, sectionLbl, hint, grid);
    }

    // ── Lógica do controller ───────────────────────────────────────────────

    public void refresh() {
        planItems.setAll(AppContextHolder.get().studyPlanRepository()
                .findAll(categoryFilter, statusFilter, periodFilter));
        long tot  = planItems.size();
        long inp  = planItems.stream().filter(p -> p.status() == StudyPlanStatus.EM_ANDAMENTO).count();
        long done = planItems.stream().filter(p -> p.status() == StudyPlanStatus.CONCLUIDO).count();
        kpiTotalPlans.setText(String.valueOf(tot));
        kpiInProgress.setText(String.valueOf(inp));
        kpiCompleted.setText(String.valueOf(done));
    }

    private void openDiary(StudyPlan plan) {
        if (plan == null) return;
        new com.pessoal.agenda.ui.view.StudyDiaryWindow(
                plan,
                AppContextHolder.get().studyPlanRepository(),
                AppContextHolder.get().studyEntryRepository(),
                this::refresh,
                this.ctx
        ).show();
    }

    private void updateFormVisibility() {
        if (typeCombo == null) return;
        boolean isBook = StudyPlan.LIVRO.equalsIgnoreCase(typeCombo.getValue());
        pagesRow.setVisible(isBook);   pagesRow.setManaged(isBook);
        sliderRow.setVisible(!isBook); sliderRow.setManaged(!isBook);
    }

    private void loadPlanIntoForm(StudyPlan plan) {
        editingId = plan.id();
        titleField.setText(plan.title());
        typeCombo.setValue(plan.studyTypeName() != null ? plan.studyTypeName() : "Geral");
        catFormCombo.setValue(plan.category());
        descArea.setText(plan.description() != null ? plan.description() : "");
        startPicker.setValue(plan.startDate() != null ? plan.startDate() : LocalDate.now());
        targetPicker.setValue(plan.targetDate());
        statusCombo.setValue(plan.status() != null ? plan.status() : StudyPlanStatus.PLANEJADO);
        totalPagesField.setText(plan.totalPages() > 0 ? String.valueOf(plan.totalPages()) : "");
        progressSlider.setValue(plan.progressPercent());
        formModeLabel.setText("Editando: \"" + plan.title() + "\"");
        submitBtn.setText("Salvar alterações");
        cancelBtn.setText("✕ Cancelar edição");
        cancelBtn.getStyleClass().removeAll("secondary-button", "danger-button");
        cancelBtn.getStyleClass().add("danger-button");
        monitorBtn.setDisable(false);

        // Carrega grade horária
        var schedule = AppContextHolder.get().studyScheduleRepository().findByStudyId(plan.id());
        java.util.Map<DayOfWeek, Integer> minByDay = new java.util.HashMap<>();
        schedule.forEach(s -> minByDay.put(s.dayOfWeek(), s.minMinutes()));
        for (DayOfWeek day : DayOfWeek.values()) {
            CheckBox cb = scheduleChecks.get(day);
            TextField tf = scheduleHours.get(day);
            if (minByDay.containsKey(day)) {
                cb.setSelected(true);
                tf.setText(String.format("%.1f", minByDay.get(day) / 60.0));
            } else {
                cb.setSelected(false);
                tf.setText("1.0");
            }
        }
        updateFormVisibility();
    }

    private void clearForm() {
        editingId = null;
        titleField.clear();
        typeCombo.setValue(ctx.studyTypeCatNames.contains("Geral") ? "Geral"
                : (ctx.studyTypeCatNames.isEmpty() ? null : ctx.studyTypeCatNames.get(0)));
        catFormCombo.setValue(ctx.studyCatNames.isEmpty() ? null : ctx.studyCatNames.get(0));
        descArea.clear();
        startPicker.setValue(LocalDate.now()); targetPicker.setValue(null);
        statusCombo.setValue(StudyPlanStatus.PLANEJADO);
        totalPagesField.clear(); progressSlider.setValue(0);
        formModeLabel.setText("Novo plano de estudo");
        submitBtn.setText("Salvar plano");
        cancelBtn.setText("Limpar");
        cancelBtn.getStyleClass().removeAll("secondary-button", "danger-button");
        cancelBtn.getStyleClass().add("secondary-button");
        monitorBtn.setDisable(true);
        // Limpa grade horária
        for (DayOfWeek day : DayOfWeek.values()) {
            scheduleChecks.get(day).setSelected(false);
            scheduleHours.get(day).setText("1.0");
        }
        if (planListView != null) planListView.getSelectionModel().clearSelection();
        updateFormVisibility();
    }

    private void savePlan() {
        String title = titleField.getText().trim();
        if (title.isBlank()) { ctx.setStatus("Título do plano é obrigatório."); return; }
        String          type   = typeCombo.getValue()   != null ? typeCombo.getValue()   : "Geral";
        String          cat    = catFormCombo.getValue() != null ? catFormCombo.getValue(): "Geral";
        String          desc   = descArea.getText();
        LocalDate       start  = startPicker.getValue();
        LocalDate       target = targetPicker.getValue();
        StudyPlanStatus status = statusCombo.getValue() != null ? statusCombo.getValue() : StudyPlanStatus.PLANEJADO;
        int    totalPg = safeParseInt(totalPagesField.getText());
        double progress = StudyPlan.LIVRO.equalsIgnoreCase(type) ? 0 : progressSlider.getValue();
        var repo = AppContextHolder.get().studyPlanRepository();
        long savedId;
        if (editingId == null) {
            repo.save(title, type, cat, desc, start, target, status, totalPg);
            // Recupera o ID recém-criado
            var all = AppContextHolder.get().studyPlanRepository().findAll(null);
            savedId = all.stream().filter(p -> p.title().equals(title))
                        .mapToLong(StudyPlan::id).max().orElse(-1);
            ctx.setStatus("Plano de estudo criado: \"" + title + "\".");
        } else {
            repo.update(editingId, title, type, cat, desc, start, target, status, totalPg);
            if (!StudyPlan.LIVRO.equalsIgnoreCase(type) && progress > 0)
                repo.updateProgress(editingId, 0, progress, status);
            savedId = editingId;
            ctx.setStatus("Plano atualizado: \"" + title + "\".");
        }
        // Salva grade horária
        if (savedId > 0) saveSchedule(savedId);
        clearForm(); refresh(); ctx.triggerDashboardRefresh();
    }

    private void removeSelectedPlan() {
        StudyPlan sel = planListView.getSelectionModel().getSelectedItem();
        if (sel == null) { ctx.setStatus("Selecione um plano para remover."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remover plano de estudo");
        confirm.setHeaderText("Remover \"" + sel.title() + "\"?");
        confirm.setContentText(
                "Todas as entradas do Diário Científico associadas a este plano\n" +
                "serão removidas permanentemente. Ação irreversível.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            AppContextHolder.get().studyPlanRepository().deleteById(sel.id());
            clearForm(); refresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Plano \"" + sel.title() + "\" removido.");
        });
    }

    private void saveSchedule(long planId) {
        var schedRepo = AppContextHolder.get().studyScheduleRepository();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (scheduleChecks.get(day).isSelected()) {
                double hours = safeParseDouble(scheduleHours.get(day).getText());
                int minutes  = Math.max(15, (int) Math.round(hours * 60));
                schedRepo.setDay(planId, day, minutes);
            } else {
                schedRepo.removeDay(planId, day);
            }
        }
    }

    private void openMonitor() {
        StudyPlan sel = planListView.getSelectionModel().getSelectedItem();
        if (sel == null && editingId != null) {
            // usa o plano em edição
            AppContextHolder.get().studyPlanRepository().findById(editingId)
                    .ifPresent(p -> new StudyMonitorWindow(p, this::refresh).show());
        } else if (sel != null) {
            new StudyMonitorWindow(sel, this::refresh).show();
        }
    }

    private static double safeParseDouble(String s) {
        try { return Double.parseDouble(s == null ? "1.0" : s.trim()); }
        catch (NumberFormatException e) { return 1.0; }
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException ex) { return 0; }
    }
}

