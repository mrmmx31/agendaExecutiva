package com.pessoal.agenda.ui.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

final class ResponsiveWindowLayout {

    private ResponsiveWindowLayout() {}

    static FlowPane actionFlow(Node... actions) {
        FlowPane flow = new FlowPane(8, 6);
        flow.setAlignment(Pos.CENTER_LEFT);
        flow.setMaxWidth(Double.MAX_VALUE);
        flow.getChildren().addAll(actions);
        return flow;
    }

    static void makeFlexible(Label label) {
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
    }

    static void preserveButtonText(Button... buttons) {
        for (Button button : buttons) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
    }
}
