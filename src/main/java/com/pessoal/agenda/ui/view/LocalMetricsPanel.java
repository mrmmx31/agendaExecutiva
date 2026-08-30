package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.LocalMetricSummary;
import com.pessoal.agenda.service.LocalMetricsService;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Objects;

public final class LocalMetricsPanel extends VBox {
    private final LocalMetricsService service;
    private final Label focusValue = valueLabel("local-metrics-focus");
    private final Label captureValue = valueLabel("local-metrics-capture");
    private final Label resumeValue = valueLabel("local-metrics-resume");
    private final Label status = new Label();

    public LocalMetricsPanel(LocalMetricsService service) {
        this.service = Objects.requireNonNull(service);
        setId("dashboard-local-metrics");
        getStyleClass().addAll("config-section", "reduced-attention");
        setSpacing(8);
        setPadding(new Insets(12, 14, 12, 14));

        Label title = new Label("Métricas locais");
        title.getStyleClass().add("section-title");
        Label privacy = new Label("Últimos 30 registros armazenados somente neste dispositivo.");
        privacy.getStyleClass().add("t-muted");
        privacy.setWrapText(true);
        status.setId("local-metrics-status");
        status.getStyleClass().add("t-danger");
        status.setWrapText(true);

        getChildren().addAll(title, privacy,
                metric("Tempo até a primeira ação de foco", focusValue),
                metric("Ações para salvar uma captura", captureValue),
                metric("Ações para retomar uma interrupção", resumeValue),
                status);
        refresh();
    }

    public void refresh() {
        boolean enabled = service.isEnabled();
        setVisible(enabled);
        setManaged(enabled);
        if (!enabled) return;
        try {
            var snapshot = service.snapshot();
            focusValue.setText(format(snapshot.focusStart(), "s"));
            captureValue.setText(format(snapshot.quickCapture(), " ação(ões)"));
            resumeValue.setText(format(snapshot.interruptionResume(), " ação(ões)"));
            status.setText("");
            status.setVisible(false);
            status.setManaged(false);
        } catch (RuntimeException error) {
            status.setText("Não foi possível carregar as métricas locais.");
            status.setVisible(true);
            status.setManaged(true);
        }
    }

    private static VBox metric(String labelText, Label value) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        return new VBox(2, label, value);
    }

    private static Label valueLabel(String id) {
        Label label = new Label();
        label.setId(id);
        label.getStyleClass().add("t-heading-sm");
        label.setWrapText(true);
        return label;
    }

    private static String format(LocalMetricSummary summary, String unit) {
        if (summary.samples() == 0) return "Ainda sem registros";
        double median = summary.median();
        String number = median == Math.rint(median)
                ? Long.toString(Math.round(median))
                : "%.1f".formatted(median);
        return number + unit + " · mediana de " + summary.samples() + " registro(s)";
    }
}
