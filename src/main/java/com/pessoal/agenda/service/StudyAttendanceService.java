package com.pessoal.agenda.service;

import com.pessoal.agenda.model.AttendanceDay;
import com.pessoal.agenda.model.AttendanceDay.AttendanceStatus;
import com.pessoal.agenda.model.StudyScheduleDay;
import com.pessoal.agenda.repository.StudyCompensationRepository;
import com.pessoal.agenda.repository.StudyEntryRepository;
import com.pessoal.agenda.repository.StudyScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calcula frequência, ausências e estatísticas de estudo para um plano.
 *
 * Regras:
 *   - Dia PRESENTE  : houve sessão com duração >= minutos programados
 *   - Dia PARCIAL   : houve sessão mas duração < mínimo
 *   - Dia AUSENTE   : dia programado sem sessão alguma, no passado
 *   - Dia COMPENSADO: ausente mas com compensação CONCLUIDA registrada
 *   - Dia FUTURO    : dia programado ainda no futuro
 *   - NAO_PROGRAMADO: dia não consta na grade semanal
 */
public class StudyAttendanceService {

    private final StudyScheduleRepository     scheduleRepo;
    private final StudyEntryRepository        entryRepo;
    private final StudyCompensationRepository compensationRepo;

    public StudyAttendanceService(StudyScheduleRepository scheduleRepo,
                                  StudyEntryRepository entryRepo,
                                  StudyCompensationRepository compensationRepo) {
        this.scheduleRepo     = scheduleRepo;
        this.entryRepo        = entryRepo;
        this.compensationRepo = compensationRepo;
    }

    // ── Calendário de frequência ───────────────────────────────────────────

    /**
     * Retorna um {@link AttendanceDay} para cada dia do intervalo [from, to].
     */
    public List<AttendanceDay> getCalendar(long studyPlanId, LocalDate from, LocalDate to) {
        // Grade semanal → mapa dayOfWeek → minMinutes
        Map<DayOfWeek, Integer> schedule = scheduleRepo.findByStudyId(studyPlanId).stream()
                .collect(Collectors.toMap(StudyScheduleDay::dayOfWeek, StudyScheduleDay::minMinutes));

        // Minutos estudados por data
        Map<LocalDate, Integer> actualByDate = entryRepo.findByStudyId(studyPlanId).stream()
                .collect(Collectors.groupingBy(
                        e -> e.entryDate(),
                        Collectors.summingInt(e -> e.durationMinutes())));

        // Compensações concluídas
        Set<LocalDate> compensatedDates = compensationRepo.findByStudyId(studyPlanId).stream()
                .filter(c -> "CONCLUIDA".equals(c.status()) && c.missedDate() != null)
                .map(c -> c.missedDate())
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now();
        List<AttendanceDay> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Integer minMin = schedule.get(d.getDayOfWeek());
            if (minMin == null) {
                result.add(new AttendanceDay(d, AttendanceStatus.NAO_PROGRAMADO, 0,
                        actualByDate.getOrDefault(d, 0)));
                continue;
            }
            int actual = actualByDate.getOrDefault(d, 0);
            AttendanceStatus status;
            if (d.isAfter(today)) {
                status = AttendanceStatus.FUTURO;
            } else if (compensatedDates.contains(d)) {
                status = AttendanceStatus.COMPENSADO;
            } else if (actual >= minMin) {
                status = AttendanceStatus.PRESENTE;
            } else if (actual > 0) {
                status = AttendanceStatus.PARCIAL;
            } else {
                status = AttendanceStatus.AUSENTE;
            }
            result.add(new AttendanceDay(d, status, minMin, actual));
        }
        return result;
    }

    // ── Estatísticas ───────────────────────────────────────────────────────

    public record Summary(
        int scheduledDays,
        int presentDays,
        int partialDays,
        int absentDays,
        int compensatedDays,
        int totalScheduledMinutes,
        int totalActualMinutes,
        int deficitMinutes
    ) {
        public double presenceRate() {
            int attended = presentDays + partialDays + compensatedDays;
            return scheduledDays == 0 ? 100.0 : attended * 100.0 / scheduledDays;
        }
    }

    public Summary getSummary(long studyPlanId, LocalDate from, LocalDate to) {
        List<AttendanceDay> calendar = getCalendar(studyPlanId, from, to);
        int scheduled = 0, present = 0, partial = 0, absent = 0, comp = 0;
        int totalSched = 0, totalActual = 0;
        for (AttendanceDay day : calendar) {
            if (day.status() == AttendanceStatus.NAO_PROGRAMADO
             || day.status() == AttendanceStatus.FUTURO) continue;
            scheduled++;
            totalSched  += day.scheduledMinutes();
            totalActual += day.actualMinutes();
            switch (day.status()) {
                case PRESENTE    -> present++;
                case PARCIAL     -> partial++;
                case AUSENTE     -> absent++;
                case COMPENSADO  -> comp++;
                default -> {}
            }
        }
        int deficit = Math.max(0, totalSched - totalActual);
        return new Summary(scheduled, present, partial, absent, comp,
                totalSched, totalActual, deficit);
    }

    // ── Ausências detectadas (sem compensação pendente já existente) ───────

    /**
     * Retorna datas ausentes no passado que ainda NÃO têm compensação registrada.
     * Útil para propor automaticamente compensações ao usuário.
     */
    public List<LocalDate> getUnregisteredAbsences(long studyPlanId, LocalDate from, LocalDate to) {
        return getCalendar(studyPlanId, from, to).stream()
                .filter(d -> d.status() == AttendanceStatus.AUSENTE
                          || d.status() == AttendanceStatus.PARCIAL)
                .map(AttendanceDay::date)
                .filter(d -> !compensationRepo.existsForDate(studyPlanId, d))
                .collect(Collectors.toList());
    }
}

