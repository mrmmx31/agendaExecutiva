package com.pessoal.agenda.tools;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository.SyncState;
import com.pessoal.agenda.repository.GoogleTasksSyncRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleTasksService;
import com.pessoal.agenda.service.GoogleTasksSyncService;
import com.pessoal.agenda.service.GoogleTasksSyncService.Resolution;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewDetails;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolve conflitos explicitamente selecionados após uma pré-validação completa. */
public final class GoogleTasksConflictResolver {

    private static final String ARGUMENT_PREFIX = "--use-local=";

    private GoogleTasksConflictResolver() {}

    public static void main(String[] args) throws Exception {
        List<Long> mappingIds = parseMappingIds(args);
        GoogleAuthService auth = GoogleAuthService.getInstance();
        if (!auth.isAuthorized()) {
            throw new IllegalStateException("A conta Google não está conectada.");
        }

        Database database = new Database();
        TaskRepository tasks = new TaskRepository(database);
        GoogleTasksMappingRepository mappings = new GoogleTasksMappingRepository(database);
        GoogleTasksSyncService sync = new GoogleTasksSyncService(
                new GoogleTasksService(), tasks, mappings,
                new GoogleTasksSyncRepository(database));

        Set<String> listIds = new LinkedHashSet<>();
        for (long mappingId : mappingIds) {
            var mapping = mappings.findById(mappingId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Vínculo não encontrado: " + mappingId));
            if (mapping.syncState() != SyncState.CONFLICT) {
                throw new IllegalStateException(
                        "O vínculo " + mappingId + " não está mais em conflito.");
            }
            listIds.add(mapping.googleListId());
        }

        Map<Long, ReviewDetails> detailsById = new LinkedHashMap<>();
        for (String listId : listIds) {
            for (ReviewDetails details : sync.loadReviewDetails(listId)) {
                detailsById.put(details.item().mappingId(), details);
            }
        }
        List<ReviewDetails> approved = validateStatusOnlyConflicts(mappingIds, detailsById);

        System.out.println("PRÉ-VALIDAÇÃO CONCLUÍDA");
        for (ReviewDetails details : approved) {
            System.out.printf("  %d | preservar local | %s%n",
                    details.item().mappingId(), details.item().title());
        }
        for (ReviewDetails details : approved) {
            sync.resolveReview(details.item().mappingId(), Resolution.USE_LOCAL);
            System.out.printf("RESOLVIDO %d | %s%n",
                    details.item().mappingId(), details.item().title());
        }
    }

    static List<Long> parseMappingIds(String[] args) {
        if (args.length != 1 || !args[0].startsWith(ARGUMENT_PREFIX)) {
            throw new IllegalArgumentException("Uso: --use-local=<id,id,...>");
        }
        String value = args[0].substring(ARGUMENT_PREFIX.length()).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Informe ao menos um ID.");

        Set<Long> ids = new LinkedHashSet<>();
        for (String rawId : value.split(",")) {
            long id;
            try {
                id = Long.parseLong(rawId.trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("ID inválido: " + rawId, error);
            }
            if (id <= 0 || !ids.add(id)) {
                throw new IllegalArgumentException("ID inválido ou repetido: " + id);
            }
        }
        return List.copyOf(ids);
    }

    static List<ReviewDetails> validateStatusOnlyConflicts(
            List<Long> mappingIds, Map<Long, ReviewDetails> detailsById) {
        List<ReviewDetails> approved = new ArrayList<>();
        for (long mappingId : mappingIds) {
            ReviewDetails details = detailsById.get(mappingId);
            if (details == null) {
                throw new IllegalStateException(
                        "O vínculo " + mappingId + " não está disponível para revisão.");
            }
            if (details.item().state() != SyncState.CONFLICT) {
                throw new IllegalStateException(
                        "O vínculo " + mappingId + " não está mais em conflito.");
            }
            if (!details.local().available() || !details.google().available()) {
                throw new IllegalStateException(
                        "Um dos lados do vínculo " + mappingId + " não está disponível.");
            }
            List<String> differences = GoogleTasksReadOnlyAudit.differences(
                    details.local(), details.google());
            if (!differences.equals(List.of("status"))) {
                throw new IllegalStateException("O vínculo " + mappingId
                        + " possui diferenças não autorizadas: " + differences);
            }
            approved.add(details);
        }
        return List.copyOf(approved);
    }
}
