package com.pessoal.agenda.model;

import java.time.LocalDateTime;

/**
/**
 * Modelo de um Protocolo Operacional (template/definição).
 *
 * linkedTaskTitle: campo transiente preenchido via LEFT JOIN em consultas — não é coluna do banco.
 * validityDays: para tipos periódicos, indica quantos dias o resultado permanece válido (0 = sem validade).
 */
public record Protocol(
        long                  id,
        String                name,
        String                category,
        ProtocolExecutionType executionType,
        String                description,
        Long                  linkedTaskId,
        String                linkedTaskTitle,
        int                   validityDays,
        String                timingMode,
        String                fixedTime,
        Integer               leadMinutes,
        LocalDateTime         createdAt
) {
    public boolean hasValidity()    { return validityDays > 0; }
    public boolean hasLinkedTask()  { return linkedTaskId != null; }
    public boolean hasFixedTime()   { return "FIXED_TIME".equalsIgnoreCase(timingMode) && fixedTime != null && !fixedTime.isBlank(); }
    public boolean hasLeadMinutes() { return "BEFORE_TASK".equalsIgnoreCase(timingMode) && leadMinutes != null && leadMinutes > 0; }
}



