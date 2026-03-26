package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.DatabaseService;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Controller da aba Ideias e Projetos.
 */
public class IdeasController {

    private final SharedContext   ctx;
    private final DatabaseService db;

    private final ObservableList<DatabaseService.RowItem> items = FXCollections.observableArrayList();
    private String categoryFilter = null;

    public IdeasController(SharedContext ctx, DatabaseService db) {
        this.ctx = ctx;
        this.db  = db;
    }

    public Tab buildTab() {
        Tab tab = new Tab("Ideias e Projetos");
        tab.setClosable(false);

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getStyleClass().add("input-control");
        filterCombo.getItems().add("Todas as ideias");
        ctx.ideaCatNames.addListener((javafx.collections.ListChangeListener<String>) ch -> {
            String cur = filterCombo.getValue();
            filterCombo.getItems().setAll("Todas as ideias");
            filterCombo.getItems().addAll(ctx.ideaCatNames);
            filterCombo.setValue(cur != null ? cur : "Todas as ideias");
        });
        filterCombo.setValue("Todas as ideias");
        filterCombo.setOnAction(e -> {
            String v = filterCombo.getValue();
            categoryFilter = "Todas as ideias".equals(v) ? null : v;
            refresh();
        });

        TextField titleField = new TextField(); titleField.getStyleClass().add("input-control"); titleField.setPromptText("Título da ideia");
        TextArea  descArea   = new TextArea();  descArea.getStyleClass().add("input-control"); descArea.setPromptText("Descrição"); descArea.setPrefRowCount(4);

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getStyleClass().add("input-control");
        statusCombo.getItems().addAll("nova", "em validação", "em execução", "concluída");
        statusCombo.setValue("nova");

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getStyleClass().add("input-control");
        catCombo.setItems(ctx.ideaCatNames);
        catCombo.setValue(ctx.ideaCatNames.isEmpty() ? "Geral" : ctx.ideaCatNames.get(0));

        ListView<DatabaseService.RowItem> list = new ListView<>(items);
        list.getStyleClass().add("clean-list");

        Button addBtn = new Button("Salvar ideia");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> {
            if (titleField.getText().isBlank()) { ctx.setStatus("Título da ideia é obrigatório."); return; }
            String cat = catCombo.getValue() != null ? catCombo.getValue() : "Geral";
            AppContextHolder.get().projectIdeaRepository()
                    .save(titleField.getText().trim(), descArea.getText().trim(), statusCombo.getValue(), cat);
            titleField.clear(); descArea.clear();
            refresh(); ctx.triggerDashboardRefresh();
            ctx.setStatus("Ideia registrada.");
        });

        HBox filterBar = new HBox(8, new Label("Filtrar por:"), filterCombo);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("agenda-top-bar");

        VBox content = UIHelper.createCardSection("Pipeline de ideias e projetos pessoais", new VBox(10,
                filterBar,
                new HBox(8, new Label("Título"), titleField, new Label("Status"), statusCombo,
                        new Label("Cat."), catCombo, addBtn),
                descArea, list));
        content.setPadding(new Insets(14));
        tab.setContent(content);
        return tab;
    }

    public void refresh() {
        items.setAll(db.listProjectIdeas(categoryFilter));
    }
}

