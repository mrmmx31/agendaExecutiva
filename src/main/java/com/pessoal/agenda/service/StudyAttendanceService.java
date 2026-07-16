package com.pessoal.agenda.service;

import com.pessoal.agenda.model.AttendanceDay;
import com.pessoal.agenda.model.AttendanceDay.AttendanceStatus;
import com.pessoal.agenda.model.StudyPlanStatus;
import com.pessoal.agenda.model.StudyScheduleDay;
import com.pessoal.agenda.repository.StudyCompensationRepository;
import com.pessoal.agenda.repository.StudyEntryRepository;
import com.pessoal.agenda.repository.StudyScheduleRepository;
import com.pessoal.agenda.repository.StudyStatusLogRepository;
import com.pessoal.agenda.repository.StudyStatusLogRepository.StatusEntry;

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
 *   - Dia AUSENTE   : dia programado sem sessão alguma, no passado e com plano ativo
 *   - Dia COMPENSADO: ausente mas com compensação CONCLUIDA ou ABONADO registrada
 *   - Dia PAUSADO   : dia programado durante período em que o plano estava pausado
 *   - Dia FUTURO    : dia programado ainda no futuro
 *   - NAO_PROGRAMADO: dia não consta na grade semanal ou é anterior à data de ativação
 */
public class StudyAttendanceService {

    private final StudyScheduleRepository     scheduleRepo;
    private final StudyEntryRepository        entryRepo;
    private final StudyCompensationRepository compensationRepo;
    private final StudyStatusLogRepository    statusLogRepo;

    public StudyAttendanceService(StudyScheduleRepository scheduleRepo,
                                  StudyEntryRepository entryRepo,
                                  StudyCompensationRepository compensationRepo,
                                  StudyStatusLogRepository statusLogRepo) {
        this.scheduleRepo     = scheduleRepo;
        this.entryRepo        = entryRepo;
        this.compensationRepo = compensationRepo;
        this.statusLogRepo    = statusLogRepo;
    }

    // ── Calendário de frequência ───────────────────────────────────────────

    /**
     * Retorna um {@link AttendanceDay} para cada dia do intervalo [from, to].
     * Considera:
     *  - data de ativação (primeiro EM_ANDAMENTO no log) para não contar antes
     *  - intervalos de pausa para marcar dias como PAUSADO em vez de AUSENTE
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

        // Compensações concluídas OU abonadas
        Set<LocalDate> compensatedDates = compensationRepo.findByStudyId(studyPlanId).stream()
                .filter(c -> ("CONCLUIDA".equals(c.status()) || "ABONADO".equals(c.status()))
                          && c.missedDate() != null)
                .map(c -> c.missedDate())
                .collect(Collectors.toSet());

        // Histórico de status → ativação + intervalos de pausa
        List<StatusEntry> statusLog = statusLogRepo.findByPlanId(studyPlanId);
        LocalDate activationDate = resolveActivationDate(statusLog);
        List<LocalDate[]> pauseIntervals = buildPauseIntervals(statusLog);

        LocalDate today = LocalDate.now();
        List<AttendanceDay> result = new ArrayList<>();

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Integer minMin = schedule.get(d.getDayOfWeek());

            // Dia não programado na grade semanal
            if (minMin == null) {
                result.add(new AttendanceDay(d, AttendanceStatus.NAO_PROGRAMADO, 0,
                        actualByDate.getOrDefault(d, 0)));
                continue;
            }

            // Antes da data de ativação do plano → não conta como falta
            if (activationDate != null && d.isBefore(activationDate)) {
                result.add(new AttendanceDay(d, AttendanceStatus.NAO_PROGRAMADO, 0, 0));
                continue;
            }

            // Dentro de um período de pausa → mostra como PAUSADO (não é falta)
            if (isPausedOn(d, pauseIntervals)) {
                result.add(new AttendanceDay(d, AttendanceStatus.PAUSADO, minMin,
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
            // PAUSADO e NAO_PROGRAMADO e FUTURO não entram na contagem de dias programados
            if (day.status() == AttendanceStatus.NAO_PROGRAMADO
             || day.status() == AttendanceStatus.FUTURO
             || day.status() == AttendanceStatus.PAUSADO) continue;
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

    // ── Helpers privados ──────────────────────────────────────────────────

    /**
     * Determina a data de ativação do plano: primeira entrada com status EM_ANDAMENTO
     * no log de histórico. Retorna null se não houver log (fallback via startDate).
     */
    private static LocalDate resolveActivationDate(List<StatusEntry> log) {
        for (StatusEntry entry : log) {
            if (entry.status() == StudyPlanStatus.EM_ANDAMENTO) {
                return entry.changedAt();
            }
        }
        return null; // sem log → não filtra por data de ativação
    }

    /**
     * Constrói a lista de intervalos [início, fim] em que o plano estava PAUSADO.
     * Se o plano terminou o log ainda pausado, estende o intervalo até hoje.
     */
    private static List<LocalDate[]> buildPauseIntervals(List<StatusEntry> log) {
        List<LocalDate[]> intervals = new ArrayList<>();
        LocalDate pauseStart = null;
        for (StatusEntry entry : log) {
            if (entry.status() == StudyPlanStatus.PAUSADO && pauseStart == null) {
                pauseStart = entry.changedAt();
            } else if (entry.status() == StudyPlanStatus.EM_ANDAMENTO && pauseStart != null) {
                intervals.add(new LocalDate[]{ pauseStart, entry.changedAt().minusDays(1) });
                pauseStart = null;
            }
        }
        // Ainda pausado (sem retomada posterior)
        if (pauseStart != null) {
            intervals.add(new LocalDate[]{ pauseStart, LocalDate.now() });
        }
        return intervals;
    }

    private static boolean isPausedOn(LocalDate d, List<LocalDate[]> intervals) {
        for (LocalDate[] interval : intervals) {
            if (!d.isBefore(interval[0]) && !d.isAfter(interval[1])) return true;
        }
        return false;
    }
}
