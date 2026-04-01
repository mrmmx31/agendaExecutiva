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
        db.execute("INSERT INTO study_sessions(subject,session_date,duration_minutes,notes) VALUES(?,?,?,?)",
                subject, date.toString(), minutes, notes);
    }

    public List<TaskSession> findByTaskId(long taskId) {
        // study_sessions table does not have taskId column; we search by subject containing task id prefix if needed.
        // For now return all sessions whose subject equals 'Tarefa:#<id>' pattern.
        String sql = "SELECT * FROM study_sessions WHERE subject LIKE ? ORDER BY session_date DESC";
        java.util.List<TaskSession> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "Tarefa:#" + taskId + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String d = rs.getString("session_date");
                    list.add(new TaskSession(rs.getLong("id"), taskId, rs.getString("subject"),
                            d != null ? LocalDate.parse(d) : LocalDate.now(), rs.getInt("duration_minutes"), rs.getString("notes")));
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
                    String d = rs.getString("session_date");
                    // taskId is not stored in table; set to 0 (unknown) in this listing
                    list.add(new TaskSession(rs.getLong("id"), 0L, rs.getString("subject"),
                            d != null ? LocalDate.parse(d) : LocalDate.now(), rs.getInt("duration_minutes"), rs.getString("notes")));
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar sessoes por periodo", e); }
        return list;
    }
}


