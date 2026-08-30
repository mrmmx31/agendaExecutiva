package com.pessoal.agenda.model;

import java.time.Instant;

public record FocusContext(long taskId, String resumeNote,
                           Instant interruptedAt, Instant updatedAt) {}
