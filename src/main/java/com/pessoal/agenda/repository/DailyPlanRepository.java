package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.DailyPlan;
import com.pessoal.agenda.model.DailyPlanCapacity;
import com.pessoal.agenda.model.DailyPlanItem;
import com.pessoal.agenda.model.DailyPlanRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DailyPlanRepository {
    private final Database database;

    public DailyPlanRepository(Database database) {
        this.database = database;
    }

    public Optional<DailyPlan> findByDate(LocalDate date) {
        String planSql = "SELECT * FROM daily_plans WHERE plan_date=?";
        try (Connection conn = database.connect();
             PreparedStatement planStatement = conn.prepareStatement(planSql)) {
            planStatement.setString(1, date.toString());
            try (ResultSet plans = planStatement.executeQuery()) {
                if (!plans.next()) return Optional.empty();
                List<DailyPlanItem> items = findItems(conn, date);
                return Optional.of(new DailyPlan(
                        date,
                        DailyPlanCapacity.valueOf(plans.getString("capacity")),
                        Instant.parse(plans.getString("created_at")),
                        parseInstant(plans.getString("closed_at")),
                        plans.getString("closing_note"),
                        items));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar plano diario", e);
        }
    }

    public void save(DailyPlan plan) {
        try (Connection conn = database.connect()) {
            conn.setAutoCommit(false);
            try {
                upsertPlan(conn, plan);
                replaceItems(conn, plan);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar plano diario", e);
        }
    }

    public void delete(LocalDate date) {
        database.execute("DELETE FROM daily_plans WHERE plan_date=?", date.toString());
    }

    private List<DailyPlanItem> findItems(Connection conn, LocalDate date) throws SQLException {
        String sql = """
                SELECT id, task_id, role, position
                FROM daily_plan_items
                WHERE plan_date=?
                ORDER BY CASE role WHEN 'ESSENTIAL' THEN 0 ELSE 1 END, position, id
                """;
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<DailyPlanItem> items = new ArrayList<>();
                while (rows.next()) {
                    items.add(new DailyPlanItem(
                            rows.getLong("id"), date, rows.getLong("task_id"),
                            DailyPlanRole.valueOf(rows.getString("role")),
                            rows.getInt("position")));
                }
                return items;
            }
        }
    }

    private void upsertPlan(Connection conn, DailyPlan plan) throws SQLException {
        String sql = """
                INSERT INTO daily_plans(plan_date, capacity, created_at, closed_at, closing_note)
                VALUES(?,?,?,?,?)
                ON CONFLICT(plan_date) DO UPDATE SET
                    capacity=excluded.capacity,
                    closed_at=excluded.closed_at,
                    closing_note=excluded.closing_note
                """;
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, plan.planDate().toString());
            statement.setString(2, plan.capacity().name());
            statement.setString(3, plan.createdAt().toString());
            statement.setString(4, plan.closedAt() != null ? plan.closedAt().toString() : null);
            statement.setString(5, plan.closingNote());
            statement.executeUpdate();
        }
    }

    private void replaceItems(Connection conn, DailyPlan plan) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM daily_plan_items WHERE plan_date=?")) {
            delete.setString(1, plan.planDate().toString());
            delete.executeUpdate();
        }
        String sql = "INSERT INTO daily_plan_items(plan_date, task_id, role, position) VALUES(?,?,?,?)";
        try (PreparedStatement insert = conn.prepareStatement(sql)) {
            for (DailyPlanItem item : plan.items()) {
                insert.setString(1, plan.planDate().toString());
                insert.setLong(2, item.taskId());
                insert.setString(3, item.role().name());
                insert.setInt(4, item.position());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
