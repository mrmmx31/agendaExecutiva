package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.tools.ICalendarExporter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Controller da aba Agenda e Prioridades.
 *
 * Gerencia:
 *   – Visualizações por Dia / Semana / Mês / Ano
 *   – Formulário de criação e edição de tarefas (com recorrência Palm-style)
 *   – Navegação temporal
 *   – Painel lateral de ações
 */
public class AgendaTabController {

    private enum AgendaView { DIA, SEMANA, MES, ANO }

    private final SharedContext   ctx;
    private final DatabaseService db;

    // ── Estado de navegação ────────────────────────────────────────────────
    private AgendaView currentView   = AgendaView.MES;
    private LocalDate  currentDate   = LocalDate.now();
    private String     categoryFilter = null;

    // ── Referências de UI (área de conteúdo) ──────────────────────────────
    private VBox  contentArea;
    private Label periodLabel;
    private ListView<DatabaseService.RowItem> currentMainListView;

    // ── Listas de dados ────────────────────────────────────────────────────
    private final ObservableList<DatabaseService.RowItem>               taskItems    = FXCollections.observableArrayList();
    private final ObservableList<DatabaseService.RowItem>               dayTaskItems = FXCollections.observableArrayList();
    private final List<ObservableList<DatabaseService.RowItem>> weekDayItems = List.of(
            FXCollections.observableArrayList(), FXCollections.observableArrayList(),
            FXCollections.observableArrayList(), FXCollections.observableArrayList(),
            FXCollections.observableArrayList(), FXCollections.observableArrayList(),
            FXCollections.observableArrayList());

    // ── Campos do formulário ───────────────────────────────────────────────
    private Long             editingId       = null;
    private Label            formModeLabel;
    private Button           submitBtn;
    private Button           cancelEditBtn;
    private TextField        titleField;
    private ComboBox<String> catCombo;
    private ComboBox<String> priorityCombo;
    private ComboBox<String> statusCombo;
    private DatePicker       startPicker;
    private TextField        startTimeField;
    private TextField        endTimeField;
    private ComboBox<String> scheduleCombo;
    private DatePicker       endDatePicker;
    private CheckBox[]       dayChecks;
    private TextArea         notesArea;
    private Label            endDateLabel;
    private Label            daysLabel;
    private HBox             daysBox;

