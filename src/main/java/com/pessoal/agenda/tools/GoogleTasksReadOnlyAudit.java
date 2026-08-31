package com.pessoal.agenda.tools;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.GoogleTasksMappingRepository;
import com.pessoal.agenda.repository.GoogleTasksSyncRepository;
import com.pessoal.agenda.repository.TaskRepository;
import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleTasksService;
import com.pessoal.agenda.service.GoogleTasksSyncService;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewDetails;
import com.pessoal.agenda.service.GoogleTasksSyncService.ReviewVersion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Auditoria Google Tasks estritamente limitada a leituras da API e do SQLite. */
public final class GoogleTasksReadOnlyAudit {

    private GoogleTasksReadOnlyAudit() {}

    public static void main(String[] args) throws Exception {
        GoogleAuthService auth = GoogleAuthService.getInstance();
        if (!auth.isAuthorized()) {
            throw new IllegalStateException("A conta Google não está conectada.");
        }

        Database database = new Database();
        TaskRepository tasks = new TaskRepository(database);
        GoogleTasksMappingRepository mappings = new GoogleTasksMappingRepository(database);
        GoogleTasksService google = new GoogleTasksService();
        GoogleTasksSyncService sync = new GoogleTasksSyncService(
                google, tasks, mappings, new GoogleTasksSyncRepository(database));

        System.out.println("AUDITORIA SOMENTE LEITURA");
        List<GoogleTasksService.TaskList> googleLists = google.listTaskLists();
        Set<String> accessibleListIds = new LinkedHashSet<>();
        for (GoogleTasksService.TaskList list : googleLists) {
            accessibleListIds.add(list.id());
            int remoteCount = google.listTasksForSync(list.id()).stream()
                    .filter(task -> !task.deleted())
                    .toList().size();
            int mappedCount = mappings.findByListId(list.id()).size();
            List<ReviewDetails> reviews = sync.loadReviewDetails(list.id());
            System.out.printf("LISTA %s | Google=%d | vínculos=%d | revisões=%d%n",
                    list.title(), remoteCount, mappedCount, reviews.size());
            for (ReviewDetails review : reviews) {
                System.out.printf("  %s | %s | diferenças: %s%n",
                        review.item().state(), review.item().title(),
                        String.join(", ", differences(review.local(), review.google())));
                System.out.println("    local : " + summarize(review.local()));
                System.out.println("    Google: " + summarize(review.google()));
            }
        }
        for (String inaccessibleListId : inaccessibleListIds(
                accessibleListIds, mappedListIds(database))) {
            System.out.printf("LISTA INACESSÍVEL NA CONTA ATUAL | vínculos=%d%n",
                    mappings.findByListId(inaccessibleListId).size());
        }
    }

    static Set<String> inaccessibleListIds(Set<String> accessible, Set<String> mapped) {
        Set<String> inaccessible = new LinkedHashSet<>(mapped);
        inaccessible.removeAll(accessible);
        return Set.copyOf(inaccessible);
    }

    private static Set<String> mappedListIds(Database database) throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        try (var connection = database.connect();
             var statement = connection.prepareStatement(
                     "SELECT DISTINCT google_list_id FROM google_tasks_mapping ORDER BY google_list_id");
             var rows = statement.executeQuery()) {
            while (rows.next()) ids.add(rows.getString(1));
        }
        return Set.copyOf(ids);
    }

    public static List<String> differences(ReviewVersion local, ReviewVersion google) {
        List<String> differences = new ArrayList<>();
        if (local == null || google == null || !local.available() || !google.available()) {
            differences.add("disponibilidade");
            return List.copyOf(differences);
        }
        if (!Objects.equals(local.title(), google.title())) differences.add("título");
        if (!Objects.equals(local.notes(), google.notes())) differences.add("notas");
        if (!Objects.equals(local.dueDate(), google.dueDate())) differences.add("data");
        if (local.completed() != google.completed()
                || !Objects.equals(local.statusLabel(), google.statusLabel())) {
            differences.add("status");
        }
        if (differences.isEmpty()) differences.add("nenhuma");
        return List.copyOf(differences);
    }

    static String summarize(ReviewVersion version) {
        if (version == null || !version.available()) return "indisponível";
        return "título='" + version.title() + "'"
                + ", status=" + version.statusLabel().toLowerCase()
                + ", data=" + (version.dueDate() != null ? version.dueDate() : "sem data")
                + ", notas=" + (version.notes() != null ? "presentes" : "ausentes");
    }
}
