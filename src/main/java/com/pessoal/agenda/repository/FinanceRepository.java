package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.FinanceEntry;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class FinanceRepository {
    private final Database db;
    public FinanceRepository(Database db) { this.db = db; }
    public void save(String type, String description, double amount, LocalDate dueDate, boolean paid) {
        db.execute("INSERT INTO finance_entries(entry_type,description,amount,due_date,paid) VALUES(?,?,?,?,?)",
                type, description, amount, dueDate != null ? dueDate.toString() : null, paid ? 1 : 0);
    }
    public void markPaid(long id) { db.execute("UPDATE finance_entries SET paid=1 WHERE id=?", id); }
    public void deleteById(long id) { db.execute("DELETE FROM finance_entries WHERE id=?", id); }
    public void update(long id, String type, String description, double amount, LocalDate dueDate, boolean paid) {
        db.execute("UPDATE finance_entries SET entry_type=?,description=?,amount=?,due_date=?,paid=? WHERE id=?",
                type, description, amount, dueDate != null ? dueDate.toString() : null, paid ? 1 : 0, id);
    }
    public List<FinanceEntry> findAll() {
        List<FinanceEntry> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM finance_entries ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar financeiro", e); }
        return list;
    }
    public List<FinanceEntry> findOverdue() {
        List<FinanceEntry> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM finance_entries WHERE paid=0 AND due_date IS NOT NULL AND due_date < date('now') ORDER BY due_date ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar financeiro vencido", e); }
        return list;
    }
    public int countPending()    { return db.queryInt("SELECT COUNT(*) FROM finance_entries WHERE paid=0"); }
    public double sumPending()   { return db.queryDouble("SELECT COALESCE(SUM(amount),0) FROM finance_entries WHERE paid=0"); }
    private FinanceEntry mapRow(ResultSet rs) throws SQLException {
        String d = rs.getString("due_date");
        return new FinanceEntry(rs.getLong("id"), rs.getString("entry_type"), rs.getString("description"),
                rs.getDouble("amount"), d != null ? LocalDate.parse(d) : null, rs.getInt("paid") == 1);
    }
}
