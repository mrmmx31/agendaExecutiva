package com.pessoal.agenda.model;

import java.time.LocalDate;

/**
 * Resultado de presença para um único dia do calendário.
 * Gerado pelo StudyAttendanceService — não é persistido.
 */
public record AttendanceDay(
        LocalDate date,
        AttendanceStatus status,
        int scheduledMinutes,  // conforme grade; 0 se não programado
        int actualMinutes      // registrado em study_entries
) {
    public enum AttendanceStatus {
        PRESENTE      ("✓", "attend-present"),
        PARCIAL       ("◑", "attend-partial"),
        AUSENTE       ("✗", "attend-absent"),
        COMPENSADO    ("○", "attend-compensated"),
        NAO_PROGRAMADO("–", "attend-none"),
        FUTURO        ("·", "attend-future");

        public final String symbol;
        public final String cssClass;
        AttendanceStatus(String symbol, String cssClass) {
            this.symbol   = symbol;
            this.cssClass = cssClass;
        }
    }
}

