package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.StudyEntry;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acesso a dados de entradas do diário de estudo. */
public class StudyEntryRepository {

    private final Database db;
    public StudyEntryRepository(Database db) { this.db = db; }

    // ── INSERT ────────────────────────────────────────────────────────────

    public void save(long studyId, String title, LocalDate date, String content,
                     int durationMinutes, int pageStart, int pageEnd) {
        int order = db.queryInt(
                "SELECT COALESCE(MAX(entry_order),0)+1 FROM study_entries WHERE study_id=?", studyId);
        db.execute(
            "INSERT INTO study_entries(study_id,entry_title,entry_date,content,"
            + "duration_minutes,page_start,page_end,entry_order) VALUES(?,?,?,?,?,?,?,?)",
            studyId, title != null ? title : "", date.toString(),
            content, durationMinutes, pageStart, pageEnd, order);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    public void update(long id, String title, LocalDate date, String content,
                       int durationMinutes, int pageStart, int pageEnd) {
        db.execute(
            "UPDATE study_entries SET entry_title=?,entry_date=?,content=?,"
            + "duration_minutes=?,page_start=?,page_end=? WHERE id=?",
            title != null ? title : "", date.toString(), content,
            durationMinutes, pageStart, pageEnd, id);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public void deleteById(long id) { db.execute("DELETE FROM study_entries WHERE id=?", id); }

    // ── QUERY ─────────────────────────────────────────────────────────────

    public Optional<StudyEntry> findById(long id) {
        List<StudyEntry> list = query(
                "SELECT * FROM study_entries WHERE id=?", new Object[]{id});
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<StudyEntry> findByStudyId(long studyId) {
        return query(
            "SELECT * FROM study_entries WHERE study_id=? ORDER BY entry_order ASC, entry_date ASC, id ASC",
            new Object[]{studyId});
    }

    /** Total de minutos estudados no mês — alimenta o KPI do dashboard. */
    public int totalMinutesInMonth(YearMonth month) {
        return db.queryInt(
            "SELECT COALESCE(SUM(duration_minutes),0) FROM study_entries"
            + " WHERE entry_date >= ? AND entry_date <= ?",
            month.atDay(1).toString(), month.atEndOfMonth().toString());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private List<StudyEntry> query(String sql, Object[] params) {
        List<StudyEntry> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar entradas de estudo", e); }
        return list;
    }

    private StudyEntry mapRow(ResultSet rs) throws SQLException {
        String d = rs.getString("entry_date");
        return new StudyEntry(
            rs.getLong("id"), rs.getLong("study_id"),
            d != null ? LocalDate.parse(d) : LocalDate.now(),
            rs.getString("entry_title"), rs.getString("content"),
            rs.getInt("duration_minutes"),
            rs.getInt("page_start"), rs.getInt("page_end"),
            rs.getInt("entry_order"));
    }
}

