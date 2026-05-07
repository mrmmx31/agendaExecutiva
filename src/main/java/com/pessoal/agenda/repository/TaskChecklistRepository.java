package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.TaskChecklistItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório CRUD para itens de checklist de Tarefas da Agenda.
 * Cada item pertence a uma única tarefa (task_id FK).
 */
public class TaskChecklistRepository {

    private final Database db;

    public TaskChecklistRepository(Database db) { this.db = db; }

    // ── Consultas ─────────────────────────────────────────────────────────────

    /** Retorna todos os itens de uma tarefa, ordenados por posição/ID de inserção. */
    public List<TaskChecklistItem> findByTaskId(long taskId) {
        List<TaskChecklistItem> list = new ArrayList<>();
        String sql = "SELECT * FROM task_checklist_items WHERE task_id=? ORDER BY position, id";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar checklist de tarefa", e); }
        return list;
    }

    // ── Inserção ──────────────────────────────────────────────────────────────

    /** Insere um novo item ao final da lista e retorna o objeto com ID gerado. */
    public TaskChecklistItem addItem(long taskId, String text) {
        int pos = db.queryInt(
                "SELECT COALESCE(MAX(position),0)+1 FROM task_checklist_items WHERE task_id=?", taskId);
        String sql = "INSERT INTO task_checklist_items(task_id,text,done,position,kanban_column) VALUES(?,?,0,?,'backlog')";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, taskId);
            ps.setString(2, text);
            ps.setInt(3, pos);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1;
                return new TaskChecklistItem(id, taskId, text, false, pos, "backlog");
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao adicionar item de checklist de tarefa", e); }
    }

    // ── Atualizações ──────────────────────────────────────────────────────────

    /** Altera o estado concluído/pendente do item — sincroniza a coluna Kanban. */
    public void updateDone(long id, boolean done) {
        if (done) {
            db.execute("UPDATE task_checklist_items SET done=1, kanban_column='concluido' WHERE id=?", id);
        } else {
            db.execute("UPDATE task_checklist_items SET done=0,"
                    + " kanban_column=CASE WHEN kanban_column='concluido' THEN 'backlog' ELSE kanban_column END"
                    + " WHERE id=?", id);
        }
    }

    /** Move um item para outra coluna do Kanban e sincroniza o flag done. */
    public void updateColumn(long id, String column) {
        boolean done = "concluido".equals(column);
        db.execute("UPDATE task_checklist_items SET kanban_column=?, done=? WHERE id=?",
                column, done ? 1 : 0, id);
    }

    /** Altera o texto de um item existente. */
    public void updateText(long id, String text) {
        db.execute("UPDATE task_checklist_items SET text=? WHERE id=?", text, id);
    }

    // ── Remoção ───────────────────────────────────────────────────────────────

    /** Remove um item específico pelo seu ID. */
    public void deleteItem(long id) {
        db.execute("DELETE FROM task_checklist_items WHERE id=?", id);
    }

    /** Remove todos os itens de uma tarefa. */
    public void deleteByTaskId(long taskId) {
        db.execute("DELETE FROM task_checklist_items WHERE task_id=?", taskId);
    }

    // ── Estatísticas ──────────────────────────────────────────────────────────

    public int countDoneByTaskId(long taskId) {
        return db.queryInt("SELECT COUNT(*) FROM task_checklist_items WHERE task_id=? AND done=1", taskId);
    }

    public int countTotalByTaskId(long taskId) {
        return db.queryInt("SELECT COUNT(*) FROM task_checklist_items WHERE task_id=?", taskId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private TaskChecklistItem mapRow(ResultSet rs) throws SQLException {
        String col = rs.getString("kanban_column");
        return new TaskChecklistItem(
                rs.getLong("id"),
                rs.getLong("task_id"),
                rs.getString("text"),
                rs.getInt("done") == 1,
                rs.getInt("position"),
                col != null ? col : "backlog");
    }
}

