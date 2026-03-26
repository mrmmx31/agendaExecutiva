package com.pessoal.agenda.model;

import java.time.LocalDateTime;

/**
 * Uma execução concreta de um protocolo operacional.
 *
 * status: ATIVA | CONCLUIDA | CANCELADA
 */
public record ProtocolExecution(
        long          id,
        long          templateId,
        String        templateName,
        ProtocolExecutionType executionType,
        int           iterationNumber,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String        status,
        String        notes,
        int           totalSteps,
        int           checkedSteps
) {
    public double progressPercent() {
        return totalSteps > 0 ? (checkedSteps * 100.0 / totalSteps) : 0.0;
    }

    public boolean isActive()    { return "ATIVA".equals(status); }
    public boolean isCompleted() { return "CONCLUIDA".equals(status); }
    public boolean isCancelled() { return "CANCELADA".equals(status); }
}

