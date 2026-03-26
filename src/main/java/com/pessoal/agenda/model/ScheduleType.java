package com.pessoal.agenda.model;

/**
 * Tipo de agendamento da tarefa, inspirado em Palm Desktop / Outlook / MS-Project.
 *
 * SINGLE  — tarefa valida apenas em um dia especifico (comportamento padrao).
 * RANGE   — tarefa valida em um intervalo continuo de dias (due_date ate end_date).
 * WEEKLY  — tarefa recorrente em dias especificos da semana dentro de um intervalo
 *            (due_date ate end_date); os dias ativos sao armazenados em recurrence_days.
 */
public enum ScheduleType {
    SINGLE,
    RANGE,
    WEEKLY
}

