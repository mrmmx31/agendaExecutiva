package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.service.GoogleTasksService.SyncResult;
import com.pessoal.agenda.service.GoogleTasksSyncService.Resolution;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewItem;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewVersion;
import com.pessoal.agenda.service.GoogleTasksSyncService.SyncPreview;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTasksSyncWindowTest {

    @Test
    void summaryReportsProcessedReviewAndErrorCounts() {
        SyncResult result = new SyncResult(1, 2, 3, 4,
                5, 6, 7, 120, 80, 1, List.of());

        String summary = GoogleTasksSyncWindow.formatSyncSummary(result);

        assertTrue(summary.contains("120 Google + 80 local verificados"));
        assertTrue(summary.contains("7 para revisão"));
        assertTrue(summary.contains("1 erro(s)"));
    }

    @Test
    void previewReportsEveryActionBeforeApplication() {
        SyncPreview preview = new SyncPreview(1, 2, 3, 4,
                5, 6, 7, 120, 80, List.of("Criar local: Exemplo"));

        String summary = GoogleTasksSyncWindow.formatPreviewSummary(preview);

        assertTrue(summary.contains("1 criar local"));
        assertTrue(summary.contains("4 atualizar Google"));
        assertTrue(summary.contains("7 revisar"));
    }

    @Test
    void reviewLabelsStateTheConcreteConsequence() {
        assertEquals("Recriar no Google", GoogleTasksSyncWindow.resolutionLabel(
                SyncState.REMOTE_DELETED, Resolution.USE_LOCAL));
        assertEquals("Excluir também a tarefa local", GoogleTasksSyncWindow.resolutionLabel(
                SyncState.REMOTE_DELETED, Resolution.USE_GOOGLE));
        assertTrue(GoogleTasksSyncWindow.resolutionConsequence(
                SyncState.LOCAL_DELETED, Resolution.USE_GOOGLE).contains("importada novamente"));
    }

    @Test
    void reviewVersionShowsEveryFieldNeededForDecision() {
        ReviewVersion version = new ReviewVersion(true, "Título Google", "Notas Google",
                LocalDate.of(2026, 8, 30), true);

        String comparison = GoogleTasksSyncWindow.formatReviewVersion(version, "Indisponível");

        assertTrue(comparison.contains("Título: Título Google"));
        assertTrue(comparison.contains("Status: Concluída"));
        assertTrue(comparison.contains("Data: 30/08/2026"));
        assertTrue(comparison.contains("Notas: Notas Google"));
        assertEquals("Indisponível",
                GoogleTasksSyncWindow.formatReviewVersion(null, "Indisponível"));
    }

    @Test
    void reviewConfirmationRepeatsItemChoiceAndIrreversibleConsequence() {
        ReviewItem item = new ReviewItem(12, SyncState.CONFLICT,
                "Ler Capítulo 7", "google-12");

        String confirmation = GoogleTasksSyncWindow.reviewConfirmationText(
                item, Resolution.USE_LOCAL);

        assertTrue(confirmation.contains("Item: Ler Capítulo 7"));
        assertTrue(confirmation.contains("Usar versão local"));
        assertTrue(confirmation.contains("locais substituirão a versão Google"));
        assertTrue(confirmation.contains("não possui desfazer automático"));
    }
}
