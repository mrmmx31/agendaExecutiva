package com.pessoal.agenda.tools;

import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.Task;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Gera arquivos iCalendar (.ics) a partir de tarefas da agenda científica.
 * O arquivo gerado é compatível com Google Calendar, Outlook, Apple Calendar etc.
 *
 * Formato iCalendar (RFC 5545):
 *   – Tarefas do tipo SINGLE  → VEVENT de um dia (ou com horário se definido)
 *   – Tarefas do tipo RANGE   → VEVENT com DTSTART/DTEND no intervalo
 *   – Tarefas do tipo WEEKLY  → VEVENT com RRULE (recorrência semanal)
 */
public class ICalendarExporter {

    private static final DateTimeFormatter DT_FMT  = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Exporta a lista de tarefas para um arquivo .ics no caminho informado. */
    public static void export(List<Task> tasks, Path destination) throws IOException {
        try (Writer w = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writeCalendarHeader(w);
            for (Task t : tasks) {
                writeEvent(w, t);
            }
            writeCalendarFooter(w);
        }
    }

    // ── Cabeçalho / rodapé do calendário ──────────────────────────────────────

    private static void writeCalendarHeader(Writer w) throws IOException {
        w.write("BEGIN:VCALENDAR\r\n");
        w.write("VERSION:2.0\r\n");
        w.write("PRODID:-//Agenda Científica//PT\r\n");
        w.write("CALSCALE:GREGORIAN\r\n");
        w.write("METHOD:PUBLISH\r\n");
        w.write("X-WR-CALNAME:Agenda Científica\r\n");
        w.write("X-WR-TIMEZONE:" + ZoneId.systemDefault().getId() + "\r\n");
    }

    private static void writeCalendarFooter(Writer w) throws IOException {
        w.write("END:VCALENDAR\r\n");
    }

    // ── VEVENT por tarefa ──────────────────────────────────────────────────────

    private static void writeEvent(Writer w, Task t) throws IOException {
        w.write("BEGIN:VEVENT\r\n");
        w.write("UID:" + UUID.randomUUID() + "@agenda-cientifica\r\n");
        w.write("DTSTAMP:" + nowUtc() + "\r\n");
        w.write("SUMMARY:" + escapeText(t.title()) + "\r\n");

        if (t.notes() != null && !t.notes().isBlank()) {
            w.write("DESCRIPTION:" + foldLine(escapeText(t.notes())) + "\r\n");
        }

        // Prioridade (mapeada para a escala iCalendar 1–9)
        if (t.priority() != null) {
            int prio = switch (t.priority()) {
                case CRITICA -> 1;
                case ALTA    -> 2;
                case NORMAL  -> 5;
                case BAIXA   -> 9;
            };
            w.write("PRIORITY:" + prio + "\r\n");
        }

        // Categoria
        if (t.category() != null && !t.category().isBlank()) {
            w.write("CATEGORIES:" + escapeText(t.category()) + "\r\n");
        }

        // Status
        String icalStatus = resolveICalStatus(t);
        w.write("STATUS:" + icalStatus + "\r\n");

        ScheduleType sched = t.scheduleType() != null ? t.scheduleType() : ScheduleType.SINGLE;

        switch (sched) {
            case SINGLE -> writeSingleEvent(w, t);
            case RANGE  -> writeRangeEvent(w, t);
            case WEEKLY -> writeWeeklyEvent(w, t);
        }

        w.write("END:VEVENT\r\n");
    }

    // ── Tipos de agendamento ───────────────────────────────────────────────────

    private static void writeSingleEvent(Writer w, Task t) throws IOException {
        if (t.startTime() != null && !t.startTime().isBlank()) {
            // Evento com horário específico
            LocalDateTime start = parseDateTime(t.dueDate(), t.startTime());
            LocalDateTime end   = (t.endTime() != null && !t.endTime().isBlank())
                    ? parseDateTime(t.dueDate(), t.endTime())
                    : start.plusHours(1);
            w.write("DTSTART:" + start.format(DT_FMT) + "\r\n");
            w.write("DTEND:"   + end.format(DT_FMT)   + "\r\n");
        } else {
            // Evento de dia inteiro
            w.write("DTSTART;VALUE=DATE:" + t.dueDate().format(DATE_FMT) + "\r\n");
            w.write("DTEND;VALUE=DATE:"   + t.dueDate().plusDays(1).format(DATE_FMT) + "\r\n");
        }
    }

