package com.pessoal.agenda.service;

import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.model.TimerRecovery;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.repository.TimerRecoveryRepository;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class TaskTimerRecoveryService {
    private static final long CHECKPOINT_INTERVAL_SECONDS = 5;

    public record Candidate(Task task, TimerRecovery recovery) {}

    private final TimerRecoveryRepository repository;
    private final TaskRepository taskRepository;
    private final TaskTimerService timerService;
    private final Clock clock;
    private final Consumer<Long> tickListener = this::onTick;
    private final Runnable stateListener = this::checkpointNow;
    private boolean tracking;
    private boolean suppressPersistence;
    private long lastCheckpointSeconds = -CHECKPOINT_INTERVAL_SECONDS;

    public TaskTimerRecoveryService(TimerRecoveryRepository repository,
                                    TaskRepository taskRepository) {
        this(repository, taskRepository, TaskTimerService.get(), Clock.systemUTC());
    }

    TaskTimerRecoveryService(TimerRecoveryRepository repository,
                             TaskRepository taskRepository,
                             TaskTimerService timerService, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.timerService = Objects.requireNonNull(timerService);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized Optional<Candidate> pending() {
        Optional<TimerRecovery> stored = repository.findPending();
        if (stored.isEmpty()) return Optional.empty();
        TimerRecovery recovery = stored.get();
        Optional<Task> task = taskRepository.findById(recovery.taskId())
                .filter(TaskTimerRecoveryService::isOpen)
                .filter(ignored -> recovery.elapsedSeconds() > 0);
        if (task.isEmpty()) {
            repository.clear();
            return Optional.empty();
        }
        return Optional.of(new Candidate(task.get(), recovery));
    }

    public synchronized Candidate recover() {
        Candidate candidate = pending()
                .orElseThrow(() -> new IllegalStateException("Não há timer para recuperar"));
        suppressPersistence = true;
        try {
            timerService.restore(candidate.task().id(),
                    candidate.recovery().elapsedSeconds(), false);
            TimerRecovery restored = new TimerRecovery(
                    candidate.task().id(), candidate.recovery().elapsedSeconds(),
                    false, clock.instant());
            repository.save(restored);
            lastCheckpointSeconds = restored.elapsedSeconds();
            return new Candidate(candidate.task(), restored);
        } finally {
            suppressPersistence = false;
        }
    }

    public synchronized void discard() {
        suppressPersistence = true;
        try {
            timerService.stop();
            repository.clear();
            lastCheckpointSeconds = -CHECKPOINT_INTERVAL_SECONDS;
        } finally {
            suppressPersistence = false;
        }
    }

    public synchronized void startTracking() {
        if (tracking) return;
        tracking = true;
        timerService.addTickListener(tickListener);
        timerService.addStateListener(stateListener);
    }

    public synchronized void stopTracking() {
        if (!tracking) return;
        checkpointNow();
        timerService.removeTickListener(tickListener);
        timerService.removeStateListener(stateListener);
        tracking = false;
    }

    public synchronized void checkpointNow() {
        if (suppressPersistence) return;
        Long taskId = timerService.getActiveTaskId();
        long elapsedSeconds = timerService.getElapsedSeconds();
        if (taskId == null || elapsedSeconds <= 0) {
            repository.clear();
            lastCheckpointSeconds = -CHECKPOINT_INTERVAL_SECONDS;
            return;
        }
        repository.save(new TimerRecovery(taskId, elapsedSeconds,
                timerService.isRunning(), clock.instant()));
        lastCheckpointSeconds = elapsedSeconds;
    }

    private synchronized void onTick(long elapsedSeconds) {
        if (!tracking || suppressPersistence) return;
        if (elapsedSeconds - lastCheckpointSeconds >= CHECKPOINT_INTERVAL_SECONDS) {
            checkpointNow();
        }
    }

    private static boolean isOpen(Task task) {
        return !task.done() && task.status() != TaskStatus.CONCLUIDA
                && task.status() != TaskStatus.CANCELADA;
    }
}
