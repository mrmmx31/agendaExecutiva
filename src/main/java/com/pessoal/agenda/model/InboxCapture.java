package com.pessoal.agenda.model;

import java.time.Instant;
import java.util.Objects;

public record InboxCapture(
        long id,
        String rawText,
        InboxCaptureKind kind,
        Instant createdAt,
        Instant triagedAt,
        Long targetId) {

    public InboxCapture {
        if (id < 0) throw new IllegalArgumentException("Id da captura nao pode ser negativo");
        Objects.requireNonNull(rawText, "Texto da captura e obrigatorio");
        if (rawText.isBlank()) throw new IllegalArgumentException("Texto da captura nao pode estar vazio");
        Objects.requireNonNull(kind, "Tipo da captura e obrigatorio");
        Objects.requireNonNull(createdAt, "Data da captura e obrigatoria");
        if (targetId != null && targetId <= 0) {
            throw new IllegalArgumentException("Destino da captura deve ser positivo");
        }
        if (kind == InboxCaptureKind.UNCLASSIFIED && (triagedAt != null || targetId != null)) {
            throw new IllegalArgumentException("Captura nao classificada nao pode ter destino ou triagem");
        }
        if (kind != InboxCaptureKind.UNCLASSIFIED && triagedAt == null) {
            throw new IllegalArgumentException("Captura classificada deve informar quando foi triada");
        }
    }
}
