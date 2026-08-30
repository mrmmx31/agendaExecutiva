package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositório da tabela de mapeamento entre tarefas locais e Google Tasks.
 *
 * Tabela: google_tasks_mapping
 *   local_task_id  — FK para tasks.id
 *   google_list_id — ID da lista no Google Tasks
 *   google_task_id — ID da tarefa no Google Tasks
 *   last_synced_at — ISO-8601
 */
public class GoogleTasksMappingRepository {

    public enum SyncState { ACTIVE, REMOTE_DELETED, LOCAL_DELETED, CONFLICT }

    public record TaskMapping(long id, long localTaskId, String googleListId,
                              String googleTaskId, String syncedTitle,
                              String syncedNotes, LocalDate syncedDueDate,
                              Boolean syncedDone, String googleUpdatedAt,
                              SyncState syncState) {}

    private final Database db;

    public GoogleTasksMappingRepository(Database db) {
        this.db = db;
    }

    public Optional<TaskMapping> findByLocalId(long localTaskId) {
        String sql = "SELECT * FROM google_tasks_mapping WHERE local_task_id = ?";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, localTaskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar mapeamento", e); }
        return Optional.empty();
    }

    public Optional<TaskMapping> findById(long mappingId) {
        String sql = "SELECT * FROM google_tasks_mapping WHERE id = ?";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, mappingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar mapeamento", e); }
        return Optional.empty();
    }

    public Optional<TaskMapping> findByGoogleId(String googleListId, String googleTaskId) {
        String sql = "SELECT * FROM google_tasks_mapping WHERE google_list_id = ? AND google_task_id = ?";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleListId);
            ps.setString(2, googleTaskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar mapeamento", e); }
        return Optional.empty();
    }

    public List<TaskMapping> findByListId(String googleListId) {
        String sql = "SELECT * FROM google_tasks_mapping WHERE google_list_id = ? ORDER BY id";
        List<TaskMapping> mappings = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleListId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mappings.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar mapeamentos Google", e);
        }
        return mappings;
    }

    /** Cria ou atualiza mapeamento e registra horário de sync. */
    public void upsert(long localTaskId, String googleListId, String googleTaskId) {
        db.execute("""
            INSERT INTO google_tasks_mapping(local_task_id, google_list_id, google_task_id, last_synced_at)
            VALUES(?, ?, ?, datetime('now'))
            ON CONFLICT(local_task_id) DO UPDATE SET
                google_list_id  = excluded.google_list_id,
                google_task_id  = excluded.google_task_id,
                sync_state      = 'ACTIVE',
                last_synced_at  = excluded.last_synced_at
            """, localTaskId, googleListId, googleTaskId);
    }

    public void updateSnapshot(long localTaskId, String title, String notes,
                               LocalDate dueDate, boolean done,
                               String googleUpdatedAt) {
        db.execute("""
                UPDATE google_tasks_mapping
                SET synced_title=?, synced_notes=?, synced_due_date=?, synced_done=?,
                    google_updated_at=?, sync_state='ACTIVE', last_synced_at=datetime('now')
                WHERE local_task_id=?
                """, title, normalizeNotes(notes), dueDate != null ? dueDate.toString() : null,
                done ? 1 : 0, googleUpdatedAt, localTaskId);
    }

    public void markState(long mappingId, SyncState state) {
        db.execute("""
                UPDATE google_tasks_mapping
                SET sync_state=?, last_synced_at=datetime('now') WHERE id=?
                """, state.name(), mappingId);
    }

    public void deleteByLocalId(long localTaskId) {
        db.execute("DELETE FROM google_tasks_mapping WHERE local_task_id = ?", localTaskId);
    }

    public void deleteByGoogleId(String googleListId, String googleTaskId) {
        db.execute("DELETE FROM google_tasks_mapping WHERE google_list_id=? AND google_task_id=?",
                googleListId, googleTaskId);
    }

    private TaskMapping map(ResultSet rs) throws SQLException {
        Object syncedDoneValue = rs.getObject("synced_done");
        String dueDate = rs.getString("synced_due_date");
        return new TaskMapping(
                rs.getLong("id"),
                rs.getLong("local_task_id"),
                rs.getString("google_list_id"),
                rs.getString("google_task_id"),
                rs.getString("synced_title"),
                rs.getString("synced_notes"),
                dueDate == null ? null : LocalDate.parse(dueDate),
                syncedDoneValue == null ? null : rs.getInt("synced_done") != 0,
                rs.getString("google_updated_at"),
                SyncState.valueOf(rs.getString("sync_state")));
    }

    private static String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes;
    }
}
