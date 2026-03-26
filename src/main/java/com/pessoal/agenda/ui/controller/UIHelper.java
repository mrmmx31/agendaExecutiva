package com.pessoal.agenda.ui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Helpers visuais estáticos reutilizáveis por todos os controllers de UI.
 */
public final class UIHelper {

    private UIHelper() {}

    /** Cria um card de KPI para o dashboard. */
    public static StackPane createKpiCard(String title, Label valueLabel, String toneClass) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-title");
        valueLabel.getStyleClass().add("kpi-value");
        VBox content = new VBox(8, titleLabel, valueLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        StackPane card = new StackPane(content);
        card.getStyleClass().addAll("kpi-card", toneClass);
        card.setPrefWidth(230); card.setMinHeight(110);
        return card;
    }

    /** Cria uma seção com título e conteúdo estilizados como card. */
    public static VBox createCardSection(String title, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        VBox box = new VBox(10, titleLabel, content);
        box.getStyleClass().add("section-card");
        box.setPadding(new Insets(14));
        return box;
    }

    /** Cria um mini-KPI compacto para uso dentro das abas. */
    public static VBox createMiniKpi(String title, Label valueLabel, String toneClass) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-title");
        valueLabel.getStyleClass().add("kpi-value");
        VBox box = new VBox(4, titleLabel, valueLabel);
        box.getStyleClass().addAll("kpi-mini", toneClass);
        return box;
    }

    /** Mostra/oculta um node e remove-o do fluxo de layout quando invisível. */
    public static void setConditionalVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Cria um spacer que cresce horizontalmente (para HBox). */
    public static Region createSpacer() {
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        return sp;
    }
}

