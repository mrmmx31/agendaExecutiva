package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.InventoryItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class InventoryRepository {
    private final Database db;
    public InventoryRepository(Database db) { this.db = db; }
    public void save(String productName, int quantity, int minimumQuantity) {
        db.execute("INSERT INTO inventory_items(product_name,quantity,minimum_quantity) VALUES(?,?,?)",
                productName, quantity, minimumQuantity);
    }
    public List<InventoryItem> findAll() {
        List<InventoryItem> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM inventory_items ORDER BY product_name ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                list.add(new InventoryItem(rs.getLong("id"), rs.getString("product_name"),
                        rs.getInt("quantity"), rs.getInt("minimum_quantity")));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar estoque", e); }
        return list;
    }
    public int countLowStock() { return db.queryInt("SELECT COUNT(*) FROM inventory_items WHERE quantity <= minimum_quantity"); }
}
