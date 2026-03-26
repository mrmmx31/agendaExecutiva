package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.*;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acesso a dados de planos de estudo. */
public class StudyPlanRepository {

    private final Database db;
    public StudyPlanRepository(Database db) { this.db = db; }

    // ── INSERT ────────────────────────────────────────────────────────────

    public void save(String title, String studyTypeName, String category, String description,
                     LocalDate startDate, LocalDate targetDate, StudyPlanStatus status,
                     int totalPages) {
        db.execute(
            "INSERT INTO study_plans(title,study_type,category,description,start_date,target_date,status,total_pages)"
            + " VALUES(?,?,?,?,?,?,?,?)",
            title,
            studyTypeName != null ? studyTypeName : "Geral",
            category != null ? category : "Geral",
            description, ds(startDate), ds(targetDate),
            (status != null ? status : StudyPlanStatus.PLANEJADO).name(),
            totalPages);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    public void update(long id, String title, String studyTypeName, String category,
                       String description, LocalDate startDate, LocalDate targetDate,
                       StudyPlanStatus status, int totalPages) {
        db.execute(
            "UPDATE study_plans SET title=?,study_type=?,category=?,description=?,"
            + "start_date=?,target_date=?,status=?,total_pages=? WHERE id=?",
            title, studyTypeName != null ? studyTypeName : "Geral",
            category, description, ds(startDate), ds(targetDate),
            (status != null ? status : StudyPlanStatus.PLANEJADO).name(),
            totalPages, id);
    }

    /** Atualiza apenas progresso (currentPage, progressPercent, status). */
    public void updateProgress(long id, int currentPage, double progressPercent, StudyPlanStatus status) {
        db.execute(
            "UPDATE study_plans SET current_page=?,progress_percent=?,status=? WHERE id=?",
            currentPage, progressPercent,
            (status != null ? status : StudyPlanStatus.EM_ANDAMENTO).name(), id);
    }

    /** Atualiza progresso de livro: current_page, total_pages, progress_percent e status. */
    public void updateProgressFull(long id, int currentPage, int totalPages,
                                   double progressPercent, StudyPlanStatus status) {
        db.execute(
            "UPDATE study_plans SET current_page=?,total_pages=?,progress_percent=?,status=? WHERE id=?",
            currentPage, totalPages, progressPercent,
            (status != null ? status : StudyPlanStatus.EM_ANDAMENTO).name(), id);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public void deleteById(long id) {
        db.execute("DELETE FROM study_entries WHERE study_id=?", id);
        db.execute("DELETE FROM study_schedules WHERE study_plan_id=?", id);
        db.execute("DELETE FROM study_compensations WHERE study_plan_id=?", id);
        db.execute("DELETE FROM study_plans WHERE id=?", id);
    }

    // ── QUERY ─────────────────────────────────────────────────────────────

    public Optional<StudyPlan> findById(long id) {
        List<StudyPlan> list = query("SELECT * FROM study_plans WHERE id=?", new Object[]{id});
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Compatibilidade: sem filtro de status nem de período. */
    public List<StudyPlan> findAll(String categoryFilter) {
        return findAll(categoryFilter, null, null);
    }

    /**
     * Lista planos aplicando até três filtros cumulativos:
     *
     * @param categoryFilter  nome da categoria (null = todas)
     * @param statusFilter    nome do enum de status (null = todos)
     * @param periodFilter    "HOJE" | "SEMANA" | "MES" | "ANO" (null = todos os prazos)
     */
    public List<StudyPlan> findAll(String categoryFilter, String statusFilter, String periodFilter) {
        StringBuilder sql    = new StringBuilder("SELECT * FROM study_plans");
        List<Object>  params = new ArrayList<>();
        List<String>  where  = new ArrayList<>();

        if (categoryFilter != null && !categoryFilter.isBlank()) {
            where.add("category=?");
            params.add(categoryFilter);
        }
        if (statusFilter != null && !statusFilter.isBlank()) {
            where.add("status=?");
            params.add(statusFilter);
        }
        if (periodFilter != null) {
            LocalDate today = LocalDate.now();
            LocalDate rangeStart, rangeEnd;
            switch (periodFilter) {
                case "HOJE"   -> { rangeStart = today; rangeEnd = today; }
                case "SEMANA" -> { rangeStart = today.with(DayOfWeek.MONDAY);
                                   rangeEnd   = today.with(DayOfWeek.SUNDAY); }
                case "MES"    -> { rangeStart = today.withDayOfMonth(1);
                                   rangeEnd   = today.withDayOfMonth(today.lengthOfMonth()); }
                case "ANO"    -> { rangeStart = today.withDayOfYear(1);
                                   rangeEnd   = today.withDayOfYear(today.lengthOfYear()); }
                default       -> { rangeStart = null; rangeEnd = null; }
            }
            if (rangeStart != null) {
                // Plano ativo no período: iniciado antes do fim do período E
                // sem prazo ou prazo ainda dentro / após o início do período
                where.add("(start_date <= ? AND (target_date IS NULL OR target_date >= ?))");
                params.add(rangeEnd.toString());
                params.add(rangeStart.toString());
            }
        }

        if (!where.isEmpty())
            sql.append(" WHERE ").append(String.join(" AND ", where));

        sql.append(" ORDER BY CASE status"
                + " WHEN 'EM_ANDAMENTO' THEN 0 WHEN 'PLANEJADO' THEN 1 ELSE 2 END,"
                + " target_date ASC, id ASC");

        return query(sql.toString(), params.toArray());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private List<StudyPlan> query(String sql, Object[] params) {
        List<StudyPlan> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar planos de estudo", e); }
        return list;
    }

    private StudyPlan mapRow(ResultSet rs) throws SQLException {
        String typeStr  = rs.getString("study_type");
        String statStr  = rs.getString("status");
        String sd = rs.getString("start_date"), td = rs.getString("target_date");
        // Compatibilidade retroativa: converte nomes de enum antigos (ex. "LIVRO") para labels ("Livro")
        String typeName = resolveTypeName(typeStr);
        return new StudyPlan(
            rs.getLong("id"), rs.getString("title"),
            typeName,
            rs.getString("category"), rs.getString("description"),
            sd != null ? LocalDate.parse(sd) : null,
            td != null ? LocalDate.parse(td) : null,
            statStr != null ? StudyPlanStatus.valueOf(statStr) : StudyPlanStatus.PLANEJADO,
            rs.getInt("total_pages"), rs.getInt("current_page"),
            rs.getDouble("progress_percent"));
    }

    /**
     * Converte o valor armazenado no BD para o nome de exibição do tipo.
     * Dados antigos podem conter o nome do enum (ex. "LIVRO"); dados novos
     * já armazenam o label diretamente (ex. "Livro").
     */
    private static String resolveTypeName(String raw) {
        if (raw == null || raw.isBlank()) return "Geral";
        try {
            // Se for um nome de enum válido, retorna o label correspondente
            return StudyType.valueOf(raw).label();
        } catch (IllegalArgumentException ex) {
            // Já é um label ou tipo personalizado — usa como está
            return raw;
        }
    }

    private static String ds(LocalDate d) { return d != null ? d.toString() : null; }
}


