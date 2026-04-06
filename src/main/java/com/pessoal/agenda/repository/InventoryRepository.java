package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.InventoryItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class InventoryRepository {
    private final Database db;
    public InventoryRepository(Database db) { this.db = db; }
    public void save(String productName, String itemType, int quantity, int minimumQuantity,
                     double unitPrice, String category, String description) {
        db.execute("""
            INSERT INTO inventory_items(product_name,item_type,quantity,minimum_quantity,
                                        unit_price,category,description)
            VALUES(?,?,?,?,?,?,?)""",
                productName, itemType, quantity, minimumQuantity,
                unitPrice, category != null ? category : "Geral", description);
    }
    public void update(long id, String productName, String itemType, int quantity,
                       int minimumQuantity, double unitPrice, String category, String description) {
        db.execute("""
            UPDATE inventory_items SET product_name=?,item_type=?,quantity=?,
                minimum_quantity=?,unit_price=?,category=?,description=? WHERE id=?""",
                productName, itemType, quantity, minimumQuantity,
                unitPrice, category != null ? category : "Geral", description, id);
    }
    public void adjustStock(long id, int delta) {
        db.execute("UPDATE inventory_items SET quantity = MAX(0, quantity + ?) WHERE id=?", delta, id);
    }
    public void deleteById(long id) { db.execute("DELETE FROM inventory_items WHERE id=?", id); }
    public List<InventoryItem> findAll() {
        List<InventoryItem> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM inventory_items ORDER BY product_name ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar estoque", e); }
        return list;
    }
    public int countLowStock() {
        return db.queryInt("""
            SELECT COUNT(*) FROM inventory_items
            WHERE (item_type IS NULL OR item_type != 'serviço')
              AND quantity <= minimum_quantity""");
    }
    public double totalStockValue() {
        return db.queryDouble("""
            SELECT COALESCE(SUM(quantity * unit_price), 0) FROM inventory_items
            WHERE item_type = 'material' OR item_type IS NULL""");
    }
    private InventoryItem mapRow(ResultSet rs) throws SQLException {
        return new InventoryItem(
                rs.getLong("id"),
                rs.getString("product_name"),
                safe(rs, "item_type",   "material"),
                rs.getInt("quantity"),
                rs.getInt("minimum_quantity"),
                safeDouble(rs, "unit_price"),
                safe(rs, "category",    "Geral"),
                safe(rs, "description", null));
    }
    private String safe(ResultSet rs, String col, String def) {
        try { String v = rs.getString(col); return v != null ? v : def; }
        catch (SQLException e) { return def; }
    }
    private double safeDouble(ResultSet rs, String col) {
        try { return rs.getDouble(col); } catch (SQLException e) { return 0.0; }
    }
}