    public AgendaTabController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
    }

    // ── API pública ────────────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("Agenda e Prioridades");
        tab.setClosable(false);

        // Listener para atualizar a lista principal quando o timer mudar de estado
        com.pessoal.agenda.service.TaskTimerService.get().addStateListener(this::refreshMainListView);

        // Adiciona CSS do timer inline
        var cssUrl = getClass().getResource("/com/pessoal/agenda/timer-inline.css");
        if (cssUrl != null) {
            // Aplica ao Scene se já existir, senão aplica ao contentArea depois
            if (tab.getContent() instanceof Region r && r.getScene() != null) {
                r.getScene().getStylesheets().add(cssUrl.toExternalForm());
            } else {
                // fallback: aplica ao contentArea quando disponível
                contentArea = new VBox();
                contentArea.getStylesheets().add(cssUrl.toExternalForm());
            }
        }

        // filtro de categoria
        ComboBox<String> catFilterCombo = new ComboBox<>();
        catFilterCombo.getStyleClass().add("input-control");
        catFilterCombo.getItems().add("Todas as categorias");
        ctx.taskCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = catFilterCombo.getValue();
            catFilterCombo.getItems().setAll("Todas as categorias");
            catFilterCombo.getItems().addAll(ctx.taskCatNames);
            catFilterCombo.setValue(cur != null ? cur : "Todas as categorias");
        });
        catFilterCombo.setValue("Todas as categorias");
        catFilterCombo.setOnAction(e -> {
            String v = catFilterCombo.getValue();
            categoryFilter = "Todas as categorias".equals(v) ? null : v;
            refreshCurrentView();
        });

        // botões de visualização
        ToggleGroup viewGroup = new ToggleGroup();
        ToggleButton diaBtn    = buildViewToggle("Dia",    AgendaView.DIA,    viewGroup);
        ToggleButton semanaBtn = buildViewToggle("Semana", AgendaView.SEMANA, viewGroup);
        ToggleButton mesBtn    = buildViewToggle("Mês",    AgendaView.MES,    viewGroup);
        ToggleButton anoBtn    = buildViewToggle("Ano",    AgendaView.ANO,    viewGroup);
        mesBtn.setSelected(true);

        HBox viewToggleBar = new HBox(2, diaBtn, semanaBtn, mesBtn, anoBtn);
        viewToggleBar.getStyleClass().add("view-toggle-bar");

        // navegação temporal
        Button prevBtn  = new Button("‹"); prevBtn.getStyleClass().add("nav-button");
        prevBtn.setOnAction(e -> navigateBack());
        Button nextBtn  = new Button("›"); nextBtn.getStyleClass().add("nav-button");
        nextBtn.setOnAction(e -> navigateForward());
        Button todayBtn = new Button("Hoje"); todayBtn.getStyleClass().add("secondary-button");
        todayBtn.setOnAction(e -> { currentDate = LocalDate.now(); refreshCurrentView(); });

        periodLabel = new Label();
        periodLabel.getStyleClass().add("nav-period-label");

        HBox navBar = new HBox(6, prevBtn, periodLabel, nextBtn, todayBtn);
        navBar.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(12, new Label("Categoria:"), catFilterCombo,
                new Separator(), viewToggleBar, spacer, navBar);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("agenda-top-bar");

        contentArea = new VBox();
        contentArea.getStyleClass().add("agenda-content-area");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        VBox mainPanel = new VBox(10, topBar, contentArea);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        mainPanel.setPadding(new Insets(14));
        HBox.setHgrow(mainPanel, Priority.ALWAYS);

        VBox sidePanel = buildSidePanel();
        sidePanel.setPrefWidth(370); sidePanel.setMinWidth(340);

        HBox content = new HBox(10, mainPanel, sidePanel);
        content.setPadding(new Insets(0, 14, 14, 0));
        tab.setContent(content);

        refreshCurrentView();
        return tab;
    }

    /** Atualiza a view atual da agenda (chamado pelo AgendaApp no refreshAllData). */
    public void refresh() {
        refreshCurrentView();
        ctx.alertItems.setAll(db.listDeadlineAlerts());
        ctx.upcomingItems.setAll(db.listUpcomingDeadlines(10));
    }

    public LocalDate getCurrentDate() { return currentDate; }

    // ── Painel lateral (formulário + ações) ────────────────────────────────

    private VBox buildSidePanel() {
        titleField = new TextField();
        titleField.getStyleClass().add("input-control");
        titleField.setPromptText("Título da tarefa");

        catCombo = new ComboBox<>();
        catCombo.getStyleClass().add("input-control");
        catCombo.setItems(ctx.taskCatNames);
        catCombo.setValue(ctx.taskCatNames.isEmpty() ? "Geral" : ctx.taskCatNames.get(0));

        priorityCombo = new ComboBox<>();
        priorityCombo.getStyleClass().add("input-control");
        for (TaskPriority p : TaskPriority.values()) priorityCombo.getItems().add(p.label());
        priorityCombo.setValue(TaskPriority.NORMAL.label());

        statusCombo = new ComboBox<>();
        statusCombo.getStyleClass().add("input-control");
        for (TaskStatus s : TaskStatus.values()) statusCombo.getItems().add(s.label());
        statusCombo.setValue(TaskStatus.PENDENTE.label());

        startPicker = new DatePicker(LocalDate.now());
        startPicker.getStyleClass().add("input-control");

        startTimeField = new TextField();
        startTimeField.getStyleClass().add("input-control");
        startTimeField.setPromptText("HH:MM"); startTimeField.setPrefWidth(68);

        endTimeField = new TextField();
        endTimeField.getStyleClass().add("input-control");
        endTimeField.setPromptText("HH:MM"); endTimeField.setPrefWidth(68);

        HBox timeBox = new HBox(4, startTimeField, new Label("→"), endTimeField);
        timeBox.setAlignment(Pos.CENTER_LEFT);

        scheduleCombo = new ComboBox<>();
        scheduleCombo.getStyleClass().add("input-control");
        scheduleCombo.getItems().addAll("Dia único", "Intervalo de datas", "Dias da semana");
        scheduleCombo.setValue("Dia único");

        endDateLabel = new Label("Término");
        endDateLabel.getStyleClass().add("form-label");
        endDatePicker = new DatePicker(LocalDate.now().plusDays(7));
        endDatePicker.getStyleClass().add("input-control");

        String[] wdNames = {"Dom","Seg","Ter","Qua","Qui","Sex","Sáb"};
        dayChecks = new CheckBox[7];
        daysBox   = new HBox(3); daysBox.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 7; i++) {
            dayChecks[i] = new CheckBox(wdNames[i]);
            dayChecks[i].getStyleClass().add("recurrence-day-check");
            if (i >= 1 && i <= 5) dayChecks[i].setSelected(true);
            daysBox.getChildren().add(dayChecks[i]);
        }
        daysLabel = new Label("Dias ativos");
        daysLabel.getStyleClass().add("form-label");

        UIHelper.setConditionalVisible(endDateLabel, false);
        UIHelper.setConditionalVisible(endDatePicker, false);
        UIHelper.setConditionalVisible(daysLabel, false);
        UIHelper.setConditionalVisible(daysBox, false);

        scheduleCombo.setOnAction(ev -> {
            String v = scheduleCombo.getValue();
            boolean range = "Intervalo de datas".equals(v), weekly = "Dias da semana".equals(v);
            UIHelper.setConditionalVisible(endDateLabel,  range || weekly);
            UIHelper.setConditionalVisible(endDatePicker, range || weekly);
            UIHelper.setConditionalVisible(daysLabel,     weekly);
            UIHelper.setConditionalVisible(daysBox,       weekly);
        });

        notesArea = new TextArea();
        notesArea.getStyleClass().add("input-control");
        notesArea.setPromptText("Contexto, experimento, hipótese ou observação");
        notesArea.setPrefRowCount(3);

        GridPane form = new GridPane();
        form.getStyleClass().add("form-grid"); form.setHgap(8); form.setVgap(6);
        javafx.scene.layout.ColumnConstraints col0 = new javafx.scene.layout.ColumnConstraints();
        col0.setMinWidth(80); col0.setPrefWidth(80); col0.setMaxWidth(80);
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS); col1.setFillWidth(true);
        form.getColumnConstraints().addAll(col0, col1);
        int row = 0;
        form.add(new Label("Título"),         0, row); form.add(titleField,    1, row++);
        form.add(new Label("Categoria"),      0, row); form.add(catCombo,      1, row++);
        form.add(new Label("Prioridade"),     0, row); form.add(priorityCombo, 1, row++);
        form.add(new Label("Status"),         0, row); form.add(statusCombo,   1, row++);
        form.add(new Label("Data início"),    0, row); form.add(startPicker,   1, row++);
        form.add(new Label("Hora ini→fim"),   0, row); form.add(timeBox,       1, row++);
        form.add(new Label("Recorrência"),    0, row); form.add(scheduleCombo, 1, row++);
        form.add(endDateLabel,                0, row); form.add(endDatePicker, 1, row++);
        form.add(daysLabel,                   0, row); form.add(daysBox,       1, row++);
        form.add(new Label("Notas"),          0, row); form.add(notesArea,     1, row);
        GridPane.setHgrow(titleField,    Priority.ALWAYS);
        GridPane.setHgrow(catCombo,      Priority.ALWAYS);
        GridPane.setHgrow(priorityCombo, Priority.ALWAYS);
        GridPane.setHgrow(statusCombo,   Priority.ALWAYS);
        GridPane.setHgrow(startPicker,   Priority.ALWAYS);
        GridPane.setHgrow(timeBox,       Priority.ALWAYS);
        GridPane.setHgrow(scheduleCombo, Priority.ALWAYS);
        GridPane.setHgrow(endDatePicker, Priority.ALWAYS);
        GridPane.setHgrow(daysBox,       Priority.ALWAYS);
        GridPane.setHgrow(notesArea,     Priority.ALWAYS);

        formModeLabel = new Label("Nova tarefa");
        formModeLabel.getStyleClass().add("section-title");

        submitBtn = new Button("+ Adicionar tarefa");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setOnAction(e -> submitForm());

        cancelEditBtn = new Button("Cancelar edição");
        cancelEditBtn.getStyleClass().add("secondary-button");
        cancelEditBtn.setMaxWidth(Double.MAX_VALUE);
        cancelEditBtn.setOnAction(e -> resetForm());
        UIHelper.setConditionalVisible(cancelEditBtn, false);

        VBox formSection = new VBox(8, formModeLabel, form, submitBtn, cancelEditBtn);
        formSection.getStyleClass().add("section-card");
        formSection.setPadding(new Insets(12));

        // ── botões de ação sobre a tarefa selecionada ─────────────────────
        Button editBtn = new Button("✎  Editar selecionada");
        editBtn.getStyleClass().add("secondary-button"); editBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setOnAction(e -> {
            DatabaseService.RowItem sel = currentMainListView != null
                    ? currentMainListView.getSelectionModel().getSelectedItem() : null;
            if (sel == null) { ctx.setStatus("Selecione uma tarefa para editar."); return; }
            AppContextHolder.get().taskService().findById(sel.id()).ifPresentOrElse(
                    this::loadTaskIntoForm, () -> ctx.setStatus("Tarefa não encontrada."));
        });

        Button doneBtn = new Button("✓  Concluir selecionada");
        doneBtn.getStyleClass().add("secondary-button"); doneBtn.setMaxWidth(Double.MAX_VALUE);
        doneBtn.setOnAction(e -> {
            DatabaseService.RowItem sel = currentMainListView != null
                    ? currentMainListView.getSelectionModel().getSelectedItem() : null;
            if (sel == null) { ctx.setStatus("Selecione uma tarefa."); return; }
            AppContextHolder.get().taskService().markDone(sel.id());
            refresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Tarefa concluída.");
        });

        Button deleteBtn = new Button("✕  Remover selecionada");
        deleteBtn.getStyleClass().add("danger-button"); deleteBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setOnAction(e -> {
            DatabaseService.RowItem sel = currentMainListView != null
                    ? currentMainListView.getSelectionModel().getSelectedItem() : null;
            if (sel == null) { ctx.setStatus("Selecione uma tarefa para remover."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar remoção");
            confirm.setHeaderText("Remover tarefa selecionada?");
            confirm.setContentText("Esta ação é permanente e não pode ser desfeita.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    AppContextHolder.get().taskService().deleteTask(sel.id());
                    if (editingId != null && editingId == sel.id()) resetForm();
                    refresh(); ctx.triggerDashboardRefresh();
                    ctx.setStatus("Tarefa removida.");
                }
            });
        });

        VBox actionsSection = new VBox(6, editBtn, doneBtn, deleteBtn);
        actionsSection.getStyleClass().add("section-card"); actionsSection.setPadding(new Insets(10));

        // history button: shows sessions for selected task or for current period
        Button historyBtn = new Button("Histórico de sessões");
        historyBtn.getStyleClass().add("secondary-button"); historyBtn.setMaxWidth(Double.MAX_VALUE);
        historyBtn.setOnAction(e -> {
            // if there's a selected task in the current main list, show sessions for that task
            DatabaseService.RowItem sel = currentMainListView != null
                    ? currentMainListView.getSelectionModel().getSelectedItem() : null;
                if (sel != null) {
                new com.pessoal.agenda.ui.view.SessionHistoryWindow(
                        AppContextHolder.get().taskSessionRepository(), sel.id()).show();
                return;
            }

            // otherwise open sessions for the current period according to currentView
            java.time.LocalDate from, to;
            switch (currentView) {
                case DIA -> { from = currentDate; to = currentDate; }
                case SEMANA -> { from = currentDate.with(java.time.DayOfWeek.MONDAY); to = from.plusDays(6); }
                case MES -> { java.time.YearMonth ym = java.time.YearMonth.from(currentDate); from = ym.atDay(1); to = ym.atEndOfMonth(); }
                default -> { from = java.time.LocalDate.of(currentDate.getYear(), 1, 1); to = java.time.LocalDate.of(currentDate.getYear(), 12, 31); }
            }
            new com.pessoal.agenda.ui.view.SessionHistoryWindow(AppContextHolder.get().taskSessionRepository(), null).show();
        });

        // append history button to actions
        actionsSection.getChildren().add(historyBtn);

        // ── Exportar para Google Calendar ─────────────────────────────────
        Button exportIcsBtn = new Button("📅  Exportar para Google Calendar");
        exportIcsBtn.getStyleClass().add("secondary-button"); exportIcsBtn.setMaxWidth(Double.MAX_VALUE);
        exportIcsBtn.setOnAction(e -> exportToICalendar());
        actionsSection.getChildren().add(exportIcsBtn);

        ListView<String> alertsList = new ListView<>(ctx.alertItems);
        alertsList.getStyleClass().add("clean-list");
        VBox.setVgrow(alertsList, Priority.ALWAYS);
        VBox alertsSection = UIHelper.createCardSection("Alertas e riscos", alertsList);
        VBox.setVgrow(alertsSection, Priority.ALWAYS);

        VBox side = new VBox(10, formSection, actionsSection, alertsSection);
        side.setPadding(new Insets(14, 0, 14, 0));
        return side;
    }

    // ── Formulário: submissão / carga / reset ──────────────────────────────

    private void submitForm() {
        if (titleField.getText().isBlank() || startPicker.getValue() == null) {
            ctx.setStatus("Informe título e data de início."); return;
        }
        String sv = scheduleCombo.getValue();
        ScheduleType st = switch (sv) {
            case "Intervalo de datas" -> ScheduleType.RANGE;
            case "Dias da semana"     -> ScheduleType.WEEKLY;
            default                   -> ScheduleType.SINGLE;
        };
        LocalDate endDate = (st == ScheduleType.SINGLE) ? null : endDatePicker.getValue();
        String recDays = null;
        if (st == ScheduleType.WEEKLY) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (dayChecks[i].isSelected()) { if (!sb.isEmpty()) sb.append(","); sb.append(i); }
            }
            recDays = sb.toString();
        }
        TaskPriority priority = resolvePriority(priorityCombo.getValue());
        TaskStatus   status   = resolveStatus(statusCombo.getValue());
        try {
            var svc = AppContextHolder.get().taskService();
            if (editingId == null) {
                svc.createTask(titleField.getText().trim(), notesArea.getText().trim(),
                        startPicker.getValue(), catCombo.getValue(),
                        st, endDate, recDays,
                        startTimeField.getText(), endTimeField.getText(), priority, status);
                ctx.setStatus("Tarefa registrada com sucesso.");
            } else {
                svc.updateTask(editingId,
                        titleField.getText().trim(), notesArea.getText().trim(),
                        startPicker.getValue(), catCombo.getValue(),
                        st, endDate, recDays,
                        startTimeField.getText(), endTimeField.getText(), priority, status);
                ctx.setStatus("Tarefa atualizada: " + titleField.getText().trim());
            }
            resetForm();
            refresh(); ctx.triggerDashboardRefresh();
        } catch (IllegalArgumentException ex) {
            ctx.setStatus("Erro: " + ex.getMessage());
        }
    }

    private void loadTaskIntoForm(Task t) {
        editingId = t.id();
        titleField.setText(t.title());
        notesArea.setText(t.notes() != null ? t.notes() : "");
        startPicker.setValue(t.dueDate());
        startTimeField.setText(t.startTime() != null ? t.startTime() : "");
        endTimeField.setText(t.endTime()   != null ? t.endTime()   : "");
        if (t.category() != null && ctx.taskCatNames.contains(t.category()))
            catCombo.setValue(t.category());
        priorityCombo.setValue(t.priority() != null ? t.priority().label() : TaskPriority.NORMAL.label());
        statusCombo.setValue(t.status() != null ? t.status().label() : TaskStatus.PENDENTE.label());
        ScheduleType sched = t.scheduleType() != null ? t.scheduleType() : ScheduleType.SINGLE;
        scheduleCombo.setValue(switch (sched) {
            case RANGE  -> "Intervalo de datas";
            case WEEKLY -> "Dias da semana";
            default     -> "Dia único";
        });
        if (t.endDate() != null) endDatePicker.setValue(t.endDate());
        if (t.recurrenceDays() != null && !t.recurrenceDays().isBlank()) {
            String[] active = t.recurrenceDays().split(",");
            for (int i = 0; i < 7; i++) {
                final String di = String.valueOf(i);
                dayChecks[i].setSelected(java.util.Arrays.stream(active).anyMatch(di::equals));
            }
        }
        boolean hasEnd = sched == ScheduleType.RANGE || sched == ScheduleType.WEEKLY;
        UIHelper.setConditionalVisible(endDateLabel,  hasEnd);
        UIHelper.setConditionalVisible(endDatePicker, hasEnd);
        UIHelper.setConditionalVisible(daysLabel,     sched == ScheduleType.WEEKLY);
        UIHelper.setConditionalVisible(daysBox,       sched == ScheduleType.WEEKLY);
        formModeLabel.setText("Editando: \"" + t.title() + "\"");
        submitBtn.setText("Salvar alterações");
        UIHelper.setConditionalVisible(cancelEditBtn, true);
        ctx.setStatus("Formulário preenchido — edite os campos e salve.");
    }

    private void resetForm() {
        editingId = null;
        titleField.clear(); notesArea.clear();
        startPicker.setValue(LocalDate.now());
        startTimeField.clear(); endTimeField.clear();
        catCombo.setValue(ctx.taskCatNames.isEmpty() ? "Geral" : ctx.taskCatNames.get(0));
        priorityCombo.setValue(TaskPriority.NORMAL.label());
        statusCombo.setValue(TaskStatus.PENDENTE.label());
        scheduleCombo.setValue("Dia único");
        UIHelper.setConditionalVisible(endDateLabel,  false);
        UIHelper.setConditionalVisible(endDatePicker, false);
        UIHelper.setConditionalVisible(daysLabel,     false);
        UIHelper.setConditionalVisible(daysBox,       false);
        for (int i = 0; i < 7; i++) dayChecks[i].setSelected(i >= 1 && i <= 5);
        formModeLabel.setText("Nova tarefa");
        submitBtn.setText("+ Adicionar tarefa");
        UIHelper.setConditionalVisible(cancelEditBtn, false);
    }

    // ── Construtores de view ───────────────────────────────────────────────

    private Node buildDayContent() {
        dayTaskItems.setAll(db.listTasksByDay(currentDate, categoryFilter));
        if (dayTaskItems.isEmpty()) {
            Label empty = new Label("Nenhuma tarefa para este dia.");
            empty.getStyleClass().add("empty-state-label");
            VBox box = new VBox(empty); box.setAlignment(Pos.CENTER); box.setPrefHeight(220);
            return box;
        }
        ListView<DatabaseService.RowItem> list = new ListView<>(dayTaskItems);
        list.getStyleClass().add("clean-list");
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        currentMainListView = list;
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DatabaseService.RowItem sel = list.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    AppContextHolder.get().taskService().findById(sel.id()).ifPresent(t ->
                        new com.pessoal.agenda.ui.view.TaskTimerWindow(t, AppContextHolder.get().taskSessionRepository()).show());
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox box = new VBox(list); VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private Node buildWeekContent() {
        LocalDate weekStart = currentDate.with(DayOfWeek.MONDAY);
        LocalDate today     = LocalDate.now();
        String[] dayNames   = {"Seg","Ter","Qua","Qui","Sex","Sáb","Dom"};
        HBox weekBox = new HBox(6); weekBox.setFillHeight(true);
        for (int i = 0; i < 7; i++) {
            LocalDate day     = weekStart.plusDays(i);
            boolean   isToday = day.equals(today);
            weekDayItems.get(i).setAll(db.listTasksByDay(day, categoryFilter));
            Label header = new Label(dayNames[i] + "\n" + day.getDayOfMonth() + "/" + day.getMonthValue());
            header.getStyleClass().add(isToday ? "day-column-header-today" : "day-column-header");
            header.setMaxWidth(Double.MAX_VALUE); header.setAlignment(Pos.CENTER);
            ListView<DatabaseService.RowItem> dayList = new ListView<>(weekDayItems.get(i));
            dayList.getStyleClass().add("clean-list"); VBox.setVgrow(dayList, Priority.ALWAYS);
            dayList.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2) {
                    DatabaseService.RowItem sel = dayList.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        AppContextHolder.get().taskService().findById(sel.id()).ifPresent(t ->
                            new com.pessoal.agenda.ui.view.TaskTimerWindow(t, AppContextHolder.get().taskSessionRepository()).show());
                    }
                }
            });
            dayList.getSelectionModel().selectedItemProperty().addListener(
                    (obs, o, n) -> { if (n != null) currentMainListView = dayList; });
            VBox col = new VBox(6, header, dayList);
            col.getStyleClass().add("day-column");
            if (isToday) col.getStyleClass().add("day-column-today");
            col.setPadding(new Insets(8)); col.setPrefWidth(160);
            HBox.setHgrow(col, Priority.ALWAYS);
            weekBox.getChildren().add(col);
        }
        ScrollPane scroll = new ScrollPane(weekBox);
        scroll.setFitToHeight(true); scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge"); VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private Node buildMonthContent() {
        taskItems.setAll(db.listTasksByMonthFiltered(YearMonth.from(currentDate), categoryFilter));
        ListView<DatabaseService.RowItem> list = new ListView<>(taskItems);
        list.getStyleClass().add("clean-list");
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        currentMainListView = list; VBox.setVgrow(list, Priority.ALWAYS);
        // Custom cell: mostra controles do timer se for a tarefa ativa
        list.setCellFactory(lv -> new ListCell<>() {
            private final HBox cellBox = new HBox(8);
            private final Label textLabel = new Label();
            private final Label timerLabel = new Label();
            private final Button playPauseBtn = new Button();
            private final Button stopBtn = new Button();
            private final Button resetBtn = new Button();
            private Long cellTaskId = null;
            private Runnable tickUnsubscriber = null;
            {
                playPauseBtn.setPrefWidth(32); playPauseBtn.setFocusTraversable(false);
                stopBtn.setPrefWidth(32); stopBtn.setFocusTraversable(false);
                resetBtn.setPrefWidth(32); resetBtn.setFocusTraversable(false);
                playPauseBtn.setText("▶");
                stopBtn.setText("■");
                resetBtn.setText("⟲");
                timerLabel.getStyleClass().add("timer-label-inline");
                cellBox.setAlignment(Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(DatabaseService.RowItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null); setText(null); cellTaskId = null;
                    if (tickUnsubscriber != null) { tickUnsubscriber.run(); tickUnsubscriber = null; }
                    return;
                }
                cellTaskId = item.id();
                var timerService = com.pessoal.agenda.service.TaskTimerService.get();
                if (timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(item.id())) {
                    // Timer ativo para esta tarefa: mostra controles
                    textLabel.setText(item.text());
                    // Atualiza label do timer
                    long s = timerService.getElapsedSeconds();
                    timerLabel.setText(formatTimer(s));
                    // Atualização em tempo real (múltiplos listeners)
                    if (tickUnsubscriber != null) { tickUnsubscriber.run(); tickUnsubscriber = null; }
                    java.util.function.Consumer<Long> tickListener = sec -> {
                        if (cellTaskId != null && timerService.getActiveTaskId() != null && timerService.getActiveTaskId().equals(cellTaskId)) {
                            javafx.application.Platform.runLater(() -> timerLabel.setText(formatTimer(sec)));
                        }
                    };
                    timerService.addTickListener(tickListener);
                    tickUnsubscriber = () -> timerService.removeTickListener(tickListener);
                    // Botões
                    playPauseBtn.setText(timerService.isRunning() ? "⏸" : "▶");
                    playPauseBtn.setOnAction(e -> {
                        if (timerService.isRunning()) timerService.pause(); else timerService.resume();
                        playPauseBtn.setText(timerService.isRunning() ? "⏸" : "▶");
                    });
                    stopBtn.setOnAction(e -> {
                        // Ao parar, exibe o mesmo diálogo de salvar sessão do TaskTimerWindow
                        var task = db.findTaskById(cellTaskId);
                        if (task != null) {
                            com.pessoal.agenda.ui.view.TaskTimerWindow.showSaveSessionDialog(
                                task,
                                db.getTaskSessionRepository(),
                                null,
                                () -> { list.refresh(); }
                            );
                        } else {
                            timerService.stop();
                            list.refresh();
                        }
                    });
                    resetBtn.setOnAction(e -> { timerService.reset(); });
                    cellBox.getChildren().setAll(textLabel, timerLabel, playPauseBtn, stopBtn, resetBtn);
                    setGraphic(cellBox); setText(null);
                } else {
                    // Não é a tarefa ativa: mostra só o texto
                    setGraphic(null); setText(item.text());
                    if (tickUnsubscriber != null) { tickUnsubscriber.run(); tickUnsubscriber = null; }
                }
            }
            private String formatTimer(long s) {
                long hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
                return String.format("%02d:%02d:%02d", hh, mm, ss);
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DatabaseService.RowItem sel = list.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    AppContextHolder.get().taskService().findById(sel.id()).ifPresent(task -> {
                        new com.pessoal.agenda.ui.view.TaskTimerWindow(
                            task,
                            AppContextHolder.get().taskSessionRepository(),
                            this::refreshMainListView // callback para refresh
                        ).show();
                    });
                }
            }
        });
        VBox box = new VBox(list); VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private Node buildYearContent() {
        java.util.List<DatabaseService.MonthSummary> summaries =
                db.getYearOverview(currentDate.getYear(), categoryFilter);
        FlowPane grid = new FlowPane();
        grid.getStyleClass().add("year-grid"); grid.setHgap(10); grid.setVgap(10);
        for (DatabaseService.MonthSummary s : summaries) {
            Label nameLabel  = new Label(s.monthName()); nameLabel.getStyleClass().add("year-month-name");
            Label countLabel = new Label(String.valueOf(s.total())); countLabel.getStyleClass().add("year-month-count");
            Label openLabel  = new Label(s.open() == 0 ? "Sem pendências" : s.open() + " abertas");
            openLabel.getStyleClass().add("year-month-open");
            if (s.open() > 0) openLabel.setStyle("-fx-text-fill: #dc2626;");
            VBox card = new VBox(4, nameLabel, countLabel, openLabel);
            card.getStyleClass().add("year-month-card");
            int m = s.month();
            card.setOnMouseClicked(e -> {
                currentDate = LocalDate.of(currentDate.getYear(), m, 1);
                currentView = AgendaView.MES;
                refreshCurrentView();
            });
            grid.getChildren().add(card);
        }
        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge"); VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    // ── Navegação e refresh ────────────────────────────────────────────────

    private ToggleButton buildViewToggle(String text, AgendaView view, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group); btn.getStyleClass().add("view-toggle-btn");
        btn.setOnAction(e -> { currentView = view; refreshCurrentView(); });
        return btn;
    }

    private void navigateBack() {
        currentDate = switch (currentView) {
            case DIA    -> currentDate.minusDays(1);
            case SEMANA -> currentDate.minusWeeks(1);
            case MES    -> currentDate.minusMonths(1);
            case ANO    -> currentDate.minusYears(1);
        };
        refreshCurrentView();
    }

    private void navigateForward() {
        currentDate = switch (currentView) {
            case DIA    -> currentDate.plusDays(1);
            case SEMANA -> currentDate.plusWeeks(1);
            case MES    -> currentDate.plusMonths(1);
            case ANO    -> currentDate.plusYears(1);
        };
        refreshCurrentView();
    }

    private void refreshCurrentView() {
        if (contentArea == null || periodLabel == null) return;
        updatePeriodLabel();
        Node viewNode = switch (currentView) {
            case DIA    -> buildDayContent();
            case SEMANA -> buildWeekContent();
            case MES    -> buildMonthContent();
            case ANO    -> buildYearContent();
        };
        VBox.setVgrow(viewNode, Priority.ALWAYS);
        contentArea.getChildren().setAll(viewNode);
    }

    private void updatePeriodLabel() {
        Locale ptBR = Locale.forLanguageTag("pt-BR");
        DateTimeFormatter dayFmt  = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ptBR);
        DateTimeFormatter shortFmt= DateTimeFormatter.ofPattern("d MMM", ptBR);
        DateTimeFormatter monFmt  = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR);
        String raw = switch (currentView) {
            case DIA    -> currentDate.format(dayFmt);
            case SEMANA -> {
                LocalDate start = currentDate.with(DayOfWeek.MONDAY);
                LocalDate end   = start.plusDays(6);
                yield "Semana de %s a %s de %d".formatted(
                        start.format(shortFmt), end.format(shortFmt), start.getYear());
            }
            case MES    -> YearMonth.from(currentDate).format(monFmt);
            case ANO    -> String.valueOf(currentDate.getYear());
        };
        periodLabel.setText(Character.toUpperCase(raw.charAt(0)) + raw.substring(1));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static TaskPriority resolvePriority(String label) {
        for (TaskPriority p : TaskPriority.values()) if (p.label().equals(label)) return p;
        return TaskPriority.NORMAL;
    }

    private static TaskStatus resolveStatus(String label) {
        for (TaskStatus s : TaskStatus.values()) if (s.label().equals(label)) return s;
        return TaskStatus.PENDENTE;
    }

    // Força refresh da lista principal (usado para atualizar células do timer)
    private void refreshMainListView() {
        if (currentMainListView != null) {
            javafx.application.Platform.runLater(() -> currentMainListView.refresh());
        }
    }

    // ── Exportação para Google Calendar (iCalendar .ics) ──────────────────────

    private void exportToICalendar() {
        List<Task> tasks;
        String periodDesc;
        Locale ptBR = Locale.forLanguageTag("pt-BR");
        var repo = AppContextHolder.get().taskRepository();

        switch (currentView) {
            case DIA -> {
                tasks = repo.findByDay(currentDate, categoryFilter);
                periodDesc = currentDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBR));
            }
            case SEMANA -> {
                LocalDate weekStart = currentDate.with(DayOfWeek.MONDAY);
                java.util.LinkedHashMap<Long, Task> dedup = new java.util.LinkedHashMap<>();
                for (int i = 0; i < 7; i++) {
                    for (Task t : repo.findByDay(weekStart.plusDays(i), categoryFilter)) {
                        dedup.putIfAbsent(t.id(), t);
                    }
                }
                tasks = new java.util.ArrayList<>(dedup.values());
                LocalDate weekEnd = weekStart.plusDays(6);
                periodDesc = "semana de " + weekStart.format(DateTimeFormatter.ofPattern("dd/MM", ptBR))
                           + " a " + weekEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBR));
            }
            case ANO -> {
                java.util.LinkedHashMap<Long, Task> dedup = new java.util.LinkedHashMap<>();
                for (int m = 1; m <= 12; m++) {
                    for (Task t : repo.findByMonth(YearMonth.of(currentDate.getYear(), m), categoryFilter)) {
                        dedup.putIfAbsent(t.id(), t);
                    }
                }
                tasks = new java.util.ArrayList<>(dedup.values());
                periodDesc = "ano " + currentDate.getYear();
            }
            default -> { // MES
                tasks = repo.findByMonth(YearMonth.from(currentDate), categoryFilter);
                periodDesc = YearMonth.from(currentDate)
                        .format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR));
            }
        }

        if (tasks.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Não há tarefas no período selecionado para exportar.",
                    ButtonType.OK).showAndWait();
            return;
        }

        // Diálogo de confirmação
        Dialog<ButtonType> optDialog = new Dialog<>();
        optDialog.setTitle("Exportar para Google Calendar");
        optDialog.setHeaderText("Exportar tarefas — " + periodDesc);
        optDialog.setContentText(
                "Serão exportadas " + tasks.size() + " tarefa(s) no formato iCalendar (.ics).\n\n"
                + "Após salvar o arquivo, importe-o no Google Calendar:\n"
                + "  1. Acesse calendar.google.com\n"
                + "  2. Configurações ⚙ → Importar e exportar → Importar\n"
                + "  3. Selecione o arquivo .ics gerado\n"
                + "  4. Escolha o calendário de destino e clique em Importar");
        optDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) optDialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Salvar arquivo .ics");

        optDialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            FileChooser fc = new FileChooser();
            fc.setTitle("Salvar arquivo iCalendar");
            fc.setInitialFileName("agenda-cientifica-" + currentDate.getYear() + ".ics");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Arquivos iCalendar (*.ics)", "*.ics"));

            Stage owner = (Stage) contentArea.getScene().getWindow();
            File dest = fc.showSaveDialog(owner);
            if (dest == null) return;

            try {
                ICalendarExporter.export(tasks, dest.toPath());
                ctx.setStatus("✅ Exportado: " + dest.getName() + " (" + tasks.size() + " tarefa(s))");

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Exportação concluída");
                success.setHeaderText("Arquivo iCalendar gerado com sucesso!");
                success.setContentText(
                        "Arquivo salvo em:\n" + dest.getAbsolutePath() + "\n\n"
                        + "Para importar no Google Calendar:\n"
                        + "  1. Acesse calendar.google.com\n"
                        + "  2. Clique em Configurações ⚙ (canto superior direito)\n"
                        + "  3. Va em \"Importar e exportar\"\n"
                        + "  4. Clique em \"Importar\" e selecione o arquivo\n"
                        + "  5. Escolha o calendario de destino e confirme\n\n"
                        + "Compativel tambem com Outlook, Apple Calendar\n"
                        + "e qualquer app que suporte iCalendar (RFC 5545).");
                success.showAndWait();
            } catch (IOException ex) {
                ctx.setStatus("Erro ao exportar: " + ex.getMessage());
                new Alert(Alert.AlertType.ERROR,
                        "Erro ao gerar o arquivo:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });
    }
}
