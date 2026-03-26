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
        LocalDateTime         createdAt
) {
    public boolean hasValidity()    { return validityDays > 0; }
    public boolean hasLinkedTask()  { return linkedTaskId != null; }
}



