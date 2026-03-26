package com.pessoal.agenda.model;

import java.time.LocalDateTime;

/**
 * Estado de um passo dentro de uma execução específica.
 * Criado quando a execução é iniciada (cópia snapshot dos passos do template).
 */
public record ProtocolExecutionStep(
        long          id,
        long          executionId,
        long          stepId,
        String        stepText,
        String        stepNotes,
        boolean       critical,
        int           stepOrder,
        boolean       checked,
        LocalDateTime checkedAt,
        String        observationNotes
) {}

