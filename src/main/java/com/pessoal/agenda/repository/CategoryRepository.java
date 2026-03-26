package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.Category;
import com.pessoal.agenda.model.CategoryDomain;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso a dados de categorias personalizadas.
 * Cada categoria pertence a um unico dominio (TASK, CHECKLIST, STUDY, IDEA).
 */
public class CategoryRepository {

    private final Database db;

    public CategoryRepository(Database db) { this.db = db; }

    public List<Category> findByDomain(CategoryDomain domain) {
        List<Category> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM categories WHERE domain=? ORDER BY name ASC")) {
            ps.setString(1, domain.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar categorias", e); }
        return list;
    }

    public void save(String name, CategoryDomain domain, String color) {
        db.execute("INSERT OR IGNORE INTO categories(name,domain,color) VALUES(?,?,?)",
                name, domain.name(), color);
    }

    public void delete(long id) {
        db.execute("DELETE FROM categories WHERE id=?", id);
    }

    /**
     * Popula categorias padrao para um dominio somente se ele estiver vazio.
     * Idempotente — seguro para re-execucao.
     */
    public void seedIfEmpty(CategoryDomain domain, List<String> names) {
        int count = db.queryInt("SELECT COUNT(*) FROM categories WHERE domain=?", domain.name());
        if (count == 0) {
            for (String name : names) save(name, domain, null);
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        return new Category(
                rs.getLong("id"),
                rs.getString("name"),
                CategoryDomain.valueOf(rs.getString("domain")),
                rs.getString("color"));
    }
}

