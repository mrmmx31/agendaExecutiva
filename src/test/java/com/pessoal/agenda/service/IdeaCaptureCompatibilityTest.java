package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.InboxCapture;
import com.pessoal.agenda.model.InboxCaptureKind;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.repository.IdeaChecklistRepository;
import com.pessoal.agenda.repository.InboxCaptureRepository;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeaCaptureCompatibilityTest {
    @TempDir
    Path tempDir;

    private Database database;
    private ProjectIdeaRepository ideaRepository;
    private IdeaChecklistRepository checklistRepository;
    private InboxCaptureService captureService;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("agenda-test.db"));
        database.runMigrations();
        ideaRepository = new ProjectIdeaRepository(database);
        checklistRepository = new IdeaChecklistRepository(database);
        captureService = new InboxCaptureService(new InboxCaptureRepository(database));
    }

    @Test
    void universalIdeaKeepsLegacyIdeasHierarchyAndChecklistUntouched() {
        long parentId = ideaRepository.saveFullIdea(new ProjectIdea(
                0, "Projeto legado", "Descrição principal com https://exemplo.test/ref",
                "em_execucao", "Pesquisa", "ALTA", "PESQUISA", "ALTO", 4, 32,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 12, 20),
                "Método preservado", "Revisar resultados", "legado, vínculo",
                "Referência completa", null));
        long childId = ideaRepository.saveFullIdea(new ProjectIdea(
                0, "Nota filha", "Conteúdo relacionado", "nova", "Caixa de entrada",
                "NORMAL", "GERAL", "MEDIO", 3, 0,
                LocalDate.of(2026, 6, 1), null, null, "Comparar com o projeto",
                "nota", null, parentId));
        var checklist = checklistRepository.addItem(parentId, "Validar amostra existente");
        checklistRepository.updateColumn(checklist.id(), "em_andamento");

        ProjectIdea parentBefore = ideaRepository.findById(parentId).orElseThrow();
        ProjectIdea childBefore = ideaRepository.findById(childId).orElseThrow();
        var checklistBefore = checklistRepository.findByIdeaId(parentId);

        InboxCapture pending = captureService.capture(
                "Nova hipótese universal\nConferir os mesmos dados");
        InboxCapture triaged = captureService.triageAsIdea(pending.id());
        database.runMigrations();

        assertEquals(parentBefore, ideaRepository.findById(parentId).orElseThrow());
        assertEquals(childBefore, ideaRepository.findById(childId).orElseThrow());
        assertEquals(checklistBefore, checklistRepository.findByIdeaId(parentId));
        assertEquals(parentId, ideaRepository.findById(childId).orElseThrow().parentIdeaId());

        ProjectIdea universalIdea = ideaRepository.findById(triaged.targetId()).orElseThrow();
        assertEquals("Nova hipótese universal", universalIdea.title());
        assertEquals("Nova hipótese universal\nConferir os mesmos dados",
                universalIdea.description());
        assertEquals(InboxCaptureKind.IDEA, triaged.kind());
        assertEquals(universalIdea.id(), triaged.targetId());

        List<Long> reviewIds = ideaRepository.findInboxIdeas(200).stream()
                .map(ProjectIdea::id).toList();
        assertTrue(reviewIds.contains(childId));
        assertTrue(reviewIds.contains(universalIdea.id()));
        assertTrue(ideaRepository.findAll().stream().anyMatch(idea -> idea.id() == parentId));
    }
}
