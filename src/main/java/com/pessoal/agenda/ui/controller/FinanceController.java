package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.FinanceEntry;
import com.pessoal.agenda.repository.FinanceRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Controller da aba Financeiro e Pendências.
 *
 * Layout:
 *   TOP    — toolbar: filtros + botão "Novo Lançamento"
 *   KPI    — Total | A Pagar | Vencidos | Pagos | Valor Pendente
 *   CENTER — SplitPane(lista com células ricas | formulário + ações + pendências urgentes)
 */
public class FinanceController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat BRL =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    private static final String[] ENTRY_TYPES = {"pagamento", "orçamento", "lançamento"};
    private static final String TYPE_ALL      = "Todos os tipos";
    private static final String STATUS_ALL    = "Todos os status";
    private static final String STATUS_PAGAR  = "A pagar";
    private static final String STATUS_VENCIDO= "Vencido";
    private static final String STATUS_PAGO   = "Pago";

    private final SharedContext ctx;

    // ── KPI labels ─────────────────────────────────────────────────────────
    private Label kpiTotalLbl, kpiPagarLbl, kpiVencidoLbl, kpiPagoLbl, kpiValorLbl;

    // ── Filtros ─────────────────────────────────────────────────────────────
    private String typeFilter   = null;
    private String statusFilter = null;

    // ── Form ────────────────────────────────────────────────────────────────
    private Long             editingId;
    private Label            formModeLabel;
    private ComboBox<String> typeCombo;
    private TextField        descField, amountField;
    private DatePicker       duePicker;
    private TextArea         notesArea;
    private CheckBox         paidCheck;
    private Button           submitBtn, cancelEditBtn, deleteBtn, markPaidBtn;

    // ── Lista ────────────────────────────────────────────────────────────────
    private final ObservableList<FinanceEntry> displayItems = FXCollections.observableArrayList();
    private ListView<FinanceEntry> listView;

    // ── Painel urgente ───────────────────────────────────────────────────────
    private VBox urgentBox;

    // ─────────────────────────────────────────────────────────────────────────

    /** Construtor compatível com AgendaApp (usa AppContextHolder internamente). */
    public FinanceController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
    }

    private FinanceRepository repo() {
        return AppContextHolder.get().financeRepository();
    }

    // ── Tab principal ─────────────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("💰 Financeiro e Pendências");
        tab.setClosable(false);

        SplitPane center = buildMainContent();
        VBox.setVgrow(center, Priority.ALWAYS);

        VBox root = new VBox(0);
        root.getChildren().addAll(buildToolbar(), buildKpiBar(), center);
        VBox.setVgrow(root.getChildren().get(2), Priority.ALWAYS);

        tab.setContent(root);
        return tab;
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Button addBtn = new Button("＋ Novo Lançamento");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> resetForm());

        ComboBox<String> typeFilterCombo = new ComboBox<>();
        typeFilterCombo.getItems().addAll(TYPE_ALL, "pagamento", "orçamento", "lançamento");
        typeFilterCombo.setValue(TYPE_ALL);
        typeFilterCombo.setPrefWidth(155);
        typeFilterCombo.setPromptText("Tipo");
        typeFilterCombo.getStyleClass().add("input-control");
        typeFilterCombo.setOnAction(e -> {
            String v = typeFilterCombo.getValue();
            typeFilter = TYPE_ALL.equals(v) ? null : v;
            applyFilters();
        });

        ComboBox<String> statusFilterCombo = new ComboBox<>();
        statusFilterCombo.getItems().addAll(STATUS_ALL, STATUS_PAGAR, STATUS_VENCIDO, STATUS_PAGO);
        statusFilterCombo.setValue(STATUS_ALL);
        statusFilterCombo.setPrefWidth(155);
        statusFilterCombo.setPromptText("Status");
        statusFilterCombo.getStyleClass().add("input-control");
        statusFilterCombo.setOnAction(e -> {
            String v = statusFilterCombo.getValue();
            statusFilter = STATUS_ALL.equals(v) ? null : v;
            applyFilters();
        });

        Button clearBtn = new Button("✕ Limpar");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> {
            typeFilterCombo.setValue(TYPE_ALL);
            statusFilterCombo.setValue(STATUS_ALL);
            typeFilter = null; statusFilter = null;
            applyFilters();
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, addBtn, spacer,
                filterLabel("Tipo:"), typeFilterCombo,
                filterLabel("Status:"), statusFilterCombo,
                clearBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("agenda-top-bar");
        bar.setPadding(new Insets(8, 14, 8, 14));
        return bar;
    }

    private static Label filterLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: 600; -fx-text-fill: #1e3a5f; -fx-font-size: 12px;");
        l.setMinWidth(40);
        return l;
    }

    // ── KPI Bar ───────────────────────────────────────────────────────────────

    private HBox buildKpiBar() {
        kpiTotalLbl   = new Label("0"); kpiTotalLbl.getStyleClass().add("kpi-value");
        kpiPagarLbl   = new Label("0"); kpiPagarLbl.getStyleClass().add("kpi-value");
        kpiVencidoLbl = new Label("0"); kpiVencidoLbl.getStyleClass().add("kpi-value");
        kpiPagoLbl    = new Label("0"); kpiPagoLbl.getStyleClass().add("kpi-value");
        kpiValorLbl   = new Label("R$ 0,00"); kpiValorLbl.getStyleClass().add("kpi-value");

        HBox bar = new HBox(10,
                kpiCard("TOTAL",         kpiTotalLbl,   "kpi-blue"),
                kpiCard("A PAGAR",        kpiPagarLbl,   "kpi-orange"),
                kpiCard("VENCIDOS",       kpiVencidoLbl, "kpi-red"),
                kpiCard("PAGOS",          kpiPagoLbl,    "kpi-green"),
                kpiCard("VALOR PENDENTE", kpiValorLbl,   "kpi-purple")
        );
        bar.setPadding(new Insets(10, 14, 6, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static VBox kpiCard(String title, Label valueLbl, String colorClass) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("kpi-title");
        VBox card = new VBox(2, titleLbl, valueLbl);
        card.getStyleClass().addAll("kpi-mini", colorClass);
        return card;
    }

    // ── Main Content ──────────────────────────────────────────────────────────

    private SplitPane buildMainContent() {
        SplitPane sp = new SplitPane(buildListPanel(), buildRightScroll());
        sp.setDividerPositions(0.60);
        sp.setPadding(new Insets(8, 14, 14, 14));
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    // ── Painel esquerdo: lista ────────────────────────────────────────────────

    private VBox buildListPanel() {
        listView = new ListView<>(displayItems);
        listView.getStyleClass().add("clean-list");
        listView.setCellFactory(lv -> new FinanceEntryCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                FinanceEntry sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null) loadEntryIntoForm(sel);
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> updateActionBtns(sel));

        Label hint = new Label("Clique duplo para editar  ·  Selecione para ações rápidas");
        hint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #7a9bbf; -fx-padding: 4 0 0 6;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        VBox panel = new VBox(0, listView, hint);
        panel.getStyleClass().add("section-card");
        panel.setPadding(new Insets(0));
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    // ── Painel direito: formulário + ações + urgências ────────────────────────

    private ScrollPane buildRightScroll() {
        VBox right = new VBox(10, buildFormCard(), buildQuickActionsCard(), buildUrgentCard());
        right.setPadding(new Insets(0, 0, 0, 4));
        ScrollPane scroll = new ScrollPane(right);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    // ── Formulário ────────────────────────────────────────────────────────────

    private VBox buildFormCard() {
        formModeLabel = new Label("📝 Novo Lançamento");
        formModeLabel.getStyleClass().add("section-title");
        formModeLabel.setMaxWidth(Double.MAX_VALUE);

        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(ENTRY_TYPES);
        typeCombo.setValue("pagamento");
        typeCombo.getStyleClass().add("input-control");
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        descField = new TextField();
        descField.getStyleClass().add("input-control");
        descField.setPromptText("Ex: Conta de energia, Aluguel, Assinatura...");

        amountField = new TextField();
        amountField.getStyleClass().add("input-control");
        amountField.setPromptText("0,00");

        duePicker = new DatePicker(LocalDate.now());
        duePicker.getStyleClass().add("input-control");
        duePicker.setMaxWidth(Double.MAX_VALUE);

        paidCheck = new CheckBox("Já pago / concluído");
        paidCheck.getStyleClass().add("recurrence-day-check");

        notesArea = new TextArea();
        notesArea.getStyleClass().add("input-control");
        notesArea.setPromptText("Observações ou referências (opcional)");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        submitBtn = new Button("＋ Registrar");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setOnAction(e -> submitForm());

        cancelEditBtn = new Button("Cancelar");
        cancelEditBtn.getStyleClass().add("secondary-button");
        cancelEditBtn.setOnAction(e -> resetForm());
        UIHelper.setConditionalVisible(cancelEditBtn, false);

        deleteBtn = new Button("✕ Excluir");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> deleteSelected());
        UIHelper.setConditionalVisible(deleteBtn, false);

        HBox btnRow = new HBox(6, submitBtn, cancelEditBtn, deleteBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(submitBtn, Priority.ALWAYS);

        // Amount + date side by side
        VBox amountBox = new VBox(3, fieldLabel("Valor (R$) *"), amountField);
        VBox dueBox    = new VBox(3, fieldLabel("Vencimento"), duePicker);
        HBox.setHgrow(amountBox, Priority.ALWAYS);
        HBox.setHgrow(dueBox, Priority.ALWAYS);
        HBox amtDueRow = new HBox(8, amountBox, dueBox);

        VBox form = new VBox(8,
                formModeLabel,
                fieldRow("Tipo *",       typeCombo),
                fieldRow("Descrição *",  descField),
                amtDueRow,
                fieldRow("Observações",  notesArea),
                paidCheck,
                btnRow
        );
        form.getStyleClass().add("section-card");
        form.setPadding(new Insets(12));
        return form;
    }

    // ── Ações Rápidas ─────────────────────────────────────────────────────────

    private VBox buildQuickActionsCard() {
        Label title = new Label("⚡ Ações Rápidas");
        title.getStyleClass().add("section-title");
        title.setMaxWidth(Double.MAX_VALUE);

        markPaidBtn = new Button("✓  Marcar como Pago");
        markPaidBtn.getStyleClass().add("secondary-button");
        markPaidBtn.setMaxWidth(Double.MAX_VALUE);
        markPaidBtn.setDisable(true);
        markPaidBtn.setOnAction(e -> {
            FinanceEntry sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.paid()) {
                repo().markPaid(sel.id());
                refresh();
                ctx.triggerDashboardRefresh();
                ctx.triggerAlertRefresh();
                ctx.setStatus("Pagamento registrado: " + sel.description());
            }
        });

        Button editBtn = new Button("✎  Editar Selecionado");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setOnAction(e -> {
            FinanceEntry sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) loadEntryIntoForm(sel);
            else ctx.setStatus("Selecione um lançamento para editar.");
        });

        Button delBtn = new Button("✕  Excluir Selecionado");
        delBtn.getStyleClass().add("danger-button");
        delBtn.setMaxWidth(Double.MAX_VALUE);
        delBtn.setOnAction(e -> {
            FinanceEntry sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione um lançamento."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Excluir Lançamento");
            confirm.setHeaderText("Excluir \"" + sel.description() + "\"?");
            confirm.setContentText("Esta ação não pode ser desfeita.");
            confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                repo().deleteById(sel.id());
                if (editingId != null && editingId.equals(sel.id())) resetForm();
                refresh();
                ctx.triggerDashboardRefresh();
                ctx.triggerAlertRefresh();
                ctx.setStatus("Lançamento excluído.");
            });
        });

        VBox card = new VBox(6, title, markPaidBtn, editBtn, delBtn);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private void updateActionBtns(FinanceEntry sel) {
        if (markPaidBtn == null) return;
        boolean canPay = sel != null && !sel.paid();
        markPaidBtn.setDisable(!canPay);
        markPaidBtn.setText(canPay
                ? "✓  Marcar Pago — " + sel.description()
                : "✓  Marcar como Pago");
    }

    // ── Pendências Urgentes ───────────────────────────────────────────────────

    private VBox buildUrgentCard() {
        Label title = new Label("⚠ Pendências Urgentes (Vencidas)");
        title.getStyleClass().add("section-title");
        title.setMaxWidth(Double.MAX_VALUE);

        urgentBox = new VBox(6);
        VBox card = new VBox(8, title, urgentBox);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private void updateUrgentPanel(List<FinanceEntry> all) {
        if (urgentBox == null) return;
        urgentBox.getChildren().clear();

        List<FinanceEntry> overdue = all.stream()
                .filter(FinanceEntry::isOverdue)
                .sorted((a, b) -> a.dueDate().compareTo(b.dueDate()))
                .toList();

        if (overdue.isEmpty()) {
            Label ok = new Label("✅  Nenhuma pendência urgente no momento.");
            ok.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #1b5e20;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
            urgentBox.getChildren().add(ok);
            return;
        }

        for (FinanceEntry e : overdue) {
            long days   = ChronoUnit.DAYS.between(e.dueDate(), LocalDate.now());
            String dayStr = days == 1 ? "1 dia" : days + " dias";

            Label descLbl = new Label(e.description());
            descLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #b71c1c;");
            descLbl.setWrapText(false);
            HBox.setHgrow(descLbl, Priority.ALWAYS);

            Label amtLbl = new Label(formatBrl(e.amount()));
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #b71c1c;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            Label ageLbl = new Label("Venceu há " + dayStr
                    + "  ·  " + e.dueDate().format(DATE_FMT));
            ageLbl.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #c62828;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            Button payBtn = new Button("✓ Pagar");
            payBtn.getStyleClass().add("secondary-button");
            payBtn.setStyle(payBtn.getStyle() + " -fx-font-size: 10.5px; -fx-padding: 3 10 3 10;");
            long entryId = e.id();
            payBtn.setOnAction(ev -> {
                repo().markPaid(entryId);
                refresh();
                ctx.triggerDashboardRefresh();
                ctx.triggerAlertRefresh();
                ctx.setStatus("Pagamento registrado: " + e.description());
            });

            HBox topRow = new HBox(6, descLbl, amtLbl, payBtn);
            topRow.setAlignment(Pos.CENTER_LEFT);

            VBox entryBox = new VBox(2, topRow, ageLbl);
            entryBox.setPadding(new Insets(6, 8, 6, 8));
            entryBox.setStyle("-fx-background-color: #fff5f5; -fx-background-radius: 6;"
                    + " -fx-border-color: #ffcdd2; -fx-border-radius: 6;");
            urgentBox.getChildren().add(entryBox);
        }
    }

    // ── Custom Cell ───────────────────────────────────────────────────────────

    private class FinanceEntryCell extends ListCell<FinanceEntry> {
        @Override
        protected void updateItem(FinanceEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) { setGraphic(null); setText(null); return; }

            boolean overdue = entry.isOverdue();

            // Type badge
            Label typeBadge = new Label("  " + entry.entryType().toUpperCase() + "  ");
            typeBadge.setStyle("-fx-padding: 2 8 2 8; -fx-background-radius: 10;"
                    + " -fx-font-size: 9.5px; -fx-font-weight: 700;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                    + typeStyle(entry.entryType()));

            // Status badge
            String statusTxt = entry.paid() ? "PAGO" : (overdue ? "VENCIDO" : "A PAGAR");
            Label statusBadge = new Label("  " + statusTxt + "  ");
            statusBadge.setStyle("-fx-padding: 2 8 2 8; -fx-background-radius: 10;"
                    + " -fx-font-size: 9.5px; -fx-font-weight: 700;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                    + (entry.paid()  ? " -fx-background-color: #e8f5e9; -fx-text-fill: #1b5e20;"
                    : overdue        ? " -fx-background-color: #ffebee; -fx-text-fill: #b71c1c;"
                    :                  " -fx-background-color: #fff3e0; -fx-text-fill: #e65100;"));

            // Description
            Label descLbl = new Label(entry.description());
            descLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #0d1b2a;"
                    + (entry.paid() ? " -fx-opacity: 0.52; -fx-strikethrough: true;" : ""));
            descLbl.setWrapText(false);
            HBox.setHgrow(descLbl, Priority.ALWAYS);

            // Due date
            Label dateLbl = new Label(entry.dueDate() != null ? entry.dueDate().format(DATE_FMT) : "—");
            dateLbl.setStyle("-fx-font-size: 11px; -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                    + " -fx-text-fill: " + (overdue && !entry.paid() ? "#c62828;" : "#5a7a9e;"));

            // Amount
            Label amtLbl = new Label(formatBrl(entry.amount()));
            amtLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                    + " -fx-text-fill: "
                    + (entry.paid() ? "#1b5e20;" : overdue ? "#b71c1c;" : "#1e3a5f;"));

            HBox row = new HBox(8, typeBadge, descLbl, dateLbl, statusBadge, amtLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 8, 5, 8));
            setGraphic(row); setText(null);
        }

        private String typeStyle(String type) {
            if (type == null) return " -fx-background-color: #f5f5f5; -fx-text-fill: #757575;";
            return switch (type.toLowerCase()) {
                case "pagamento" -> " -fx-background-color: #fff3e0; -fx-text-fill: #e65100;";
                case "orçamento" -> " -fx-background-color: #e8eaf6; -fx-text-fill: #283593;";
                default          -> " -fx-background-color: #e8f5e9; -fx-text-fill: #1b5e20;";
            };
        }
    }

    // ── Lógica do Formulário ──────────────────────────────────────────────────

    private void submitForm() {
        if (descField.getText().isBlank()) {
            ctx.setStatus("Descrição é obrigatória."); return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            ctx.setStatus("Valor inválido — use números (ex: 150.00 ou 150,00)."); return;
        }

        String    type  = typeCombo.getValue();
        String    desc  = descField.getText().trim();
        LocalDate due   = duePicker.getValue();
        boolean   paid  = paidCheck.isSelected();

        if (editingId == null) {
            repo().save(type, desc, amount, due, paid);
            ctx.setStatus("Lançamento registrado: " + desc);
        } else {
            repo().update(editingId, type, desc, amount, due, paid);
            ctx.setStatus("Lançamento atualizado: " + desc);
        }
        resetForm();
        refresh();
        ctx.triggerDashboardRefresh();
        ctx.triggerAlertRefresh();
    }

    private void loadEntryIntoForm(FinanceEntry e) {
        editingId = e.id();
        typeCombo.setValue(e.entryType());
        descField.setText(e.description());
        amountField.setText(String.format(Locale.US, "%.2f", e.amount()));
        duePicker.setValue(e.dueDate() != null ? e.dueDate() : LocalDate.now());
        paidCheck.setSelected(e.paid());
        if (notesArea != null) notesArea.clear();

        formModeLabel.setText("✎ Editando: \"" + e.description() + "\"");
        submitBtn.setText("💾 Salvar alterações");
        UIHelper.setConditionalVisible(cancelEditBtn, true);
        UIHelper.setConditionalVisible(deleteBtn, true);
        ctx.setStatus("Editando lançamento — altere os campos e salve.");
    }

    private void resetForm() {
        editingId = null;
        if (typeCombo     != null) typeCombo.setValue("pagamento");
        if (descField     != null) descField.clear();
        if (amountField   != null) amountField.clear();
        if (duePicker     != null) duePicker.setValue(LocalDate.now());
        if (paidCheck     != null) paidCheck.setSelected(false);
        if (notesArea     != null) notesArea.clear();
        if (formModeLabel != null) formModeLabel.setText("📝 Novo Lançamento");
        if (submitBtn     != null) submitBtn.setText("＋ Registrar");
        UIHelper.setConditionalVisible(cancelEditBtn, false);
        UIHelper.setConditionalVisible(deleteBtn, false);
        if (listView != null) listView.getSelectionModel().clearSelection();
    }

    private void deleteSelected() {
        if (editingId == null) return;
        FinanceEntry toDelete = repo().findAll().stream()
                .filter(e -> e.id() == editingId).findFirst().orElse(null);
        String desc = toDelete != null ? toDelete.description() : "#" + editingId;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Excluir Lançamento");
        confirm.setHeaderText("Excluir \"" + desc + "\"?");
        confirm.setContentText("Esta ação não pode ser desfeita.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            repo().deleteById(editingId);
            resetForm();
            refresh();
            ctx.triggerDashboardRefresh();
            ctx.triggerAlertRefresh();
            ctx.setStatus("Lançamento excluído: " + desc);
        });
    }

    // ── Filtros e Refresh ─────────────────────────────────────────────────────

    private void applyFilters() {
        List<FinanceEntry> all = repo().findAll();

        List<FinanceEntry> filtered = all.stream().filter(e -> {
            if (typeFilter != null && !typeFilter.equalsIgnoreCase(e.entryType())) return false;
            if (statusFilter != null) {
                return switch (statusFilter) {
                    case STATUS_PAGO    ->  e.paid();
                    case STATUS_VENCIDO ->  e.isOverdue();
                    case STATUS_PAGAR   -> !e.paid() && !e.isOverdue();
                    default             -> true;
                };
            }
            return true;
        }).toList();

        displayItems.setAll(filtered);
        updateKpis(all);
        updateUrgentPanel(all);
    }

    private void updateKpis(List<FinanceEntry> all) {
        long total   = all.size();
        long pago    = all.stream().filter(FinanceEntry::paid).count();
        long vencido = all.stream().filter(FinanceEntry::isOverdue).count();
        long pagar   = all.stream().filter(e -> !e.paid() && !e.isOverdue()).count();
        double valor = all.stream().filter(e -> !e.paid()).mapToDouble(FinanceEntry::amount).sum();

        if (kpiTotalLbl   != null) kpiTotalLbl.setText(String.valueOf(total));
        if (kpiPagarLbl   != null) kpiPagarLbl.setText(String.valueOf(pagar));
        if (kpiVencidoLbl != null) kpiVencidoLbl.setText(String.valueOf(vencido));
        if (kpiPagoLbl    != null) kpiPagoLbl.setText(String.valueOf(pago));
        if (kpiValorLbl   != null) kpiValorLbl.setText(formatBrl(valor));
    }

    public void refresh() {
        applyFilters();
    }

    // ── Helpers visuais ───────────────────────────────────────────────────────

    private static VBox fieldRow(String labelText, javafx.scene.Node control) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-weight: 600; -fx-font-size: 11.5px; -fx-text-fill: #1e3a5f;");
        return new VBox(3, lbl, control);
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: 600; -fx-font-size: 11.5px; -fx-text-fill: #1e3a5f;");
        return l;
    }

    private static String formatBrl(double value) {
        return BRL.format(value);
    }
}
