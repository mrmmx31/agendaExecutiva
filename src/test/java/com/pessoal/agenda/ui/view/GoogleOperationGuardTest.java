package com.pessoal.agenda.ui.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleOperationGuardTest {
    @Test
    void allowsOnlyOneOperationUntilFinished() {
        GoogleOperationGuard guard = new GoogleOperationGuard();

        assertTrue(guard.tryStart());
        assertTrue(guard.isRunning());
        assertFalse(guard.tryStart());

        guard.finish();
        assertFalse(guard.isRunning());
        assertTrue(guard.tryStart());
    }
}
