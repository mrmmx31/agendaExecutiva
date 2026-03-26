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
    public void save(String productName, int quantity, double unitPrice, LocalDate saleDate) {
        db.execute("INSERT INTO sales_entries(product_name,quantity,unit_price,sale_date) VALUES(?,?,?,?)",
                productName, quantity, unitPrice, saleDate.toString());
    }
    public List<SaleEntry> findAll() {
        List<SaleEntry> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sales_entries ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                list.add(new SaleEntry(rs.getLong("id"), rs.getString("product_name"),
                        rs.getInt("quantity"), rs.getDouble("unit_price"), LocalDate.parse(rs.getString("sale_date"))));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar vendas", e); }
        return list;
    }
}
