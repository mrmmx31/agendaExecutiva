package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.StudySession;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
public class StudyRepository {
    private final Database db;
    public StudyRepository(Database db) { this.db = db; }
    public void save(String subject, LocalDate sessionDate, int durationMinutes, String notes) {
        save(subject, sessionDate, durationMinutes, notes, "Geral");
    }
    public void save(String subject, LocalDate sessionDate, int durationMinutes, String notes, String category) {
        db.execute("INSERT INTO study_sessions(subject,session_date,duration_minutes,notes,category) VALUES(?,?,?,?,?)",
                subject, sessionDate.toString(), durationMinutes, notes,
                category != null ? category : "Geral");
    }
    public List<StudySession> findAll() { return findByCategory(null); }
    public List<StudySession> findByCategory(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT * FROM study_sessions"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY session_date DESC";
        List<StudySession> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasFilter) ps.setString(1, categoryFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new StudySession(rs.getLong("id"), rs.getString("subject"),
                            LocalDate.parse(rs.getString("session_date")),
                            rs.getInt("duration_minutes"), rs.getString("notes"),
                            rs.getString("category")));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar estudos", e); }
        return list;
    }
    public int totalMinutesInMonth(YearMonth month) {
        return db.queryInt("SELECT COALESCE(SUM(duration_minutes),0) FROM study_sessions WHERE session_date>=? AND session_date<=?",
                month.atDay(1).toString(), month.atEndOfMonth().toString());
    }
}
