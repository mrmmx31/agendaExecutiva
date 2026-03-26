package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.StudyScheduleDay;

import java.sql.*;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/** Acesso à grade horária semanal de planos de estudo. */
public class StudyScheduleRepository {

    private final Database db;
    public StudyScheduleRepository(Database db) { this.db = db; }

    // ── INSERT / UPSERT ──────────────────────────────────────────────────

    /**
     * Define (ou atualiza) a carga mínima de um dia para um plano.
     * Se já existir entrada para o mesmo plano+dia, substitui.
     */
    public void setDay(long studyPlanId, DayOfWeek day, int minMinutes) {
        db.execute(
            "INSERT INTO study_schedules(study_plan_id, day_of_week, min_minutes)"
            + " VALUES(?,?,?)"
            + " ON CONFLICT(study_plan_id, day_of_week) DO UPDATE SET min_minutes=excluded.min_minutes",
            studyPlanId, day.getValue(), minMinutes);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public void removeDay(long studyPlanId, DayOfWeek day) {
        db.execute("DELETE FROM study_schedules WHERE study_plan_id=? AND day_of_week=?",
                studyPlanId, day.getValue());
    }

    /** Remove toda a grade de um plano (usado no cascade delete). */
    public void deleteByStudyId(long studyPlanId) {
        db.execute("DELETE FROM study_schedules WHERE study_plan_id=?", studyPlanId);
    }

    // ── QUERY ─────────────────────────────────────────────────────────────

    public List<StudyScheduleDay> findByStudyId(long studyPlanId) {
        List<StudyScheduleDay> list = new ArrayList<>();
        String sql = "SELECT * FROM study_schedules WHERE study_plan_id=? ORDER BY day_of_week";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studyPlanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar grade horária", e); }
        return list;
    }

    private static StudyScheduleDay map(ResultSet rs) throws SQLException {
        return new StudyScheduleDay(
            rs.getLong("id"),
            rs.getLong("study_plan_id"),
            DayOfWeek.of(rs.getInt("day_of_week")),
            rs.getInt("min_minutes"));
    }
}

