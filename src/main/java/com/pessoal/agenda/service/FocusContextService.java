package com.pessoal.agenda.service;

import com.pessoal.agenda.model.FocusContext;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.FocusContextRepository;
import com.pessoal.agenda.repository.TaskRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class FocusContextService {
    private final FocusContextRepository repository;
    private final TaskRepository taskRepository;
    private final Clock clock;

    public FocusContextService(FocusContextRepository repository,
                               TaskRepository taskRepository) {
        this(repository, taskRepository, Clock.systemUTC());
    }

    FocusContextService(FocusContextRepository repository,
                        TaskRepository taskRepository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public FocusContext interrupt(long taskId, String resumeNote) {
        String normalizedNote = normalizeNote(resumeNote);
        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Tarefa não encontrada"));
        if (task.done() || task.status() == TaskStatus.CONCLUIDA
                || task.status() == TaskStatus.CANCELADA) {
            throw new IllegalStateException("A pista exige uma tarefa aberta");
        }
        Instant now = clock.instant();
        FocusContext context = new FocusContext(taskId, normalizedNote, now, now);
        repository.replaceCurrent(context);
        return context;
    }

    public Optional<FocusContext> current() {
        return repository.findCurrent().filter(context ->
                taskRepository.findById(context.taskId())
                        .filter(task -> !task.done()
                                && task.status() != TaskStatus.CONCLUIDA
                                && task.status() != TaskStatus.CANCELADA)
                        .isPresent());
    }

    public Optional<FocusContext> completeResume(long taskId) {
        Optional<FocusContext> current = current();
        if (current.isEmpty() || current.get().taskId() != taskId) return Optional.empty();
        repository.clearCurrent(taskId);
        return current;
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Informe onde parou ou o próximo passo");
        }
        return note.strip();
    }
}
