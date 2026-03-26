package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.ChecklistItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class ChecklistRepository {
    private final Database db;
    public ChecklistRepository(Database db) { this.db = db; }
    public void save(String protocolName, String itemText) {
        save(protocolName, itemText, "Geral");
    }
    public void save(String protocolName, String itemText, String category) {
        db.execute("INSERT INTO checklist_items(protocol_name,item_text,done,category) VALUES(?,?,0,?)",
                protocolName, itemText, category != null ? category : "Geral");
    }
    public void markDone(long id) { db.execute("UPDATE checklist_items SET done=1 WHERE id=?", id); }
    public List<ChecklistItem> findAll() { return findByCategory(null); }
    public List<ChecklistItem> findByCategory(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT * FROM checklist_items"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY done ASC, id DESC";
        List<ChecklistItem> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasFilter) ps.setString(1, categoryFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new ChecklistItem(rs.getLong("id"), rs.getString("protocol_name"),
                            rs.getString("item_text"), rs.getInt("done") == 1,
                            rs.getString("category")));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar checklist", e); }
        return list;
    }
    public int countPending() {
        // Conta execuções de protocolo ativas (novo modelo)
        // Fallback: some com itens legados não concluídos (retrocompatibilidade)
        int activeExecs = db.queryInt(
            "SELECT COUNT(*) FROM protocol_executions WHERE status='ATIVA'");
        int legacyPending = db.queryInt(
            "SELECT COUNT(*) FROM checklist_items WHERE done=0");
        return activeExecs + legacyPending;
    }
}
