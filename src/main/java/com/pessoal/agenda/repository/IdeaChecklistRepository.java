package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.IdeaChecklistItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório CRUD para itens de checklist de Ideias / Projetos.
 * Cada item pertence a uma única ideia (idea_id FK).
 */
public class IdeaChecklistRepository {

    private final Database db;

    public IdeaChecklistRepository(Database db) { this.db = db; }

    // ── Consultas ─────────────────────────────────────────────────────────────

    /** Retorna todos os itens de uma ideia, ordenados por posição/ID de inserção. */
    public List<IdeaChecklistItem> findByIdeaId(long ideaId) {
        List<IdeaChecklistItem> list = new ArrayList<>();
        String sql = "SELECT * FROM idea_checklist_items WHERE idea_id=? ORDER BY position, id";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar checklist", e); }
        return list;
    }

    // ── Inserção ──────────────────────────────────────────────────────────────

    /**
     * Insere um novo item ao final da lista e retorna o objeto com ID gerado.
     * Usado tanto para persistência imediata (ideia existente) quanto na
     * etapa de salvamento de novas ideias.
     */
    public IdeaChecklistItem addItem(long ideaId, String text) {
        int pos = db.queryInt(
                "SELECT COALESCE(MAX(position),0)+1 FROM idea_checklist_items WHERE idea_id=?", ideaId);
        String sql = "INSERT INTO idea_checklist_items(idea_id,text,done,position) VALUES(?,?,0,?)";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ideaId);
            ps.setString(2, text);
            ps.setInt(3, pos);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1;
                return new IdeaChecklistItem(id, ideaId, text, false, pos);
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao adicionar item de checklist", e); }
    }

    // ── Atualizações ──────────────────────────────────────────────────────────

    /** Altera o estado concluído/pendente do item. Persiste imediatamente. */
    public void updateDone(long id, boolean done) {
        db.execute("UPDATE idea_checklist_items SET done=? WHERE id=?", done ? 1 : 0, id);
    }

    /** Altera o texto de um item existente. */
    public void updateText(long id, String text) {
        db.execute("UPDATE idea_checklist_items SET text=? WHERE id=?", text, id);
    }

    // ── Remoção ───────────────────────────────────────────────────────────────

    /** Remove um item específico pelo seu ID. */
    public void deleteItem(long id) {
        db.execute("DELETE FROM idea_checklist_items WHERE id=?", id);
    }

    /** Remove todos os itens de uma ideia. Deve ser chamado antes de deletar a ideia. */
    public void deleteByIdeaId(long ideaId) {
        db.execute("DELETE FROM idea_checklist_items WHERE idea_id=?", ideaId);
    }

    // ── Estatísticas ──────────────────────────────────────────────────────────

    public int countDoneByIdeaId(long ideaId) {
        return db.queryInt("SELECT COUNT(*) FROM idea_checklist_items WHERE idea_id=? AND done=1", ideaId);
    }

    public int countTotalByIdeaId(long ideaId) {
        return db.queryInt("SELECT COUNT(*) FROM idea_checklist_items WHERE idea_id=?", ideaId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private IdeaChecklistItem mapRow(ResultSet rs) throws SQLException {
        return new IdeaChecklistItem(
                rs.getLong("id"),
                rs.getLong("idea_id"),
                rs.getString("text"),
                rs.getInt("done") == 1,
                rs.getInt("position"));
    }
}

