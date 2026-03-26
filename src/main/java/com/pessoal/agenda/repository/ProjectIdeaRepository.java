package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.ProjectIdea;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class ProjectIdeaRepository {
    private final Database db;
    public ProjectIdeaRepository(Database db) { this.db = db; }
    public void save(String title, String description, String status) {
        save(title, description, status, "Geral");
    }
    public void save(String title, String description, String status, String category) {
        db.execute("INSERT INTO project_ideas(title,description,status,category) VALUES(?,?,?,?)",
                title, description, status, category != null ? category : "Geral");
    }
    public List<ProjectIdea> findAll() { return findByCategory(null); }
    public List<ProjectIdea> findByCategory(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT * FROM project_ideas"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY id DESC";
        List<ProjectIdea> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasFilter) ps.setString(1, categoryFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new ProjectIdea(rs.getLong("id"), rs.getString("title"),
                            rs.getString("description"), rs.getString("status"),
                            rs.getString("category")));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar ideias", e); }
        return list;
    }
    public int countInProgress() { return db.queryInt("SELECT COUNT(*) FROM project_ideas WHERE status<>'concluida'"); }
}
