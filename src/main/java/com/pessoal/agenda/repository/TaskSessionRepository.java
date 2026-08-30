package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.TaskSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório para gravar sessões relacionadas a tarefas. Reaproveita a tabela
 * `study_sessions` para persistência simples (historic storage).
 */
public class TaskSessionRepository {
    private final Database db;
    public TaskSessionRepository(Database db) { this.db = db; }

    public void save(long taskId, String subject, LocalDate date, int minutes, String notes) {
        db.execute("INSERT INTO study_sessions(task_id,subject,session_date,duration_minutes,notes) VALUES(?,?,?,?,?)",
                taskId, subject, date.toString(), minutes, notes);
    }

    public void update(long sessionId, String subject, int minutes, String notes) {
        db.execute("UPDATE study_sessions SET subject=?, duration_minutes=?, notes=? WHERE id=?",
                subject, minutes, notes, sessionId);
    }

    public List<TaskSession> findByTaskId(long taskId) {
        return findByTaskId(taskId, null, null);
    }

    public List<TaskSession> findByTaskId(long taskId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM study_sessions
                WHERE (task_id = ? OR (task_id IS NULL AND subject LIKE ?))
                """);
        if (from != null) sql.append(" AND session_date >= ?");
        if (to != null) sql.append(" AND session_date <= ?");
        sql.append(" ORDER BY session_date DESC");
        List<TaskSession> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setLong(index++, taskId);
            ps.setString(index++, "Tarefa:#" + taskId + "%");
            if (from != null) ps.setString(index++, from.toString());
            if (to != null) ps.setString(index, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSession(rs, taskId));
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar sessoes de tarefa", e); }
        return list;
    }

    public List<TaskSession> findByDateRange(LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM study_sessions WHERE session_date BETWEEN ? AND ? ORDER BY session_date DESC";
        java.util.List<TaskSession> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSession(rs, null));
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar sessoes por periodo", e); }
        return list;
    }

    private TaskSession mapSession(ResultSet rs, Long fallbackTaskId) throws SQLException {
        long storedTaskId = rs.getLong("task_id");
        long resolvedTaskId = rs.wasNull() ? (fallbackTaskId != null ? fallbackTaskId : 0L) : storedTaskId;
        String date = rs.getString("session_date");
        return new TaskSession(rs.getLong("id"), resolvedTaskId, rs.getString("subject"),
                date != null ? LocalDate.parse(date) : LocalDate.now(),
                rs.getInt("duration_minutes"), rs.getString("notes"));
    }
}

