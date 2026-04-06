package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.InventoryItem;
import com.pessoal.agenda.model.SaleEntry;
import com.pessoal.agenda.repository.InventoryRepository;
import com.pessoal.agenda.repository.SalesRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Controller da aba Vendas e Estoque.
 *
 * Dois painéis via ToggleButton: Vendas (transações) e Catálogo (produtos/serviços).
 *
 * Tipos de item:
 *   - material  → produto físico com controle de estoque
 *   - serviço   → prestação/trabalho por fora; sem estoque
 */
public class SalesController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat BRL =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    private static final String TYPE_MATERIAL  = "material";
    private static final String TYPE_SERVICO   = "serviço";
    private static final String STATUS_RECEBIDO = "recebido";
    private static final String STATUS_PENDENTE = "pendente";

    private final SharedContext ctx;

    // ── View toggle refs (para programmatic switch) ───────────────────────────
    private ToggleButton viewVendasBtn;
    private VBox vendasSection, catalogoSection;

    // ── Vendas KPIs ───────────────────────────────────────────────────────────
    private Label kpiTotalVendasLbl, kpiReceitaLbl, kpiReceberLbl,
                  kpiMatVendasLbl, kpiSvcVendasLbl;

    // ── Vendas lista + form ───────────────────────────────────────────────────
    private final ObservableList<SaleEntry> salesDisplay = FXCollections.observableArrayList();
    private ListView<SaleEntry> salesListView;
    private Long             editingSaleId;
    private Label            saleFormLabel;
    private ComboBox<String> saleTypeCombo;
    private ComboBox<InventoryItem> catalogPickCombo;
    private TextField        saleProductField, saleQtyField, salePriceField, saleClientField;
    private DatePicker       saleDatePicker;
    private ComboBox<String> saleStatusCombo;
    private TextArea         saleNotesArea;
    private Button           saleSubmitBtn, saleCancelBtn, saleDeleteBtn, saleMarkReceivedBtn;
    private Label            saleTotalPreview;

    // ── Catálogo KPIs ─────────────────────────────────────────────────────────
    private Label kpiTotalItensLbl, kpiMatCatLbl, kpiSvcCatLbl,
                  kpiLowStockLbl, kpiStockValueLbl;

    // ── Catálogo lista + form ─────────────────────────────────────────────────
    private final ObservableList<InventoryItem> catalogDisplay = FXCollections.observableArrayList();
    private ListView<InventoryItem> catalogListView;
    private Long             editingItemId;
    private Label            catalogFormLabel;
    private ComboBox<String> catalogTypeCombo;
    private TextField        catalogNameField, catalogQtyField, catalogMinQtyField,
                             catalogPriceField, catalogCategoryField;
    private TextArea         catalogDescArea;
    private Button           catalogSubmitBtn, catalogCancelBtn, catalogDeleteBtn, catalogSellBtn;
    private VBox             catalogStockBox;
    private VBox             lowStockBox;
    private TextField        adjustQtyField;

    // ─────────────────────────────────────────────────────────────────────────

    public SalesController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
    }

    private SalesRepository   salesRepo()     { return AppContextHolder.get().salesRepository(); }
    private InventoryRepository inventoryRepo() { return AppContextHolder.get().inventoryRepository(); }

    // ── Tab principal ─────────────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("🛒 Vendas e Estoque");
        tab.setClosable(false);

        vendasSection   = buildVendasSection();
        catalogoSection = buildCatalogoSection();
        UIHelper.setConditionalVisible(catalogoSection, false);
        VBox.setVgrow(vendasSection,   Priority.ALWAYS);
        VBox.setVgrow(catalogoSection, Priority.ALWAYS);

        VBox root = new VBox(0, buildTopToggleBar(), vendasSection, catalogoSection);
        VBox.setVgrow(vendasSection, Priority.ALWAYS);
        tab.setContent(root);
        return tab;
    }

    // ── Toggle bar ────────────────────────────────────────────────────────────

    private HBox buildTopToggleBar() {
        ToggleGroup tg = new ToggleGroup();
        viewVendasBtn              = new ToggleButton("📦  Vendas");
        ToggleButton catalogoBtn   = new ToggleButton("🏪  Catálogo de Itens");

        viewVendasBtn.setToggleGroup(tg);
        viewVendasBtn.getStyleClass().add("view-toggle-btn");
        viewVendasBtn.setSelected(true);

        catalogoBtn.setToggleGroup(tg);
        catalogoBtn.getStyleClass().add("view-toggle-btn");

        viewVendasBtn.setOnAction(e -> {
            UIHelper.setConditionalVisible(vendasSection,   true);
            UIHelper.setConditionalVisible(catalogoSection, false);
            VBox.setVgrow(vendasSection, Priority.ALWAYS);
        });
        catalogoBtn.setOnAction(e -> {
            UIHelper.setConditionalVisible(vendasSection,   false);
            UIHelper.setConditionalVisible(catalogoSection, true);
            VBox.setVgrow(catalogoSection, Priority.ALWAYS);
        });

        HBox toggleGroup = new HBox(2, viewVendasBtn, catalogoBtn);
        toggleGroup.getStyleClass().add("view-toggle-bar");

        HBox bar = new HBox(10, new Label("Vista:"), toggleGroup);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("agenda-top-bar");
        bar.setPadding(new Insets(8, 14, 8, 14));
        return bar;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEÇÃO VENDAS
    // ═══════════════════════════════════════════════════════════════════════════

    private VBox buildVendasSection() {
        SplitPane sp = new SplitPane(buildSalesList(), buildSalesRightScroll());
        sp.setDividerPositions(0.60);
        sp.setPadding(new Insets(8, 14, 14, 14));
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox section = new VBox(0, buildVendasKpiBar(), sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return section;
    }

    private HBox buildVendasKpiBar() {
        kpiTotalVendasLbl = kpiLbl("0");
        kpiReceitaLbl     = kpiLbl("R$ 0,00");
        kpiReceberLbl     = kpiLbl("R$ 0,00");
        kpiMatVendasLbl   = kpiLbl("0");
        kpiSvcVendasLbl   = kpiLbl("0");

        HBox bar = new HBox(10,
                kpiCard("TOTAL VENDAS",  kpiTotalVendasLbl, "kpi-blue"),
                kpiCard("RECEITA",        kpiReceitaLbl,     "kpi-green"),
                kpiCard("A RECEBER",      kpiReceberLbl,     "kpi-orange"),
                kpiCard("MATERIAIS",      kpiMatVendasLbl,   "kpi-indigo"),
                kpiCard("SERVIÇOS",       kpiSvcVendasLbl,   "kpi-cyan"));
        bar.setPadding(new Insets(10, 14, 6, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ── Lista de vendas ───────────────────────────────────────────────────────

    private VBox buildSalesList() {
        salesListView = new ListView<>(salesDisplay);
        salesListView.getStyleClass().add("clean-list");
        salesListView.setCellFactory(lv -> new SaleEntryCell());
        VBox.setVgrow(salesListView, Priority.ALWAYS);

        salesListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                SaleEntry sel = salesListView.getSelectionModel().getSelectedItem();
                if (sel != null) loadSaleIntoForm(sel);
            }
        });
        salesListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> updateSaleActionBtns(sel));

        Label hint = new Label("Clique duplo para editar  ·  Selecione para ações rápidas");
        hint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #7a9bbf; -fx-padding: 4 0 0 6;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        VBox panel = new VBox(0, salesListView, hint);
        panel.getStyleClass().add("section-card");
        panel.setPadding(new Insets(0));
        VBox.setVgrow(salesListView, Priority.ALWAYS);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private ScrollPane buildSalesRightScroll() {
        VBox right = new VBox(10, buildSalesFormCard(), buildSalesActionsCard());
        right.setPadding(new Insets(0, 0, 0, 4));
        ScrollPane scroll = new ScrollPane(right);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    // ── Formulário de venda ───────────────────────────────────────────────────

    private VBox buildSalesFormCard() {
        saleFormLabel = new Label("📦 Nova Venda");
        saleFormLabel.getStyleClass().add("section-title");
        saleFormLabel.setMaxWidth(Double.MAX_VALUE);

        // Tipo
        saleTypeCombo = new ComboBox<>();
        saleTypeCombo.getItems().addAll(TYPE_MATERIAL, TYPE_SERVICO);
        saleTypeCombo.setValue(TYPE_MATERIAL);
        saleTypeCombo.getStyleClass().add("input-control");
        saleTypeCombo.setMaxWidth(Double.MAX_VALUE);
        saleTypeCombo.setOnAction(e -> onSaleTypeChanged());

        // Pré-preenchimento a partir do catálogo
        catalogPickCombo = new ComboBox<>();
        catalogPickCombo.setPromptText("Selecionar do catálogo (pré-preenche os campos)");
        catalogPickCombo.setMaxWidth(Double.MAX_VALUE);
        catalogPickCombo.getStyleClass().add("input-control");
        catalogPickCombo.setConverter(new StringConverter<>() {
            @Override public String toString(InventoryItem i) {
                if (i == null) return "";
                return i.productName() + (i.unitPrice() > 0 ? "  —  " + formatBrl(i.unitPrice()) : "");
            }
            @Override public InventoryItem fromString(String s) { return null; }
        });
        catalogPickCombo.setOnAction(e -> {
            InventoryItem sel = catalogPickCombo.getValue();
            if (sel == null) return;
            saleProductField.setText(sel.productName());
            saleTypeCombo.setValue(sel.itemType() != null ? sel.itemType() : TYPE_MATERIAL);
            if (sel.unitPrice() > 0)
                salePriceField.setText(String.format(Locale.US, "%.2f", sel.unitPrice()));
            onSaleTypeChanged();
            updateSaleTotalPreview();
        });

        // Produto
        saleProductField = new TextField();
        saleProductField.getStyleClass().add("input-control");
        saleProductField.setPromptText("Nome do produto ou serviço");

        // Qtd + Preço lado a lado
        saleQtyField = new TextField("1");
        saleQtyField.getStyleClass().add("input-control");
        saleQtyField.setPromptText("Qtd");
        saleQtyField.textProperty().addListener((o, v, n) -> updateSaleTotalPreview());

        salePriceField = new TextField();
        salePriceField.getStyleClass().add("input-control");
        salePriceField.setPromptText("Valor unitário");
        salePriceField.textProperty().addListener((o, v, n) -> updateSaleTotalPreview());

        VBox qtyBox   = new VBox(3, fieldLabel("Quantidade"), saleQtyField);
        VBox priceBox = new VBox(3, fieldLabel("Valor unitário (R$) *"), salePriceField);
        HBox.setHgrow(qtyBox, Priority.ALWAYS); HBox.setHgrow(priceBox, Priority.ALWAYS);
        HBox qtyPriceRow = new HBox(8, qtyBox, priceBox);

        saleTotalPreview = new Label("Total: —");
        saleTotalPreview.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #1565c0;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        // Cliente
        saleClientField = new TextField();
        saleClientField.getStyleClass().add("input-control");
        saleClientField.setPromptText("Nome do cliente / contratante (opcional)");

        // Data + Status lado a lado
        saleDatePicker = new DatePicker(LocalDate.now());
        saleDatePicker.getStyleClass().add("input-control");
        saleDatePicker.setMaxWidth(Double.MAX_VALUE);

        saleStatusCombo = new ComboBox<>();
        saleStatusCombo.getItems().addAll(STATUS_RECEBIDO, STATUS_PENDENTE);
        saleStatusCombo.setValue(STATUS_RECEBIDO);
        saleStatusCombo.getStyleClass().add("input-control");
        saleStatusCombo.setMaxWidth(Double.MAX_VALUE);

        VBox dateBox   = new VBox(3, fieldLabel("Data"), saleDatePicker);
        VBox statusBox = new VBox(3, fieldLabel("Status"), saleStatusCombo);
        HBox.setHgrow(dateBox, Priority.ALWAYS); HBox.setHgrow(statusBox, Priority.ALWAYS);
        HBox dateStatusRow = new HBox(8, dateBox, statusBox);

        // Notas
        saleNotesArea = new TextArea();
        saleNotesArea.getStyleClass().add("input-control");
        saleNotesArea.setPromptText("Escopo do trabalho, número do pedido, referências...");
        saleNotesArea.setPrefRowCount(2);
        saleNotesArea.setWrapText(true);

        // Botões
        saleSubmitBtn = new Button("＋ Registrar Venda");
        saleSubmitBtn.getStyleClass().add("primary-button");
        saleSubmitBtn.setMaxWidth(Double.MAX_VALUE);
        saleSubmitBtn.setOnAction(e -> submitSale());

        saleCancelBtn = new Button("Cancelar");
        saleCancelBtn.getStyleClass().add("secondary-button");
        saleCancelBtn.setOnAction(e -> resetSaleForm());
        UIHelper.setConditionalVisible(saleCancelBtn, false);

        saleDeleteBtn = new Button("✕ Excluir");
        saleDeleteBtn.getStyleClass().add("danger-button");
        saleDeleteBtn.setOnAction(e -> deleteSaleFromForm());
        UIHelper.setConditionalVisible(saleDeleteBtn, false);

        HBox btnRow = new HBox(6, saleSubmitBtn, saleCancelBtn, saleDeleteBtn);
        HBox.setHgrow(saleSubmitBtn, Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(8,
                saleFormLabel,
                fieldRow("Tipo *", saleTypeCombo),
                fieldRow("Pré-preencher do Catálogo", catalogPickCombo),
                fieldRow("Produto / Serviço *", saleProductField),
                qtyPriceRow,
                saleTotalPreview,
                fieldRow("Cliente / Contratante", saleClientField),
                dateStatusRow,
                fieldRow("Observações", saleNotesArea),
                btnRow);
        form.getStyleClass().add("section-card");
        form.setPadding(new Insets(12));
        return form;
    }

    private void onSaleTypeChanged() {
        boolean isService = TYPE_SERVICO.equalsIgnoreCase(saleTypeCombo.getValue());
        // services default to "pendente" (bill after delivery)
        if (isService) saleStatusCombo.setValue(STATUS_PENDENTE);
    }

    private void updateSaleTotalPreview() {
        try {
            double qty   = Double.parseDouble(saleQtyField.getText().trim().replace(',', '.'));
            double price = Double.parseDouble(salePriceField.getText().trim().replace(',', '.'));
            saleTotalPreview.setText("Total: " + formatBrl(qty * price));
        } catch (NumberFormatException e) {
            saleTotalPreview.setText("Total: —");
        }
    }

    private VBox buildSalesActionsCard() {
        Label title = new Label("⚡ Ações Rápidas");
        title.getStyleClass().add("section-title");
        title.setMaxWidth(Double.MAX_VALUE);

        saleMarkReceivedBtn = new Button("✓  Marcar como Recebido");
        saleMarkReceivedBtn.getStyleClass().add("secondary-button");
        saleMarkReceivedBtn.setMaxWidth(Double.MAX_VALUE);
        saleMarkReceivedBtn.setDisable(true);
        saleMarkReceivedBtn.setOnAction(e -> {
            SaleEntry sel = salesListView.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.isPending()) return;
            salesRepo().update(sel.id(), sel.productName(), sel.itemType(), sel.quantity(),
                    sel.unitPrice(), sel.saleDate(), sel.clientName(), sel.notes(), STATUS_RECEBIDO);
            refresh();
            ctx.triggerDashboardRefresh();
            ctx.setStatus("Recebimento registrado: " + sel.productName());
        });

        Button editBtn = new Button("✎  Editar Selecionado");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setOnAction(e -> {
            SaleEntry sel = salesListView.getSelectionModel().getSelectedItem();
            if (sel != null) loadSaleIntoForm(sel);
            else ctx.setStatus("Selecione uma venda para editar.");
        });

        Button delBtn = new Button("✕  Excluir Selecionado");
        delBtn.getStyleClass().add("danger-button");
        delBtn.setMaxWidth(Double.MAX_VALUE);
        delBtn.setOnAction(e -> {
            SaleEntry sel = salesListView.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione uma venda."); return; }
            new Alert(Alert.AlertType.CONFIRMATION,
                    "Excluir venda de \"" + sel.productName() + "\"?",
                    ButtonType.OK, ButtonType.CANCEL)
                    .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                        salesRepo().deleteById(sel.id());
                        if (editingSaleId != null && editingSaleId.equals(sel.id())) resetSaleForm();
                        refresh();
                        ctx.triggerDashboardRefresh();
                        ctx.setStatus("Venda excluída.");
                    });
        });

        VBox card = new VBox(6, title, saleMarkReceivedBtn, editBtn, delBtn);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private void updateSaleActionBtns(SaleEntry sel) {
        if (saleMarkReceivedBtn == null) return;
        boolean canMark = sel != null && sel.isPending();
        saleMarkReceivedBtn.setDisable(!canMark);
        saleMarkReceivedBtn.setText(canMark
                ? "✓  Marcar Recebido — " + sel.productName()
                : "✓  Marcar como Recebido");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEÇÃO CATÁLOGO
    // ═══════════════════════════════════════════════════════════════════════════

    private VBox buildCatalogoSection() {
        SplitPane sp = new SplitPane(buildCatalogList(), buildCatalogRightScroll());
        sp.setDividerPositions(0.60);
        sp.setPadding(new Insets(8, 14, 14, 14));
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox section = new VBox(0, buildCatalogoKpiBar(), sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return section;
    }

    private HBox buildCatalogoKpiBar() {
        kpiTotalItensLbl  = kpiLbl("0");
        kpiMatCatLbl      = kpiLbl("0");
        kpiSvcCatLbl      = kpiLbl("0");
        kpiLowStockLbl    = kpiLbl("0");
        kpiStockValueLbl  = kpiLbl("R$ 0,00");

        HBox bar = new HBox(10,
                kpiCard("TOTAL ITENS",   kpiTotalItensLbl, "kpi-blue"),
                kpiCard("MATERIAIS",     kpiMatCatLbl,     "kpi-indigo"),
                kpiCard("SERVIÇOS",      kpiSvcCatLbl,     "kpi-cyan"),
                kpiCard("ESTOQUE BAIXO", kpiLowStockLbl,   "kpi-red"),
                kpiCard("VALOR TABELA",  kpiStockValueLbl, "kpi-green"));
        bar.setPadding(new Insets(10, 14, 6, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ── Lista do catálogo ─────────────────────────────────────────────────────

    private VBox buildCatalogList() {
        catalogListView = new ListView<>(catalogDisplay);
        catalogListView.getStyleClass().add("clean-list");
        catalogListView.setCellFactory(lv -> new CatalogItemCell());
        VBox.setVgrow(catalogListView, Priority.ALWAYS);

        catalogListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                InventoryItem sel = catalogListView.getSelectionModel().getSelectedItem();
                if (sel != null) loadCatalogItemIntoForm(sel);
            }
        });
        catalogListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> updateCatalogActionBtns(sel));

        Label hint = new Label("Clique duplo para editar  ·  Selecione para ações rápidas");
        hint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #7a9bbf; -fx-padding: 4 0 0 6;"
                + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

        VBox panel = new VBox(0, catalogListView, hint);
        panel.getStyleClass().add("section-card");
        panel.setPadding(new Insets(0));
        VBox.setVgrow(catalogListView, Priority.ALWAYS);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private ScrollPane buildCatalogRightScroll() {
        lowStockBox = new VBox(6);
        VBox right = new VBox(10, buildCatalogFormCard(), buildCatalogActionsCard(), buildLowStockCard());
        right.setPadding(new Insets(0, 0, 0, 4));
        ScrollPane scroll = new ScrollPane(right);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    // ── Formulário do catálogo ────────────────────────────────────────────────

    private VBox buildCatalogFormCard() {
        catalogFormLabel = new Label("🏪 Novo Item do Catálogo");
        catalogFormLabel.getStyleClass().add("section-title");
        catalogFormLabel.setMaxWidth(Double.MAX_VALUE);

        catalogTypeCombo = new ComboBox<>();
        catalogTypeCombo.getItems().addAll(TYPE_MATERIAL, TYPE_SERVICO);
        catalogTypeCombo.setValue(TYPE_MATERIAL);
        catalogTypeCombo.getStyleClass().add("input-control");
        catalogTypeCombo.setMaxWidth(Double.MAX_VALUE);
        catalogTypeCombo.setOnAction(e -> updateCatalogFormForType(catalogTypeCombo.getValue()));

        catalogNameField = new TextField();
        catalogNameField.getStyleClass().add("input-control");
        catalogNameField.setPromptText("Ex: Cabo HDMI, Consultoria de TI, Instalação elétrica...");

        catalogPriceField = new TextField();
        catalogPriceField.getStyleClass().add("input-control");
        catalogPriceField.setPromptText("0,00");

        catalogCategoryField = new TextField();
        catalogCategoryField.getStyleClass().add("input-control");
        catalogCategoryField.setPromptText("Ex: Eletrônicos, TI, Elétrica, Serviços gerais...");

        VBox priceBox = new VBox(3, fieldLabel("Preço unitário (R$) *"), catalogPriceField);
        VBox catBox   = new VBox(3, fieldLabel("Categoria"), catalogCategoryField);
        HBox.setHgrow(priceBox, Priority.ALWAYS); HBox.setHgrow(catBox, Priority.ALWAYS);
        HBox priceCatRow = new HBox(8, priceBox, catBox);

        // Campos de estoque (somente materiais)
        catalogQtyField = new TextField("0");
        catalogQtyField.getStyleClass().add("input-control");
        catalogQtyField.setPromptText("Qtd em estoque");

        catalogMinQtyField = new TextField("0");
        catalogMinQtyField.getStyleClass().add("input-control");
        catalogMinQtyField.setPromptText("Qtd mínima (alerta)");

        VBox qtyBox = new VBox(3, fieldLabel("Estoque atual"), catalogQtyField);
        VBox minBox = new VBox(3, fieldLabel("Estoque mínimo (alerta)"), catalogMinQtyField);
        HBox.setHgrow(qtyBox, Priority.ALWAYS); HBox.setHgrow(minBox, Priority.ALWAYS);
        catalogStockBox = new VBox(8, new HBox(8, qtyBox, minBox));

        // Descrição
        catalogDescArea = new TextArea();
        catalogDescArea.getStyleClass().add("input-control");
        catalogDescArea.setPromptText("Especificações, escopo do serviço, modo de entrega...");
        catalogDescArea.setPrefRowCount(2);
        catalogDescArea.setWrapText(true);

        // Botões
        catalogSubmitBtn = new Button("＋ Adicionar ao Catálogo");
        catalogSubmitBtn.getStyleClass().add("primary-button");
        catalogSubmitBtn.setMaxWidth(Double.MAX_VALUE);
        catalogSubmitBtn.setOnAction(e -> submitCatalogItem());

        catalogCancelBtn = new Button("Cancelar");
        catalogCancelBtn.getStyleClass().add("secondary-button");
        catalogCancelBtn.setOnAction(e -> resetCatalogForm());
        UIHelper.setConditionalVisible(catalogCancelBtn, false);

        catalogDeleteBtn = new Button("✕ Excluir");
        catalogDeleteBtn.getStyleClass().add("danger-button");
        catalogDeleteBtn.setOnAction(e -> deleteCatalogItemFromForm());
        UIHelper.setConditionalVisible(catalogDeleteBtn, false);

        HBox btnRow = new HBox(6, catalogSubmitBtn, catalogCancelBtn, catalogDeleteBtn);
        HBox.setHgrow(catalogSubmitBtn, Priority.ALWAYS);

        VBox form = new VBox(8,
                catalogFormLabel,
                fieldRow("Tipo *", catalogTypeCombo),
                fieldRow("Nome *", catalogNameField),
                priceCatRow,
                catalogStockBox,
                fieldRow("Descrição", catalogDescArea),
                btnRow);
        form.getStyleClass().add("section-card");
        form.setPadding(new Insets(12));
        return form;
    }

    private void updateCatalogFormForType(String type) {
        UIHelper.setConditionalVisible(catalogStockBox, TYPE_MATERIAL.equalsIgnoreCase(type));
    }

    // ── Ações rápidas do catálogo ─────────────────────────────────────────────

    private VBox buildCatalogActionsCard() {
        Label title = new Label("⚡ Ações Rápidas");
        title.getStyleClass().add("section-title");
        title.setMaxWidth(Double.MAX_VALUE);

        // Criar venda a partir deste item
        catalogSellBtn = new Button("🛒  Vender Este Item");
        catalogSellBtn.getStyleClass().add("primary-button");
        catalogSellBtn.setMaxWidth(Double.MAX_VALUE);
        catalogSellBtn.setDisable(true);
        catalogSellBtn.setOnAction(e -> {
            InventoryItem sel = catalogListView.getSelectionModel().getSelectedItem();
            if (sel != null) preFillSaleFromCatalog(sel);
        });

        // Ajuste de estoque (apenas materiais)
        Label adjTitle = new Label("Ajustar estoque de material:");
        adjTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #1e3a5f; -fx-font-weight: 600; -fx-padding: 6 0 0 0;");

        adjustQtyField = new TextField();
        adjustQtyField.getStyleClass().add("input-control");
        adjustQtyField.setPromptText("Quantidade");
        adjustQtyField.setPrefWidth(80);

        Button adjInBtn  = new Button("＋ Entrada");
        adjInBtn.getStyleClass().add("secondary-button");
        adjInBtn.setOnAction(e -> adjustStock(true));

        Button adjOutBtn = new Button("− Saída");
        adjOutBtn.getStyleClass().add("danger-button");
        adjOutBtn.setOnAction(e -> adjustStock(false));

        HBox adjRow = new HBox(6, adjustQtyField, adjInBtn, adjOutBtn);
        adjRow.setAlignment(Pos.CENTER_LEFT);

        // Editar / Excluir
        Button editBtn = new Button("✎  Editar Selecionado");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setOnAction(e -> {
            InventoryItem sel = catalogListView.getSelectionModel().getSelectedItem();
            if (sel != null) loadCatalogItemIntoForm(sel);
            else ctx.setStatus("Selecione um item.");
        });

        Button delBtn = new Button("✕  Excluir Selecionado");
        delBtn.getStyleClass().add("danger-button");
        delBtn.setMaxWidth(Double.MAX_VALUE);
        delBtn.setOnAction(e -> {
            InventoryItem sel = catalogListView.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione um item."); return; }
            new Alert(Alert.AlertType.CONFIRMATION,
                    "Excluir \"" + sel.productName() + "\" do catálogo?",
                    ButtonType.OK, ButtonType.CANCEL)
                    .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                        inventoryRepo().deleteById(sel.id());
                        if (editingItemId != null && editingItemId.equals(sel.id())) resetCatalogForm();
                        refreshCatalog();
                        ctx.triggerDashboardRefresh();
                        ctx.setStatus("Item excluído: " + sel.productName());
                    });
        });

        VBox card = new VBox(6, title, catalogSellBtn, adjTitle, adjRow, editBtn, delBtn);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private void updateCatalogActionBtns(InventoryItem sel) {
        if (catalogSellBtn == null) return;
        catalogSellBtn.setDisable(sel == null);
        catalogSellBtn.setText(sel != null
                ? "🛒  Vender — " + sel.productName()
                : "🛒  Vender Este Item");
    }

    private void preFillSaleFromCatalog(InventoryItem item) {
        UIHelper.setConditionalVisible(vendasSection,   true);
        UIHelper.setConditionalVisible(catalogoSection, false);
        VBox.setVgrow(vendasSection, Priority.ALWAYS);
        if (viewVendasBtn != null) viewVendasBtn.setSelected(true);

        resetSaleForm();
        saleProductField.setText(item.productName());
        saleTypeCombo.setValue(item.itemType() != null ? item.itemType() : TYPE_MATERIAL);
        if (item.unitPrice() > 0)
            salePriceField.setText(String.format(Locale.US, "%.2f", item.unitPrice()));
        onSaleTypeChanged();
        updateSaleTotalPreview();
        ctx.setStatus("Formulário pré-preenchido: " + item.productName());
    }

    private void adjustStock(boolean isEntry) {
        InventoryItem sel = catalogListView.getSelectionModel().getSelectedItem();
        if (sel == null)     { ctx.setStatus("Selecione um item para ajustar."); return; }
        if (sel.isService()) { ctx.setStatus("Serviços não têm controle de estoque."); return; }
        int qty;
        try { qty = Integer.parseInt(adjustQtyField.getText().trim()); }
        catch (NumberFormatException e) { ctx.setStatus("Quantidade inválida."); return; }
        inventoryRepo().adjustStock(sel.id(), isEntry ? qty : -qty);
        adjustQtyField.clear();
        refreshCatalog();
        ctx.triggerDashboardRefresh();
        ctx.setStatus((isEntry ? "Entrada" : "Saída") + " de " + qty + " un.: " + sel.productName());
    }

    // ── Painel de estoque baixo ───────────────────────────────────────────────

    private VBox buildLowStockCard() {
        Label title = new Label("⚠ Estoque Baixo — Materiais");
        title.getStyleClass().add("section-title");
        title.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(8, title, lowStockBox);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private void updateLowStockPanel(List<InventoryItem> all) {
        if (lowStockBox == null) return;
        lowStockBox.getChildren().clear();

        List<InventoryItem> low = all.stream()
                .filter(InventoryItem::isLowStock)
                .sorted((a, b) -> Integer.compare(a.quantity(), b.quantity()))
                .toList();

        if (low.isEmpty()) {
            Label ok = new Label("✅  Todos os materiais com estoque adequado.");
            ok.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #1b5e20;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
            lowStockBox.getChildren().add(ok);
            return;
        }
        for (InventoryItem item : low) {
            Label nameLbl = new Label(item.productName());
            nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #b71c1c;");
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            Label stockLbl = new Label(item.quantity() + " un.  (mín: " + item.minimumQuantity() + ")");
            stockLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #c62828;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            HBox row = new HBox(8, nameLbl, stockLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 8, 5, 8));
            row.setStyle("-fx-background-color: #fff5f5; -fx-background-radius: 5;"
                    + " -fx-border-color: #ffcdd2; -fx-border-radius: 5;");
            lowStockBox.getChildren().add(row);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LÓGICA DO FORMULÁRIO — VENDAS
    // ═══════════════════════════════════════════════════════════════════════════

    private void submitSale() {
        if (saleProductField.getText().isBlank()) {
            ctx.setStatus("Nome do produto/serviço é obrigatório."); return;
        }
        int qty;
        try { qty = Integer.parseInt(saleQtyField.getText().trim()); }
        catch (NumberFormatException e) { ctx.setStatus("Quantidade inválida."); return; }
        double price;
        try { price = Double.parseDouble(salePriceField.getText().trim().replace(',', '.')); }
        catch (NumberFormatException e) { ctx.setStatus("Valor inválido."); return; }

        String product = saleProductField.getText().trim();
        String type    = saleTypeCombo.getValue();
        String client  = saleClientField.getText().trim();
        String notes   = saleNotesArea.getText().trim();
        String status  = saleStatusCombo.getValue();
        LocalDate date = saleDatePicker.getValue();

        if (editingSaleId == null) {
            salesRepo().save(product, type, qty, price, date,
                    client.isBlank() ? null : client,
                    notes.isBlank()  ? null : notes, status);
            ctx.setStatus("Venda registrada: " + product + " — " + formatBrl(qty * price));
        } else {
            salesRepo().update(editingSaleId, product, type, qty, price, date,
                    client.isBlank() ? null : client,
                    notes.isBlank()  ? null : notes, status);
            ctx.setStatus("Venda atualizada: " + product);
        }
        resetSaleForm();
        refresh();
        ctx.triggerDashboardRefresh();
    }

    private void loadSaleIntoForm(SaleEntry s) {
        editingSaleId = s.id();
        saleTypeCombo.setValue(s.itemType() != null ? s.itemType() : TYPE_MATERIAL);
        catalogPickCombo.setValue(null);
        saleProductField.setText(s.productName());
        saleQtyField.setText(String.valueOf(s.quantity()));
        salePriceField.setText(String.format(Locale.US, "%.2f", s.unitPrice()));
        saleClientField.setText(s.clientName() != null ? s.clientName() : "");
        saleDatePicker.setValue(s.saleDate());
        saleStatusCombo.setValue(s.status() != null ? s.status() : STATUS_RECEBIDO);
        saleNotesArea.setText(s.notes() != null ? s.notes() : "");
        updateSaleTotalPreview();

        saleFormLabel.setText("✎ Editando: \"" + s.productName() + "\"");
        saleSubmitBtn.setText("💾 Salvar alterações");
        UIHelper.setConditionalVisible(saleCancelBtn, true);
        UIHelper.setConditionalVisible(saleDeleteBtn, true);
        ctx.setStatus("Editando venda — altere os campos e salve.");
    }

    private void resetSaleForm() {
        editingSaleId = null;
        if (saleTypeCombo    != null) saleTypeCombo.setValue(TYPE_MATERIAL);
        if (catalogPickCombo != null) catalogPickCombo.setValue(null);
        if (saleProductField != null) saleProductField.clear();
        if (saleQtyField     != null) saleQtyField.setText("1");
        if (salePriceField   != null) salePriceField.clear();
        if (saleClientField  != null) saleClientField.clear();
        if (saleDatePicker   != null) saleDatePicker.setValue(LocalDate.now());
        if (saleStatusCombo  != null) saleStatusCombo.setValue(STATUS_RECEBIDO);
        if (saleNotesArea    != null) saleNotesArea.clear();
        if (saleTotalPreview != null) saleTotalPreview.setText("Total: —");
        if (saleFormLabel    != null) saleFormLabel.setText("📦 Nova Venda");
        if (saleSubmitBtn    != null) saleSubmitBtn.setText("＋ Registrar Venda");
        UIHelper.setConditionalVisible(saleCancelBtn, false);
        UIHelper.setConditionalVisible(saleDeleteBtn, false);
        if (salesListView != null) salesListView.getSelectionModel().clearSelection();
    }

    private void deleteSaleFromForm() {
        if (editingSaleId == null) return;
        new Alert(Alert.AlertType.CONFIRMATION, "Excluir esta venda?", ButtonType.OK, ButtonType.CANCEL)
                .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    salesRepo().deleteById(editingSaleId);
                    resetSaleForm();
                    refresh();
                    ctx.triggerDashboardRefresh();
                    ctx.setStatus("Venda excluída.");
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LÓGICA DO FORMULÁRIO — CATÁLOGO
    // ═══════════════════════════════════════════════════════════════════════════

    private void submitCatalogItem() {
        if (catalogNameField.getText().isBlank()) {
            ctx.setStatus("Nome é obrigatório."); return;
        }
        double price;
        try { price = Double.parseDouble(catalogPriceField.getText().trim().replace(',', '.')); }
        catch (NumberFormatException e) { ctx.setStatus("Preço inválido."); return; }

        String type = catalogTypeCombo.getValue();
        String name = catalogNameField.getText().trim();
        String cat  = catalogCategoryField.getText().trim();
        String desc = catalogDescArea.getText().trim();
        boolean isService = TYPE_SERVICO.equalsIgnoreCase(type);
        int qty = 0, min = 0;
        if (!isService) {
            try { qty = Integer.parseInt(catalogQtyField.getText().trim()); }
            catch (NumberFormatException e) { ctx.setStatus("Quantidade inválida."); return; }
            try { min = Integer.parseInt(catalogMinQtyField.getText().trim()); }
            catch (NumberFormatException e) { ctx.setStatus("Quantidade mínima inválida."); return; }
        }

        if (editingItemId == null) {
            inventoryRepo().save(name, type, qty, min, price,
                    cat.isBlank() ? "Geral" : cat, desc.isBlank() ? null : desc);
            ctx.setStatus("Item adicionado ao catálogo: " + name);
        } else {
            inventoryRepo().update(editingItemId, name, type, qty, min, price,
                    cat.isBlank() ? "Geral" : cat, desc.isBlank() ? null : desc);
            ctx.setStatus("Item atualizado: " + name);
        }
        resetCatalogForm();
        refreshCatalog();
        ctx.triggerDashboardRefresh();
    }

    private void loadCatalogItemIntoForm(InventoryItem item) {
        editingItemId = item.id();
        catalogTypeCombo.setValue(item.itemType() != null ? item.itemType() : TYPE_MATERIAL);
        catalogNameField.setText(item.productName());
        catalogPriceField.setText(String.format(Locale.US, "%.2f", item.unitPrice()));
        catalogCategoryField.setText(item.category() != null ? item.category() : "");
        catalogQtyField.setText(String.valueOf(item.quantity()));
        catalogMinQtyField.setText(String.valueOf(item.minimumQuantity()));
        catalogDescArea.setText(item.description() != null ? item.description() : "");
        updateCatalogFormForType(item.itemType());

        catalogFormLabel.setText("✎ Editando: \"" + item.productName() + "\"");
        catalogSubmitBtn.setText("💾 Salvar alterações");
        UIHelper.setConditionalVisible(catalogCancelBtn, true);
        UIHelper.setConditionalVisible(catalogDeleteBtn, true);
        ctx.setStatus("Editando item — altere os campos e salve.");
    }

    private void resetCatalogForm() {
        editingItemId = null;
        if (catalogTypeCombo     != null) { catalogTypeCombo.setValue(TYPE_MATERIAL); updateCatalogFormForType(TYPE_MATERIAL); }
        if (catalogNameField     != null) catalogNameField.clear();
        if (catalogPriceField    != null) catalogPriceField.clear();
        if (catalogCategoryField != null) catalogCategoryField.clear();
        if (catalogQtyField      != null) catalogQtyField.setText("0");
        if (catalogMinQtyField   != null) catalogMinQtyField.setText("0");
        if (catalogDescArea      != null) catalogDescArea.clear();
        if (catalogFormLabel     != null) catalogFormLabel.setText("🏪 Novo Item do Catálogo");
        if (catalogSubmitBtn     != null) catalogSubmitBtn.setText("＋ Adicionar ao Catálogo");
        UIHelper.setConditionalVisible(catalogCancelBtn, false);
        UIHelper.setConditionalVisible(catalogDeleteBtn, false);
        if (catalogListView != null) catalogListView.getSelectionModel().clearSelection();
    }

    private void deleteCatalogItemFromForm() {
        if (editingItemId == null) return;
        new Alert(Alert.AlertType.CONFIRMATION, "Excluir este item do catálogo?",
                ButtonType.OK, ButtonType.CANCEL)
                .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    inventoryRepo().deleteById(editingItemId);
                    resetCatalogForm();
                    refreshCatalog();
                    ctx.triggerDashboardRefresh();
                    ctx.setStatus("Item excluído do catálogo.");
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REFRESH / KPIs
    // ═══════════════════════════════════════════════════════════════════════════

    private void refreshSales() {
        List<SaleEntry> all = salesRepo().findAll();
        salesDisplay.setAll(all);

        long total    = all.size();
        double receita  = all.stream().filter(e -> STATUS_RECEBIDO.equalsIgnoreCase(e.status())).mapToDouble(SaleEntry::total).sum();
        double aReceber = all.stream().filter(SaleEntry::isPending).mapToDouble(SaleEntry::total).sum();
        long mat = all.stream().filter(e -> TYPE_MATERIAL.equalsIgnoreCase(e.itemType())).count();
        long svc = all.stream().filter(SaleEntry::isService).count();

        if (kpiTotalVendasLbl != null) kpiTotalVendasLbl.setText(String.valueOf(total));
        if (kpiReceitaLbl     != null) kpiReceitaLbl.setText(formatBrl(receita));
        if (kpiReceberLbl     != null) kpiReceberLbl.setText(formatBrl(aReceber));
        if (kpiMatVendasLbl   != null) kpiMatVendasLbl.setText(String.valueOf(mat));
        if (kpiSvcVendasLbl   != null) kpiSvcVendasLbl.setText(String.valueOf(svc));

        // Re-popula combo do catálogo no formulário de venda
        if (catalogPickCombo != null) {
            InventoryItem cur = catalogPickCombo.getValue();
            catalogPickCombo.getItems().setAll(inventoryRepo().findAll());
            catalogPickCombo.setValue(cur);
        }
    }

    private void refreshCatalog() {
        List<InventoryItem> all = inventoryRepo().findAll();
        catalogDisplay.setAll(all);

        long total    = all.size();
        long mat      = all.stream().filter(e -> TYPE_MATERIAL.equalsIgnoreCase(e.itemType())).count();
        long svc      = all.stream().filter(InventoryItem::isService).count();
        long lowStock = all.stream().filter(InventoryItem::isLowStock).count();
        double value  = all.stream()
                .filter(e -> TYPE_MATERIAL.equalsIgnoreCase(e.itemType()))
                .mapToDouble(e -> e.quantity() * e.unitPrice()).sum();

        if (kpiTotalItensLbl != null) kpiTotalItensLbl.setText(String.valueOf(total));
        if (kpiMatCatLbl     != null) kpiMatCatLbl.setText(String.valueOf(mat));
        if (kpiSvcCatLbl     != null) kpiSvcCatLbl.setText(String.valueOf(svc));
        if (kpiLowStockLbl   != null) kpiLowStockLbl.setText(String.valueOf(lowStock));
        if (kpiStockValueLbl != null) kpiStockValueLbl.setText(formatBrl(value));

        updateLowStockPanel(all);
    }

    public void refresh() {
        refreshSales();
        refreshCatalog();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CÉLULAS PERSONALIZADAS
    // ═══════════════════════════════════════════════════════════════════════════

    private class SaleEntryCell extends ListCell<SaleEntry> {
        @Override protected void updateItem(SaleEntry e, boolean empty) {
            super.updateItem(e, empty);
            if (empty || e == null) { setGraphic(null); setText(null); return; }

            boolean svc = e.isService();
            boolean pend = e.isPending();

            Label typeBadge = badge(svc ? "SERVIÇO" : "MATERIAL",
                    svc ? "#e0f7f4:#006d5b" : "#e8eaf6:#283593");

            Label statusBadge = badge(pend ? "A RECEBER" : "RECEBIDO",
                    pend ? "#fff3e0:#e65100" : "#e8f5e9:#1b5e20");

            Label nameLbl = new Label(e.productName());
            nameLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #0d1b2a;");
            nameLbl.setWrapText(false);

            Label subLbl = new Label(
                    (e.clientName() != null && !e.clientName().isBlank() ? e.clientName() : "")
                    + "  " + e.quantity() + " × " + formatBrl(e.unitPrice()));
            subLbl.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #5a7a9e;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            VBox nameBox = new VBox(1, nameLbl, subLbl);
            HBox.setHgrow(nameBox, Priority.ALWAYS);

            Label dateLbl = new Label(e.saleDate() != null ? e.saleDate().format(DATE_FMT) : "—");
            dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a7a9e;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            Label totalLbl = new Label(formatBrl(e.total()));
            totalLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                    + " -fx-text-fill: " + (pend ? "#e65100;" : "#1b5e20;"));

            HBox row = new HBox(8, typeBadge, nameBox, dateLbl, statusBadge, totalLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 8, 5, 8));
            setGraphic(row); setText(null);
        }
    }

    private class CatalogItemCell extends ListCell<InventoryItem> {
        @Override protected void updateItem(InventoryItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); setText(null); return; }

            boolean svc      = item.isService();
            boolean lowStock = item.isLowStock();

            Label typeBadge = badge(svc ? "SERVIÇO" : "MATERIAL",
                    svc ? "#e0f7f4:#006d5b" : "#e8eaf6:#283593");

            Label nameLbl = new Label(item.productName());
            nameLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #0d1b2a;");

            Label catLbl = new Label(item.category() != null ? item.category() : "");
            catLbl.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #5a7a9e;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;");

            VBox nameBox = new VBox(1, nameLbl, catLbl);
            HBox.setHgrow(nameBox, Priority.ALWAYS);

            Label stockLbl;
            if (!svc) {
                stockLbl = new Label(item.quantity() + " un.");
                stockLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700;"
                        + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                        + " -fx-text-fill: " + (lowStock ? "#b71c1c;" : "#1e3a5f;"));
            } else {
                stockLbl = new Label("∞");
                stockLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #5a7a9e;");
            }

            Label stockBadge = svc ? new Label() :
                    badge(lowStock ? "BAIXO" : "OK",
                            lowStock ? "#ffebee:#b71c1c" : "#e8f5e9:#1b5e20");

            Label priceLbl = new Label(formatBrl(item.unitPrice()));
            priceLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800;"
                    + " -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                    + " -fx-text-fill: #1565c0;");

            HBox row = new HBox(8, typeBadge, nameBox, stockLbl, stockBadge, priceLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 8, 5, 8));
            setGraphic(row); setText(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** "bg:#fg" — cria badge inline. */
    private static Label badge(String text, String colors) {
        String[] c = colors.split(":");
        Label l = new Label("  " + text + "  ");
        l.setStyle("-fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-font-size: 9.5px;"
                + " -fx-font-weight: 700; -fx-font-family: 'JetBrains Mono','Consolas',monospace;"
                + " -fx-background-color: " + c[0] + "; -fx-text-fill: " + c[1] + ";");
        return l;
    }

    private static VBox kpiCard(String title, Label valueLbl, String colorClass) {
        Label tl = new Label(title); tl.getStyleClass().add("kpi-title");
        VBox card = new VBox(2, tl, valueLbl);
        card.getStyleClass().addAll("kpi-mini", colorClass);
        return card;
    }

    private static Label kpiLbl(String text) {
        Label l = new Label(text); l.getStyleClass().add("kpi-value"); return l;
    }

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

    private static String formatBrl(double value) { return BRL.format(value); }
}
