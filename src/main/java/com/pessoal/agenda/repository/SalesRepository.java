package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.SaleEntry;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesRepository {
    private final Database db;
    public SalesRepository(Database db) { this.db = db; }

    public void save(String productName, String itemType, int quantity, double unitPrice,
                     LocalDate saleDate, String clientName, String notes, String status) {
        db.execute("""
            INSERT INTO sales_entries(product_name,item_type,quantity,unit_price,
                                      sale_date,client_name,notes,status)
            VALUES(?,?,?,?,?,?,?,?)""",
                productName, itemType, quantity, unitPrice,
                saleDate.toString(), clientName, notes,
                status != null ? status : "recebido");
    }

    public void update(long id, String productName, String itemType, int quantity, double unitPrice,
                       LocalDate saleDate, String clientName, String notes, String status) {
        db.execute("""
            UPDATE sales_entries SET product_name=?,item_type=?,quantity=?,unit_price=?,
                sale_date=?,client_name=?,notes=?,status=? WHERE id=?""",
                productName, itemType, quantity, unitPrice,
                saleDate.toString(), clientName, notes,
                status != null ? status : "recebido", id);
    }

    public void deleteById(long id) { db.execute("DELETE FROM sales_entries WHERE id=?", id); }

    public List<SaleEntry> findAll() {
        List<SaleEntry> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM sales_entries ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar vendas", e); }
        return list;
    }

    public double sumRevenue() {
        return db.queryDouble("SELECT COALESCE(SUM(quantity*unit_price),0) FROM sales_entries WHERE status='recebido'");
    }
    public double sumPending() {
        return db.queryDouble("SELECT COALESCE(SUM(quantity*unit_price),0) FROM sales_entries WHERE status='pendente'");
    }

    private SaleEntry mapRow(ResultSet rs) throws SQLException {
        String d = rs.getString("sale_date");
        return new SaleEntry(
                rs.getLong("id"),
                rs.getString("product_name"),
                safe(rs, "item_type",    "material"),
                rs.getInt("quantity"),
                rs.getDouble("unit_price"),
                d != null ? LocalDate.parse(d) : LocalDate.now(),
                safe(rs, "client_name", null),
                safe(rs, "notes",       null),
                safe(rs, "status",      "recebido"));
    }

    private String safe(ResultSet rs, String col, String def) {
        try { String v = rs.getString(col); return v != null ? v : def; }
        catch (SQLException e) { return def; }
    }
}
