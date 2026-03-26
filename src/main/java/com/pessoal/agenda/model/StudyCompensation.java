package com.pessoal.agenda.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de compensação de uma sessão perdida.
 * Quando o estudante perde um dia programado, uma compensação pode ser
 * agendada em outro dia ou distribuída nos próximos dias de estudo.
 */
public record StudyCompensation(
        long          id,
        long          studyPlanId,
        String        studyTitle,         // campo transiente — preenchido por JOIN
        LocalDate     missedDate,
        LocalDate     compensationDate,   // null enquanto PENDENTE
        int           compensationMinutes,
        String        status,             // PENDENTE | CONCLUIDA | CANCELADA
        String        notes,
        LocalDateTime createdAt
) {
    public boolean isPending()   { return "PENDENTE".equals(status);  }
    public boolean isDone()      { return "CONCLUIDA".equals(status); }
    public boolean isCancelled() { return "CANCELADA".equals(status); }
}

