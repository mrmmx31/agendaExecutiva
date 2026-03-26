package com.pessoal.agenda.model;

import java.time.DayOfWeek;

/**
 * Um dia da semana com carga horária mínima dentro de um plano de estudo.
 * Representa a grade horária semanal comprometida pelo estudante.
 */
public record StudyScheduleDay(
        long       id,
        long       studyPlanId,
        DayOfWeek  dayOfWeek,
        int        minMinutes    // minutos mínimos a estudar nesse dia
) {
    public double minHours() { return minMinutes / 60.0; }

    /** Rótulo curto para exibir no calendário. */
    public static String shortLabel(DayOfWeek d) {
        return switch (d) {
            case MONDAY    -> "Seg";
            case TUESDAY   -> "Ter";
            case WEDNESDAY -> "Qua";
            case THURSDAY  -> "Qui";
            case FRIDAY    -> "Sex";
            case SATURDAY  -> "Sáb";
            case SUNDAY    -> "Dom";
        };
    }
}

