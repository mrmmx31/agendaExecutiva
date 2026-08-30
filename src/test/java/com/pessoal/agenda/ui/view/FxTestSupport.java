package com.pessoal.agenda.ui.view;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class FxTestSupport {
    private static boolean started;

    private FxTestSupport() {}

    public static synchronized void startToolkit() throws Exception {
        if (started) return;

        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            latch.countDown();
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX toolkit did not start within 10 seconds.");
        }
        started = true;
    }

    public static <T> T call(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    public static void run(ThrowingRunnable action) throws Exception {
        call(() -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
