package com.pessoal.agenda.tools;

import com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewDetails;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewItem;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewVersion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleTasksConflictResolverTest {

    @Test
    void parsesExplicitUniquePositiveIds() {
        assertEquals(List.of(14L, 61L, 91L),
                GoogleTasksConflictResolver.parseMappingIds(
                        new String[]{"--use-local=14,61,91"}));
        assertThrows(IllegalArgumentException.class,
                () -> GoogleTasksConflictResolver.parseMappingIds(
                        new String[]{"--use-local=14,14"}));
    }

    @Test
    void approvesOnlyConflictsWhoseSoleDifferenceIsStatus() {
        ReviewDetails details = details(14, "Consulta", "Consulta", true, false);

        assertEquals(List.of(details),
                GoogleTasksConflictResolver.validateStatusOnlyConflicts(
                        List.of(14L), Map.of(14L, details)));
    }

    @Test
    void rejectsAnyUnapprovedFieldDifferenceBeforeResolution() {
        ReviewDetails details = details(14, "Consulta local", "Consulta Google", true, false);

        assertThrows(IllegalStateException.class,
                () -> GoogleTasksConflictResolver.validateStatusOnlyConflicts(
                        List.of(14L), Map.of(14L, details)));
    }

    private static ReviewDetails details(long id, String localTitle, String googleTitle,
                                         boolean localDone, boolean googleDone) {
        LocalDate dueDate = LocalDate.of(2026, 8, 31);
        return new ReviewDetails(
                new ReviewItem(id, SyncState.CONFLICT, localTitle, "google-" + id),
                new ReviewVersion(true, localTitle, null, dueDate, localDone),
                new ReviewVersion(true, googleTitle, null, dueDate, googleDone));
    }
}
