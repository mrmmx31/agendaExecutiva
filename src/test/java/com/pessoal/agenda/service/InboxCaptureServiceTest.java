package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.InboxCapture;
import com.pessoal.agenda.model.InboxCaptureKind;
import com.pessoal.agenda.repository.InboxCaptureRepository;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import com.pessoal.agenda.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxCaptureServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-27T14:00:00Z");

    @TempDir
    Path tempDir;

    private Database database;
    private InboxCaptureRepository repository;
    private InboxCaptureService service;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        repository = new InboxCaptureRepository(database);
        service = serviceAt(NOW);
    }

    @Test
    void migrationIsIdempotentAndCreatesLookupIndex() {
        database.runMigrations();

        assertEquals(1, database.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='inbox_captures'"));
        assertEquals(1, database.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' "
                        + "AND name='idx_inbox_captures_kind_created'"));
    }

    @Test
    void capturesMultilineTextWithoutChangingIt() {
        String rawText = "  Ideia para o artigo\nVerificar os dados antes  ";

        InboxCapture saved = service.capture(rawText);
        InboxCapture restored = repository.findById(saved.id()).orElseThrow();

        assertEquals(rawText, restored.rawText());
        assertEquals(InboxCaptureKind.UNCLASSIFIED, restored.kind());
        assertEquals(NOW, restored.createdAt());
        assertNull(restored.triagedAt());
        assertNull(restored.targetId());
    }

    @Test
    void listsNewestUnclassifiedCapturesFirstAndHonorsLimit() {
        InboxCapture first = service.capture("Primeiro pensamento");
        InboxCapture second = serviceAt(NOW.plusSeconds(1)).capture("Segundo pensamento");
        serviceAt(NOW.plusSeconds(2)).capture("Terceiro pensamento");

        List<InboxCapture> captures = service.listUnclassified(2);

        assertEquals(List.of("Terceiro pensamento", "Segundo pensamento"),
                captures.stream().map(InboxCapture::rawText).toList());
        assertEquals(3, service.countUnclassified());
        assertTrue(repository.findById(first.id()).isPresent());
        assertTrue(repository.findById(second.id()).isPresent());
    }

    @Test
    void rejectsMissingTextAndInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> service.capture(null));
        assertThrows(IllegalArgumentException.class, () -> service.capture(" \n\t "));
        assertThrows(IllegalArgumentException.class, () -> service.listUnclassified(0));

        assertEquals(0, service.countUnclassified());
    }

    @Test
    void databaseRejectsInconsistentUnclassifiedRows() {
        assertThrows(RuntimeException.class, () -> database.execute("""
                INSERT INTO inbox_captures(raw_text, kind, created_at, triaged_at, target_id)
                VALUES(?, 'UNCLASSIFIED', ?, ?, ?)
                """, "Captura inconsistente", NOW.toString(), NOW.toString(), 10));

        assertEquals(0, service.countUnclassified());
    }

    @Test
    void triagesCaptureToTaskAtomicallyAndPreservesRawText() {
        String rawText = "  Preparar relatório\nConferir anexos antes de enviar  ";
        InboxCapture pending = service.capture(rawText);
        LocalDate dueDate = LocalDate.of(2026, 8, 29);

        InboxCapture triaged = service.triageAsTask(pending.id(), dueDate);
        var task = new TaskRepository(database).findById(triaged.targetId()).orElseThrow();

        assertEquals(InboxCaptureKind.TASK, triaged.kind());
        assertEquals(NOW, triaged.triagedAt());
        assertEquals("Preparar relatório", task.title());
        assertEquals(rawText, task.notes());
        assertEquals(dueDate, task.dueDate());
        assertEquals(0, service.countUnclassified());
    }

    @Test
    void triagesCaptureToIdeaWithFullDescription() {
        String rawText = "Hipótese sobre os dados\nComparar com a amostra anterior";
        InboxCapture pending = service.capture(rawText);

        InboxCapture triaged = service.triageAsIdea(pending.id());
        var idea = new ProjectIdeaRepository(database).findById(triaged.targetId()).orElseThrow();

        assertEquals(InboxCaptureKind.IDEA, triaged.kind());
        assertEquals("Hipótese sobre os dados", idea.title());
        assertEquals(rawText, idea.description());
        assertEquals("Caixa de entrada", idea.category());
        assertEquals("nova", idea.status());
    }

    @Test
    void triagesInterruptionAndArchiveWithoutCreatingTarget() {
        InboxCapture interruption = service.capture("Retomar pela tabela da página 4");
        InboxCapture archived = serviceAt(NOW.plusSeconds(1)).capture("Referência já processada");

        InboxCapture interruptionResult = service.triageAsInterruptionNote(interruption.id());
        InboxCapture archiveResult = serviceAt(NOW.plusSeconds(2)).archive(archived.id());

        assertEquals(InboxCaptureKind.INTERRUPTION_NOTE, interruptionResult.kind());
        assertNull(interruptionResult.targetId());
        assertEquals(InboxCaptureKind.ARCHIVED, archiveResult.kind());
        assertNull(archiveResult.targetId());
        assertEquals(0, service.countUnclassified());
    }

    @Test
    void repeatedTriageDoesNotDuplicateDestination() {
        InboxCapture pending = service.capture("Criar tarefa uma única vez");
        service.triageAsTask(pending.id(), LocalDate.of(2026, 8, 27));

        assertThrows(IllegalStateException.class,
                () -> service.triageAsTask(pending.id(), LocalDate.of(2026, 8, 27)));
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM tasks"));
    }

    @Test
    void destinationFailureKeepsCapturePending() {
        InboxCapture pending = service.capture("Não perder se a tarefa falhar");
        database.execute("DROP TABLE tasks");

        assertThrows(RuntimeException.class,
                () -> service.triageAsTask(pending.id(), LocalDate.of(2026, 8, 27)));

        InboxCapture restored = repository.findById(pending.id()).orElseThrow();
        assertEquals(InboxCaptureKind.UNCLASSIFIED, restored.kind());
        assertNull(restored.triagedAt());
        assertNull(restored.targetId());
    }

    private InboxCaptureService serviceAt(Instant instant) {
        return new InboxCaptureService(
                repository, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
