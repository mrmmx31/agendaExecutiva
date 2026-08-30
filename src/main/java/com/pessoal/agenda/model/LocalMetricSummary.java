package com.pessoal.agenda.model;

public record LocalMetricSummary(int samples, Double median) {
    public LocalMetricSummary {
        if (samples < 0) throw new IllegalArgumentException("Amostras não podem ser negativas");
        if (samples == 0 && median != null) {
            throw new IllegalArgumentException("Métrica sem amostras não possui mediana");
        }
        if (samples > 0 && median == null) {
            throw new IllegalArgumentException("Métrica com amostras precisa de mediana");
        }
    }

    public static LocalMetricSummary empty() {
        return new LocalMetricSummary(0, null);
    }
}
