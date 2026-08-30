package com.pessoal.agenda.ui.view;

import java.util.concurrent.atomic.AtomicBoolean;

final class GoogleOperationGuard {
    private final AtomicBoolean running = new AtomicBoolean();

    boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    void finish() {
        running.set(false);
    }

    boolean isRunning() {
        return running.get();
    }
}
