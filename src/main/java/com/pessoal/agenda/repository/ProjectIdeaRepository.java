package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.ProjectIdea;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acesso a dados de Ideias e Projetos Científicos. */
public class ProjectIdeaRepository {

    private final Database db;

    public ProjectIdeaRepository(Database db) { this.db = db; }

    // ── INSERT ────────────────────────────────────────────────────────────────

    /** Compatibilidade retroativa. */
    public void save(String title, String description, String status) {
        save(title, description, status, "Geral");
    }

    /** Compatibilidade com categoria. */
    public void save(String title, String description, String status, String category) {
        db.execute(
            "INSERT INTO project_ideas(title,description,status,category,"
            + "priority,idea_type,impact_level,feasibility,estimated_hours)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
            title, description, status, category != null ? category : "Geral",
            "NORMAL", "GERAL", "MEDIO", 3, 0);
    }

    /** Insere ideia completa com todos os campos avançados. Retorna o ID gerado. */
    public long saveFullIdea(ProjectIdea p) {
        String sql = "INSERT INTO project_ideas(title,description,status,category,"
                + "priority,idea_type,impact_level,feasibility,estimated_hours,"
                + "start_date,target_date,methodology,next_actions,keywords,references_text)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.title()); ps.setString(2, p.description());
            ps.setString(3, p.status());
            ps.setString(4, p.category() != null ? p.category() : "Geral");
            ps.setString(5, p.priority()    != null ? p.priority()    : "NORMAL");
            ps.setString(6, p.ideaType()    != null ? p.ideaType()    : "GERAL");
            ps.setString(7, p.impactLevel() != null ? p.impactLevel() : "MEDIO");
            ps.setInt(8, p.feasibility()); ps.setInt(9, p.estimatedHours());
            ps.setString(10, p.startDate()  != null ? p.startDate().toString()  : null);
            ps.setString(11, p.targetDate() != null ? p.targetDate().toString() : null);
            ps.setString(12, p.methodology()); ps.setString(13, p.nextActions());
            ps.setString(14, p.keywords()); ps.setString(15, p.referencesText());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao salvar ideia", e); }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    public void update(ProjectIdea p) {
        db.execute(
            "UPDATE project_ideas SET title=?,description=?,status=?,category=?,"
            + "priority=?,idea_type=?,impact_level=?,feasibility=?,estimated_hours=?,"
            + "start_date=?,target_date=?,methodology=?,next_actions=?,keywords=?,references_text=?"
            + " WHERE id=?",
            p.title(), p.description(), p.status(),
            p.category() != null ? p.category() : "Geral",
            p.priority() != null ? p.priority() : "NORMAL",
            p.ideaType()  != null ? p.ideaType()  : "GERAL",
            p.impactLevel()!= null ? p.impactLevel(): "MEDIO",
            p.feasibility(), p.estimatedHours(),
            p.startDate()  != null ? p.startDate().toString()  : null,
            p.targetDate() != null ? p.targetDate().toString() : null,
            p.methodology(), p.nextActions(), p.keywords(), p.referencesText(),
            p.id());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    public void deleteById(long id) {
        db.execute("DELETE FROM project_ideas WHERE id=?", id);
    }

    // ── Modo Próximas Ações ───────────────────────────────────────────────────

    /**
     * Retorna o modo da seção "Próximas Ações" para uma ideia:
     * "text" (padrão) ou "checklist".
     */
    public String getNextActionsMode(long id) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT next_actions_mode FROM project_ideas WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return v != null ? v : "text";
                }
            }
        } catch (SQLException ignored) { /* coluna ainda não migrada — fallback */ }
        return "text";
    }

    /** Persiste o modo da seção "Próximas Ações" de uma ideia. */
    public void setNextActionsMode(long id, String mode) {
        try {
            db.execute("UPDATE project_ideas SET next_actions_mode=? WHERE id=?", mode, id);
        } catch (RuntimeException ignored) { /* coluna ainda não migrada */ }
    }

    // ── QUERY ─────────────────────────────────────────────────────────────────

    public Optional<ProjectIdea> findById(long id) {
        List<ProjectIdea> r = query("SELECT * FROM project_ideas WHERE id=?", new Object[]{id});
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public List<ProjectIdea> findAll() { return findByCategory(null); }

    public List<ProjectIdea> findByCategory(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT * FROM project_ideas"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY CASE status"
                + " WHEN 'em_execucao' THEN 1 WHEN 'em execução' THEN 1"
                + " WHEN 'prototipagem' THEN 2 WHEN 'em_validacao' THEN 3"
                + " WHEN 'nova' THEN 4 WHEN 'pausada' THEN 5"
                + " WHEN 'concluida' THEN 6 WHEN 'concluída' THEN 6"
                + " ELSE 7 END, id DESC";
        return query(sql, hasFilter ? new Object[]{categoryFilter} : new Object[0]);
    }

    public List<ProjectIdea> findWithFilters(String categoryFilter, String statusFilter,
                                              String priorityFilter, String typeFilter,
                                              String searchText) {
        StringBuilder sql = new StringBuilder("SELECT * FROM project_ideas WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            sql.append(" AND category=?"); params.add(categoryFilter);
        }
        if (statusFilter != null && !statusFilter.isBlank()) {
            sql.append(" AND status=?"); params.add(statusFilter);
        }
        if (priorityFilter != null && !priorityFilter.isBlank()) {
            sql.append(" AND priority=?"); params.add(priorityFilter);
        }
        if (typeFilter != null && !typeFilter.isBlank()) {
            sql.append(" AND idea_type=?"); params.add(typeFilter);
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (title LIKE ? OR description LIKE ? OR keywords LIKE ?)");
            String q = "%" + searchText + "%";
            params.add(q); params.add(q); params.add(q);
        }
        sql.append(" ORDER BY CASE status"
                + " WHEN 'em_execucao' THEN 1 WHEN 'em execução' THEN 1"
                + " WHEN 'prototipagem' THEN 2 WHEN 'em_validacao' THEN 3"
                + " WHEN 'nova' THEN 4 WHEN 'pausada' THEN 5"
                + " WHEN 'concluida' THEN 6 WHEN 'concluída' THEN 6"
                + " ELSE 7 END,"
                + " CASE priority WHEN 'CRITICA' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END,"
                + " id DESC");
        return query(sql.toString(), params.toArray());
    }

    public List<ProjectIdea> findByStatus(String status) {
        return query("SELECT * FROM project_ideas WHERE status=? ORDER BY id DESC",
                new Object[]{status});
    }

    public int countAll()      { return db.queryInt("SELECT COUNT(*) FROM project_ideas"); }
    public int countActive()   { return db.queryInt("SELECT COUNT(*) FROM project_ideas WHERE status NOT IN ('concluida','concluída','abandonada')"); }
    public int countDone()     { return db.queryInt("SELECT COUNT(*) FROM project_ideas WHERE status IN ('concluida','concluída')"); }
    public int countInProgress() { return db.queryInt("SELECT COUNT(*) FROM project_ideas WHERE status IN ('em_execucao','em execução','prototipagem')"); }
    public int countHighImpact()  { return db.queryInt("SELECT COUNT(*) FROM project_ideas WHERE impact_level IN ('ALTO','REVOLUCIONARIO')"); }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<ProjectIdea> query(String sql, Object[] params) {
        List<ProjectIdea> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar ideias", e); }
        return list;
    }

    private ProjectIdea mapRow(ResultSet rs) throws SQLException {
        String sd = rs.getString("start_date");
        String td = rs.getString("target_date");
        int feasibility = rs.getInt("feasibility");
        if (feasibility == 0) feasibility = 3;
        return new ProjectIdea(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                nullOrDefault(rs, "category", "Geral"),
                nullOrDefault(rs, "priority", "NORMAL"),
                nullOrDefault(rs, "idea_type", "GERAL"),
                nullOrDefault(rs, "impact_level", "MEDIO"),
                feasibility,
                rs.getInt("estimated_hours"),
                sd != null ? LocalDate.parse(sd) : null,
                td != null ? LocalDate.parse(td) : null,
                rs.getString("methodology"),
                rs.getString("next_actions"),
                rs.getString("keywords"),
                rs.getString("references_text"));
    }

    private String nullOrDefault(ResultSet rs, String col, String def) {
        try { String v = rs.getString(col); return v != null ? v : def; }
        catch (SQLException e) { return def; }
    }
}
