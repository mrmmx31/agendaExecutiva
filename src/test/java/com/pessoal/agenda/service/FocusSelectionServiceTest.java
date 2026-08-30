package com.pessoal.agenda.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FocusSelectionServiceTest {
    private final FocusSelectionService service = new FocusSelectionService();

    @Test
    void followsTimerManualPlanAutomaticPrecedence() {
        Set<Long> available = Set.of(1L, 2L, 3L, 4L);

        assertSelection(1, FocusSelectionService.Origin.TIMER,
                service.select(1L, 2L, 3L, List.of(4L), available));
        assertSelection(2, FocusSelectionService.Origin.MANUAL,
                service.select(null, 2L, 3L, List.of(4L), available));
        assertSelection(3, FocusSelectionService.Origin.DAILY_PLAN,
                service.select(null, null, 3L, List.of(4L), available));
        assertSelection(4, FocusSelectionService.Origin.AUTOMATIC,
                service.select(null, null, null, List.of(4L), available));
    }

    @Test
    void skipsUnavailableOriginsAndKeepsAutomaticOrder() {
        assertSelection(8, FocusSelectionService.Origin.AUTOMATIC,
                service.select(1L, 2L, 3L, List.of(9L, 8L, 7L), Set.of(7L, 8L)));
    }

    private static void assertSelection(long taskId, FocusSelectionService.Origin origin,
                                        java.util.Optional<FocusSelectionService.Selection> selection) {
        FocusSelectionService.Selection resolved = selection.orElseThrow();
        assertEquals(taskId, resolved.taskId());
        assertEquals(origin, resolved.origin());
    }
}
