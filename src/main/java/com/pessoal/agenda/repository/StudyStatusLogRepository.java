package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.StudyPlanStatus;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rastreia o histórico de mudanças de status de planos de estudo.
 *
 * Utilizado pelo StudyAttendanceService para:
 *   - Determinar a data de ativação do plano (1ª entrada com EM_ANDAMENTO)
 *   - Identificar intervalos de pausa para excluí-los da frequência
 */
public class StudyStatusLogRepository {

    /** Linha de histórico: plano + status + data da mudança. */
    public record StatusEntry(long planId, StudyPlanStatus status, LocalDate changedAt) {}

    private final Database db;

    public StudyStatusLogRepository(Database db) { this.db = db; }

    // ── INSERT ─────────────────────────────────────────────────────────────

    /**
     * Registra uma mudança de status. Ignora duplicatas consecutivas:
     * se o status mais recente já é o mesmo, não insere.
     */
    public void log(long planId, StudyPlanStatus status) {
        StatusEntry latest = findLatest(planId);
        if (latest != null && latest.status() == status) return;
        db.execute(
            "INSERT INTO study_plan_status_log(study_plan_id, status, changed_at) VALUES(?,?,?)",
            planId, status.name(), LocalDate.now().toString());
    }

    // ── QUERY ──────────────────────────────────────────────────────────────

    /** Retorna a entrada mais recente para o plano, ou null se não houver nenhuma. */
    public StatusEntry findLatest(long planId) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM study_plan_status_log WHERE study_plan_id=?"
                + " ORDER BY changed_at DESC, id DESC LIMIT 1")) {
            ps.setLong(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar status log", e); }
        return null;
    }

    /** Retorna todo o histórico de um plano em ordem cronológica ascendente. */
    public List<StatusEntry> findByPlanId(long planId) {
        List<StatusEntry> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM study_plan_status_log WHERE study_plan_id=?"
                + " ORDER BY changed_at ASC, id ASC")) {
            ps.setLong(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar status log", e); }
        return list;
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    public void deleteByPlanId(long planId) {
        db.execute("DELETE FROM study_plan_status_log WHERE study_plan_id=?", planId);
    }

    // ── helper ─────────────────────────────────────────────────────────────

    private static StatusEntry map(ResultSet rs) throws SQLException {
        String statStr = rs.getString("status");
        String dateStr = rs.getString("changed_at");
        StudyPlanStatus status;
        try {
            status = statStr != null ? StudyPlanStatus.valueOf(statStr) : StudyPlanStatus.PLANEJADO;
        } catch (IllegalArgumentException ex) {
            status = StudyPlanStatus.PLANEJADO;
        }
        return new StatusEntry(
                rs.getLong("study_plan_id"),
                status,
                dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now());
    }
}

