package com.pessoal.agenda.tools;

import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewVersion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleTasksReadOnlyAuditTest {

    @Test
    void reportsOnlyFieldsThatDiffer() {
        ReviewVersion local = new ReviewVersion(true, "Consulta", null,
                LocalDate.of(2026, 6, 5), true);
        ReviewVersion google = new ReviewVersion(true, "Consulta", null,
                LocalDate.of(2026, 6, 5), false);

        assertEquals(List.of("status"),
                GoogleTasksReadOnlyAudit.differences(local, google));
    }

    @Test
    void reportsUnavailableSideWithoutInventingFieldValues() {
        ReviewVersion local = new ReviewVersion(true, "Consulta", null,
                LocalDate.of(2026, 6, 5), true);
        ReviewVersion unavailable = new ReviewVersion(false, null, null, null, false);

        assertEquals(List.of("disponibilidade"),
                GoogleTasksReadOnlyAudit.differences(local, unavailable));
        assertTrue(GoogleTasksReadOnlyAudit.summarize(unavailable).contains("indisponível"));
    }

    @Test
    void preservesRicherLocalCancellationStatus() {
        ReviewVersion local = new ReviewVersion(true, "Consulta", null,
                LocalDate.of(2026, 6, 3), true, "Cancelada");
        ReviewVersion google = new ReviewVersion(true, "Consulta", null,
                LocalDate.of(2026, 6, 3), false, "Pendente");

        assertEquals(List.of("status"),
                GoogleTasksReadOnlyAudit.differences(local, google));
        assertTrue(GoogleTasksReadOnlyAudit.summarize(local).contains("status=cancelada"));
    }

    @Test
    void identifiesMappingsWhoseListIsMissingFromCurrentAccount() {
        assertEquals(Set.of("old-list"), GoogleTasksReadOnlyAudit.inaccessibleListIds(
                Set.of("current-list"), Set.of("current-list", "old-list")));
    }
}
