package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;

import java.sql.*;
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

    public record TaskMapping(long id, long localTaskId, String googleListId, String googleTaskId) {}

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

    /** Cria ou atualiza mapeamento e registra horário de sync. */
    public void upsert(long localTaskId, String googleListId, String googleTaskId) {
        db.execute("""
            INSERT INTO google_tasks_mapping(local_task_id, google_list_id, google_task_id, last_synced_at)
            VALUES(?, ?, ?, datetime('now'))
            ON CONFLICT(local_task_id) DO UPDATE SET
                google_list_id  = excluded.google_list_id,
                google_task_id  = excluded.google_task_id,
                last_synced_at  = excluded.last_synced_at
            """, localTaskId, googleListId, googleTaskId);
    }

    public void deleteByLocalId(long localTaskId) {
        db.execute("DELETE FROM google_tasks_mapping WHERE local_task_id = ?", localTaskId);
    }

    public void deleteByGoogleId(String googleListId, String googleTaskId) {
        db.execute("DELETE FROM google_tasks_mapping WHERE google_list_id=? AND google_task_id=?",
                googleListId, googleTaskId);
    }

    private TaskMapping map(ResultSet rs) throws SQLException {
        return new TaskMapping(
                rs.getLong("id"),
                rs.getLong("local_task_id"),
                rs.getString("google_list_id"),
                rs.getString("google_task_id"));
    }
}

