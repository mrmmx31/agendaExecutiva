package com.pessoal.agenda.model;

/**
 * Um passo dentro de um template de protocolo.
 * critical = true indica que este passo é obrigatório/bloqueante.
 */
public record ProtocolStep(
        long   id,
        long   templateId,
        int    stepOrder,
        String stepText,
        String notes,
        boolean critical
) {}

