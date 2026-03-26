package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.SharedContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/**
 * Controller da aba Financeiro e Pendências.
 */
public class FinanceController {

    private final SharedContext   ctx;
    private final DatabaseService db;

    private final ObservableList<DatabaseService.RowItem> items = FXCollections.observableArrayList();

    public FinanceController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
    }

    public Tab buildTab() {
        Tab tab = new Tab("Financeiro e Pendências");
        tab.setClosable(false);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getStyleClass().add("input-control");
        typeCombo.getItems().addAll("orçamento", "pagamento", "lançamento");
        typeCombo.setValue("orçamento");

        TextField descField = new TextField();
        descField.getStyleClass().add("input-control"); descField.setPromptText("Descrição");

        TextField amountField = new TextField();
        amountField.getStyleClass().add("input-control"); amountField.setPromptText("Valor");

        DatePicker duePicker = new DatePicker(LocalDate.now());
        duePicker.getStyleClass().add("input-control");

        ListView<DatabaseService.RowItem> list = new ListView<>(items);
        list.getStyleClass().add("clean-list");
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        Button addBtn = new Button("Registrar");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> {
            if (descField.getText().isBlank() || amountField.getText().isBlank()) {
                ctx.setStatus("Descrição e valor são obrigatórios."); return;
            }
            double amount;
            try { amount = Double.parseDouble(amountField.getText().trim().replace(',', '.')); }
            catch (NumberFormatException ignored) { ctx.setStatus("Valor inválido."); return; }
            db.addFinanceEntry(typeCombo.getValue(), descField.getText().trim(),
                    amount, duePicker.getValue(), false);
            descField.clear(); amountField.clear();
            refresh(); ctx.triggerAlertRefresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Lançamento financeiro salvo.");
        });

        Button paidBtn = new Button("Marcar pagamento selecionado");
        paidBtn.getStyleClass().add("secondary-button");
        paidBtn.setOnAction(e -> {
            DatabaseService.RowItem sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione um item."); return; }
            db.markFinancePaid(sel.id());
            refresh(); ctx.triggerAlertRefresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Pagamento marcado como concluído.");
        });

        HBox controls = new HBox(8, new Label("Tipo"), typeCombo,
                new Label("Descrição"), descField, new Label("Valor"), amountField,
                new Label("Venc."), duePicker, addBtn, paidBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox content = UIHelper.createCardSection("Governança financeira pessoal",
                new VBox(10, controls, list));
        content.setPadding(new Insets(14));
        tab.setContent(content);
        return tab;
    }

    public void refresh() {
        items.setAll(db.listFinanceEntries());
    }
}

