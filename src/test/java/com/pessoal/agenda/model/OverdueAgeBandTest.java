package com.pessoal.agenda.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverdueAgeBandTest {
    @Test
    void classifiesBoundaryDaysIntoNeutralReviewBands() {
        assertEquals(OverdueAgeBand.UP_TO_7_DAYS, OverdueAgeBand.fromPendingDays(1));
        assertEquals(OverdueAgeBand.UP_TO_7_DAYS, OverdueAgeBand.fromPendingDays(7));
        assertEquals(OverdueAgeBand.DAYS_8_TO_30, OverdueAgeBand.fromPendingDays(8));
        assertEquals(OverdueAgeBand.DAYS_8_TO_30, OverdueAgeBand.fromPendingDays(30));
        assertEquals(OverdueAgeBand.OVER_30_DAYS, OverdueAgeBand.fromPendingDays(31));
    }

    @Test
    void rejectsTasksThatAreNotPendingYet() {
        assertThrows(IllegalArgumentException.class,
                () -> OverdueAgeBand.fromPendingDays(0));
    }
}
