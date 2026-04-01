package com.pessoal.agenda.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Serviço singleton para controlar o timer de tarefa ativa. */
public class TaskTimerService {
    private static TaskTimerService instance;
    private Long activeTaskId = null;
    private final AtomicLong elapsedSeconds = new AtomicLong(0);
    private boolean running = false;
    private ScheduledExecutorService exec;
    private ScheduledFuture<?> timerFuture;
    // Suporte a múltiplos tick listeners
    private final java.util.List<Consumer<Long>> tickListeners = new java.util.ArrayList<>();
    private final java.util.List<Runnable> stateListeners = new java.util.ArrayList<>();

    private TaskTimerService() {
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "task-timer-service");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized TaskTimerService get() {
        if (instance == null) instance = new TaskTimerService();
        return instance;
    }

    public synchronized void start(long taskId) {
        if (activeTaskId != null && activeTaskId != taskId) stop();
        activeTaskId = taskId;
        running = true;
        if (timerFuture == null || timerFuture.isDone()) {
            timerFuture = exec.scheduleAtFixedRate(() -> {
                long s = elapsedSeconds.incrementAndGet();
                java.util.List<Consumer<Long>> copy;
                synchronized (tickListeners) { copy = new java.util.ArrayList<>(tickListeners); }
                for (Consumer<Long> l : copy) {
                    try { l.accept(s); } catch (Exception ignored) {}
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
        notifyStateListeners();
    }

    public synchronized void pause() {
        running = false;
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
        notifyStateListeners();
    }

    public synchronized void resume() {
        if (activeTaskId == null) return;
        if (running) return;
        running = true;
        if (timerFuture == null || timerFuture.isDone()) {
            timerFuture = exec.scheduleAtFixedRate(() -> {
                long s = elapsedSeconds.incrementAndGet();
                java.util.List<Consumer<Long>> copy;
                synchronized (tickListeners) { copy = new java.util.ArrayList<>(tickListeners); }
                for (Consumer<Long> l : copy) {
                    try { l.accept(s); } catch (Exception ignored) {}
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
        notifyStateListeners();
    }

    public synchronized void stop() {
        running = false;
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
        activeTaskId = null;
        elapsedSeconds.set(0);
        notifyStateListeners();
    }

    public synchronized void reset() {
        elapsedSeconds.set(0);
        java.util.List<Consumer<Long>> copy;
        synchronized (tickListeners) { copy = new java.util.ArrayList<>(tickListeners); }
        for (Consumer<Long> l : copy) {
            try { l.accept(0L); } catch (Exception ignored) {}
        }
        notifyStateListeners();
    }
    public void addStateListener(Runnable listener) {
        synchronized (stateListeners) { stateListeners.add(listener); }
    }

    public void removeStateListener(Runnable listener) {
        synchronized (stateListeners) { stateListeners.remove(listener); }
    }

    private void notifyStateListeners() {
        java.util.List<Runnable> copy;
        synchronized (stateListeners) { copy = new java.util.ArrayList<>(stateListeners); }
        for (Runnable r : copy) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    public Long getActiveTaskId() { return activeTaskId; }
    public boolean isRunning() { return running; }
    public long getElapsedSeconds() { return elapsedSeconds.get(); }

    // Novo: permite múltiplos listeners
    public void addTickListener(Consumer<Long> listener) {
        synchronized (tickListeners) { tickListeners.add(listener); }
    }
    public void removeTickListener(Consumer<Long> listener) {
        synchronized (tickListeners) { tickListeners.remove(listener); }
    }
}
