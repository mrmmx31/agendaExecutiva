package com.pessoal.agenda.service;

import com.pessoal.agenda.model.InboxCapture;
import com.pessoal.agenda.model.InboxCaptureKind;
import com.pessoal.agenda.repository.InboxCaptureRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class InboxCaptureService {
    private final InboxCaptureRepository repository;
    private final Clock clock;

    public InboxCaptureService(InboxCaptureRepository repository) {
        this(repository, Clock.systemUTC());
    }

    InboxCaptureService(InboxCaptureRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public InboxCapture capture(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Escreva algo antes de salvar");
        }
        return repository.saveUnclassified(rawText, Instant.now(clock));
    }

    public List<InboxCapture> listUnclassified(int limit) {
        return repository.findUnclassified(limit);
    }

    public int countUnclassified() {
        return repository.countUnclassified();
    }

    public InboxCapture triageAsTask(long captureId, LocalDate dueDate) {
        Objects.requireNonNull(dueDate, "Data da tarefa e obrigatoria");
        InboxCapture capture = pendingCapture(captureId);
        return repository.triageToTask(
                captureId, titleFrom(capture.rawText()), capture.rawText(),
                dueDate, Instant.now(clock));
    }

    public InboxCapture triageAsIdea(long captureId) {
        InboxCapture capture = pendingCapture(captureId);
        return repository.triageToIdea(
                captureId, titleFrom(capture.rawText()), capture.rawText(), Instant.now(clock));
    }

    public InboxCapture triageAsInterruptionNote(long captureId) {
        pendingCapture(captureId);
        return repository.markTriaged(
                captureId, InboxCaptureKind.INTERRUPTION_NOTE,
                Instant.now(clock));
    }

    public InboxCapture archive(long captureId) {
        pendingCapture(captureId);
        return repository.markTriaged(
                captureId, InboxCaptureKind.ARCHIVED,
                Instant.now(clock));
    }

    private InboxCapture pendingCapture(long captureId) {
        if (captureId <= 0) throw new IllegalArgumentException("Captura invalida");
        InboxCapture capture = repository.findById(captureId)
                .orElseThrow(() -> new IllegalArgumentException("Captura nao encontrada"));
        if (capture.kind() != InboxCaptureKind.UNCLASSIFIED) {
            throw new IllegalStateException("Captura ja foi triada");
        }
        return capture;
    }

    static String titleFrom(String rawText) {
        return rawText.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Captura sem titulo"));
    }
}
