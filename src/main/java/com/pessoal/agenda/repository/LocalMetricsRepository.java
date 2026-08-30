package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.LocalMetricType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LocalMetricsRepository {
    private static final int MAX_EVENTS_PER_TYPE = 200;
    private final Database database;

    public LocalMetricsRepository(Database database) {
        this.database = database;
    }

    public void save(LocalMetricType type, long value, Instant occurredAt) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO local_metric_events(metric_type,metric_value,occurred_at)
                        VALUES(?,?,?)
                        """)) {
                    insert.setString(1, type.name());
                    insert.setLong(2, value);
                    insert.setString(3, occurredAt.toString());
                    insert.executeUpdate();
                }
                try (PreparedStatement trim = connection.prepareStatement("""
                        DELETE FROM local_metric_events
                        WHERE id IN (
                            SELECT id FROM local_metric_events
                            WHERE metric_type=?
                            ORDER BY occurred_at DESC, id DESC
                            LIMIT -1 OFFSET ?
                        )
                        """)) {
                    trim.setString(1, type.name());
                    trim.setInt(2, MAX_EVENTS_PER_TYPE);
                    trim.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao salvar métrica local", error);
        }
    }

    public List<Long> findRecentValues(LocalMetricType type, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Limite deve ser positivo");
        String sql = """
                SELECT metric_value FROM local_metric_events
                WHERE metric_type=?
                ORDER BY occurred_at DESC, id DESC
                LIMIT ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<Long> values = new ArrayList<>();
                while (rows.next()) values.add(rows.getLong(1));
                return values;
            }
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao consultar métricas locais", error);
        }
    }

    public void deleteAll() {
        database.execute("DELETE FROM local_metric_events");
    }
}
