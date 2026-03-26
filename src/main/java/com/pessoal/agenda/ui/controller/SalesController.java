package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.SharedContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/**
 * Controller da aba Vendas e Estoque.
 */
public class SalesController {

    private final SharedContext   ctx;
    private final DatabaseService db;

    private final ObservableList<DatabaseService.RowItem> salesItems     = FXCollections.observableArrayList();
    private final ObservableList<DatabaseService.RowItem> inventoryItems = FXCollections.observableArrayList();

    public SalesController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
    }

    public Tab buildTab() {
        Tab tab = new Tab("Vendas e Estoque");
        tab.setClosable(false);

        // ── Vendas ───────────────────────────────────────────────────────
        TextField saleProductField  = new TextField(); saleProductField.getStyleClass().add("input-control");  saleProductField.setPromptText("Produto");
        TextField saleQuantityField = new TextField(); saleQuantityField.getStyleClass().add("input-control"); saleQuantityField.setPromptText("Qtd");
        TextField salePriceField    = new TextField(); salePriceField.getStyleClass().add("input-control");    salePriceField.setPromptText("Valor unitário");
        DatePicker saleDatePicker   = new DatePicker(LocalDate.now()); saleDatePicker.getStyleClass().add("input-control");

        Button addSaleBtn = new Button("Lançar venda");
        addSaleBtn.getStyleClass().add("primary-button");
        addSaleBtn.setOnAction(e -> {
            if (saleProductField.getText().isBlank() || saleQuantityField.getText().isBlank() || salePriceField.getText().isBlank()) {
                ctx.setStatus("Preencha produto, quantidade e valor."); return;
            }
            int qty; double price;
            try { qty   = Integer.parseInt(saleQuantityField.getText().trim());
                  price = Double.parseDouble(salePriceField.getText().trim().replace(',', '.')); }
            catch (NumberFormatException ignored) { ctx.setStatus("Quantidade ou valor inválido."); return; }
            db.addSaleEntry(saleProductField.getText().trim(), qty, price, saleDatePicker.getValue());
            saleProductField.clear(); saleQuantityField.clear(); salePriceField.clear();
            refresh(); ctx.setStatus("Venda registrada.");
        });

        ListView<DatabaseService.RowItem> salesList = new ListView<>(salesItems);
        salesList.getStyleClass().add("clean-list");

        // ── Estoque ──────────────────────────────────────────────────────
        TextField invProductField  = new TextField(); invProductField.getStyleClass().add("input-control");  invProductField.setPromptText("Produto");
        TextField invQuantityField = new TextField(); invQuantityField.getStyleClass().add("input-control"); invQuantityField.setPromptText("Qtd atual");
        TextField invMinField      = new TextField(); invMinField.getStyleClass().add("input-control");      invMinField.setPromptText("Qtd mínima");

        Button addInventoryBtn = new Button("Cadastrar estoque");
        addInventoryBtn.getStyleClass().add("secondary-button");
        addInventoryBtn.setOnAction(e -> {
            if (invProductField.getText().isBlank() || invQuantityField.getText().isBlank() || invMinField.getText().isBlank()) {
                ctx.setStatus("Preencha produto e níveis de estoque."); return;
            }
            int qty, min;
            try { qty = Integer.parseInt(invQuantityField.getText().trim());
                  min = Integer.parseInt(invMinField.getText().trim()); }
            catch (NumberFormatException ignored) { ctx.setStatus("Valores de estoque inválidos."); return; }
            db.addInventoryItem(invProductField.getText().trim(), qty, min);
            invProductField.clear(); invQuantityField.clear(); invMinField.clear();
            refresh(); ctx.triggerDashboardRefresh(); ctx.setStatus("Estoque atualizado.");
        });

        ListView<DatabaseService.RowItem> inventoryList = new ListView<>(inventoryItems);
        inventoryList.getStyleClass().add("clean-list");

        HBox saleControls = new HBox(8, saleProductField, saleQuantityField, salePriceField, saleDatePicker, addSaleBtn);
        saleControls.setAlignment(Pos.CENTER_LEFT);
        HBox invControls  = new HBox(8, invProductField, invQuantityField, invMinField, addInventoryBtn);
        invControls.setAlignment(Pos.CENTER_LEFT);

        VBox left  = UIHelper.createCardSection("Operação de vendas",   new VBox(10, saleControls, salesList));
        VBox right = UIHelper.createCardSection("Gestão de estoque",    new VBox(10, invControls,  inventoryList));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14)); root.setLeft(left); root.setCenter(right);
        tab.setContent(root);
        return tab;
    }

    public void refresh() {
        salesItems.setAll(db.listSalesEntries());
        inventoryItems.setAll(db.listInventoryItems());
    }
}

