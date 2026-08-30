package com.pessoal.agenda.model;

import java.time.Instant;

public record TimerRecovery(long taskId, long elapsedSeconds,
                            boolean wasRunning, Instant updatedAt) {}
