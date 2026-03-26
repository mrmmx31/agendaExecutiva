package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.Category;
import com.pessoal.agenda.model.CategoryDomain;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Controller da aba de Configurações.
 * Gerencia as categorias de todos os domínios do sistema.
 */
public class ConfigController {

    private final SharedContext ctx;

    public ConfigController(SharedContext ctx) {
        this.ctx = ctx;
    }

    public Tab buildTab() {
        Tab tab = new Tab("⚙ Configurações");
        tab.setClosable(false);

        VBox taskSection      = buildCategorySection("Categorias de Tarefas",
                "config-task",       CategoryDomain.TASK,       ctx.taskCatList);
        VBox checklistSection = buildCategorySection("Categorias de Protocolos",
                "config-checklist",  CategoryDomain.CHECKLIST,  ctx.checklistCatList);
        VBox studySection     = buildCategorySection("Categorias de Estudos",
                "config-study",      CategoryDomain.STUDY,      ctx.studyCatList);
        VBox studyTypeSection = buildCategorySection("Tipos de Estudo",
                "config-study-type", CategoryDomain.STUDY_TYPE, ctx.studyTypeCatList);
        VBox ideaSection      = buildCategorySection("Categorias de Projetos e Ideias",
                "config-idea",       CategoryDomain.IDEA,       ctx.ideaCatList);

        HBox row1 = new HBox(14, taskSection, checklistSection);
        HBox row2 = new HBox(14, studySection, studyTypeSection);
        HBox row3 = new HBox(14, ideaSection);
        HBox.setHgrow(taskSection,       Priority.ALWAYS);
        HBox.setHgrow(checklistSection,  Priority.ALWAYS);
        HBox.setHgrow(studySection,      Priority.ALWAYS);
        HBox.setHgrow(studyTypeSection,  Priority.ALWAYS);
        HBox.setHgrow(ideaSection,       Priority.ALWAYS);

        Label header = new Label(
                "Gerencie as categorias e tipos que organizam cada módulo do sistema. " +
                "As categorias e tipos ficam disponíveis como filtros e opções de seleção nas abas.");
        header.setWrapText(true);

        VBox content = new VBox(14, header, row1, row2, row3);
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        tab.setContent(scroll);
        return tab;
    }

    private VBox buildCategorySection(String title, String styleClass,
                                      CategoryDomain domain,
                                      ObservableList<Category> catList) {
        ListView<Category> listView = new ListView<>(catList);
        listView.getStyleClass().add("clean-list");
        listView.setPrefHeight(180);

        TextField nameField = new TextField();
        nameField.getStyleClass().add("input-control");
        nameField.setPromptText("Nome da nova categoria...");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button addBtn = new Button("Adicionar");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> {
            if (nameField.getText().isBlank()) { ctx.setStatus("Informe o nome da categoria."); return; }
            try {
                AppContextHolder.get().categoryService().add(nameField.getText(), domain);
                nameField.clear();
                ctx.refreshCategories();
                ctx.setStatus("Categoria adicionada com sucesso.");
            } catch (IllegalArgumentException ex) {
                ctx.setStatus("Erro: " + ex.getMessage());
            }
        });
        nameField.setOnAction(e -> addBtn.fire());

        Button removeBtn = new Button("Remover selecionada");
        removeBtn.getStyleClass().add("secondary-button");
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setOnAction(e -> {
            Category sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione uma categoria para remover."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar remoção");
            confirm.setHeaderText("Remover categoria \"" + sel.name() + "\"?");
            confirm.setContentText(
                    "Esta ação remove a categoria permanentemente.\n" +
                    "Os registros vinculados a ela não serão excluídos,\n" +
                    "mas ficarão sem categoria associada.");
            confirm.getDialogPane().getStyleClass().add("confirm-dialog");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    AppContextHolder.get().categoryService().remove(sel.id());
                    ctx.refreshCategories();
                    ctx.setStatus("Categoria \"" + sel.name() + "\" removida.");
                }
            });
        });

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        HBox addBar = new HBox(8, nameField, addBtn);
        addBar.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, titleLabel, listView, addBar, removeBtn);
        section.getStyleClass().addAll("config-section", styleClass);
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }
}

