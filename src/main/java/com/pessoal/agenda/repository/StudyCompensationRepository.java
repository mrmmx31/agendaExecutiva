package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.StudyCompensation;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Acesso às compensações de sessões de estudo perdidas. */
public class StudyCompensationRepository {

    private final Database db;
    public StudyCompensationRepository(Database db) { this.db = db; }

    // ── INSERT ────────────────────────────────────────────────────────────

    public void save(long studyPlanId, LocalDate missedDate, int compensationMinutes, String notes) {
        db.execute(
            "INSERT INTO study_compensations"
            + "(study_plan_id, missed_date, compensation_minutes, status, notes)"
            + " VALUES(?,?,?,'PENDENTE',?)",
            studyPlanId, missedDate.toString(), compensationMinutes, notes);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    /** Marca uma compensação como concluída, registrando a data de compensação. */
    public void markDone(long id, LocalDate compensationDate, String notes) {
        db.execute(
            "UPDATE study_compensations SET status='CONCLUIDA', compensation_date=?, notes=? WHERE id=?",
            compensationDate.toString(), notes, id);
    }

    /** Cancela uma compensação pendente. */
    public void cancel(long id) {
        db.execute("UPDATE study_compensations SET status='CANCELADA' WHERE id=?", id);
    }

    public void deleteByStudyId(long studyPlanId) {
        db.execute("DELETE FROM study_compensations WHERE study_plan_id=?", studyPlanId);
    }

    // ── QUERY ─────────────────────────────────────────────────────────────

    public List<StudyCompensation> findByStudyId(long studyPlanId) {
        return query(
            "SELECT sc.*, sp.title as study_title FROM study_compensations sc"
            + " JOIN study_plans sp ON sp.id = sc.study_plan_id"
            + " WHERE sc.study_plan_id=? ORDER BY sc.missed_date DESC",
            studyPlanId);
    }

    /** Todas as compensações pendentes em todos os planos — painel global. */
    public List<StudyCompensation> findAllPending() {
        return query(
            "SELECT sc.*, sp.title as study_title FROM study_compensations sc"
            + " JOIN study_plans sp ON sp.id = sc.study_plan_id"
            + " WHERE sc.status='PENDENTE' ORDER BY sc.missed_date ASC",
            (Object[]) null);
    }

    /** Verifica se já existe compensação registrada para esse plano+data. */
    public boolean existsForDate(long studyPlanId, LocalDate missedDate) {
        return db.queryInt(
            "SELECT COUNT(*) FROM study_compensations WHERE study_plan_id=? AND missed_date=?",
            studyPlanId, missedDate.toString()) > 0;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private List<StudyCompensation> query(String sql, Object... params) {
        List<StudyCompensation> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (params != null)
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar compensações", e); }
        return list;
    }

    private static StudyCompensation map(ResultSet rs) throws SQLException {
        String cd  = rs.getString("compensation_date");
        String cat = rs.getString("created_at");
        return new StudyCompensation(
            rs.getLong("id"),
            rs.getLong("study_plan_id"),
            rs.getString("study_title"),
            LocalDate.parse(rs.getString("missed_date")),
            cd  != null ? LocalDate.parse(cd) : null,
            rs.getInt("compensation_minutes"),
            rs.getString("status"),
            rs.getString("notes"),
            cat != null ? LocalDateTime.parse(cat.replace(" ", "T")) : LocalDateTime.now());
    }
}