    private static void writeRangeEvent(Writer w, Task t) throws IOException {
        LocalDate end = t.endDate() != null ? t.endDate() : t.dueDate();
        if (t.startTime() != null && !t.startTime().isBlank()) {
            LocalDateTime start = parseDateTime(t.dueDate(), t.startTime());
            LocalDateTime endDt = (t.endTime() != null && !t.endTime().isBlank())
                    ? parseDateTime(end, t.endTime())
                    : parseDateTime(end, t.startTime());
            w.write("DTSTART:" + start.format(DT_FMT) + "\r\n");
            w.write("DTEND:"   + endDt.format(DT_FMT) + "\r\n");
        } else {
            w.write("DTSTART;VALUE=DATE:" + t.dueDate().format(DATE_FMT) + "\r\n");
            w.write("DTEND;VALUE=DATE:"   + end.plusDays(1).format(DATE_FMT) + "\r\n");
        }
    }

    private static void writeWeeklyEvent(Writer w, Task t) throws IOException {
        LocalDate endDate = t.endDate() != null ? t.endDate() : t.dueDate().plusMonths(1);

        if (t.startTime() != null && !t.startTime().isBlank()) {
            LocalDateTime start = parseDateTime(t.dueDate(), t.startTime());
            LocalDateTime end   = (t.endTime() != null && !t.endTime().isBlank())
                    ? parseDateTime(t.dueDate(), t.endTime())
                    : start.plusHours(1);
            w.write("DTSTART:" + start.format(DT_FMT) + "\r\n");
            w.write("DTEND:"   + end.format(DT_FMT)   + "\r\n");
        } else {
            w.write("DTSTART;VALUE=DATE:" + t.dueDate().format(DATE_FMT) + "\r\n");
            w.write("DTEND;VALUE=DATE:"   + t.dueDate().plusDays(1).format(DATE_FMT) + "\r\n");
        }

        // RRULE: recorrência semanal com dias específicos
        String rrule = buildWeeklyRRule(t.recurrenceDays(), endDate);
        w.write("RRULE:" + rrule + "\r\n");
    }

    // ── Builders de RRULE ─────────────────────────────────────────────────────

    /**
     * Converte o formato de dias da agenda (0=Dom, 1=Seg, ..., 6=Sáb)
     * para BYDAY do iCalendar (SU, MO, TU, WE, TH, FR, SA).
     */
    private static String buildWeeklyRRule(String recurrenceDays, LocalDate until) {
        String[] icalDays = {"SU","MO","TU","WE","TH","FR","SA"};
        StringBuilder byday = new StringBuilder();
        if (recurrenceDays != null && !recurrenceDays.isBlank()) {
            for (String d : recurrenceDays.split(",")) {
                try {
                    int idx = Integer.parseInt(d.trim());
                    if (idx >= 0 && idx < 7) {
                        if (!byday.isEmpty()) byday.append(",");
                        byday.append(icalDays[idx]);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        String untilStr = until.format(DATE_FMT) + "T235959Z";
        String rule = "FREQ=WEEKLY;UNTIL=" + untilStr;
        if (!byday.isEmpty()) rule += ";BYDAY=" + byday;
        return rule;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String resolveICalStatus(Task t) {
        if (t.status() == null) return "CONFIRMED";
        return switch (t.status()) {
            case CONCLUIDA   -> "COMPLETED";
            case CANCELADA   -> "CANCELLED";
            case BLOQUEADA   -> "TENTATIVE";
            default          -> "CONFIRMED";
        };
    }

    private static LocalDateTime parseDateTime(LocalDate date, String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int min  = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return date.atTime(hour, min);
    }

    private static String nowUtc() {
        return LocalDateTime.now(ZoneId.of("UTC")).format(DT_FMT) + "Z";
    }

    /** Escapa caracteres especiais iCalendar (vírgula, ponto-e-vírgula, barra invertida). */
    private static String escapeText(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    /**
     * RFC 5545 §3.1: linhas longas devem ser quebradas em 75 octetos (fold).
     * Simplificado para evitar dependências externas.
     */
    private static String foldLine(String value) {
        if (value.length() <= 70) return value;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (i > 0) sb.append("\r\n ");
            int end = Math.min(i + 73, value.length());
            sb.append(value, i, end);
            i = end;
        }
        return sb.toString();
    }
}

