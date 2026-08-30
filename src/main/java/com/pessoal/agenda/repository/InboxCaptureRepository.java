package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.InboxCapture;
import com.pessoal.agenda.model.InboxCaptureKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InboxCaptureRepository {
    private final Database database;

    public InboxCaptureRepository(Database database) {
        this.database = database;
    }

    public InboxCapture saveUnclassified(String rawText, Instant createdAt) {
        String sql = "INSERT INTO inbox_captures(raw_text, created_at) VALUES(?,?)";
        try (Connection conn = database.connect();
             PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, rawText);
            statement.setString(2, createdAt.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Id da captura nao foi retornado");
                return findById(conn, keys.getLong(1)).orElseThrow(
                        () -> new SQLException("Captura salva nao foi encontrada"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar captura na caixa de entrada", e);
        }
    }

    public Optional<InboxCapture> findById(long id) {
        try (Connection conn = database.connect()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar captura", e);
        }
    }

    public List<InboxCapture> findUnclassified(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Limite deve ser positivo");
        String sql = """
                SELECT * FROM inbox_captures
                WHERE kind='UNCLASSIFIED'
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        try (Connection conn = database.connect();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<InboxCapture> captures = new ArrayList<>();
                while (rows.next()) captures.add(map(rows));
                return captures;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar caixa de entrada", e);
        }
    }

    public int countUnclassified() {
        return database.queryInt("SELECT COUNT(*) FROM inbox_captures WHERE kind='UNCLASSIFIED'");
    }

    public InboxCapture triageToTask(long captureId, String title, String notes,
                                     LocalDate dueDate, Instant triagedAt) {
        return triageWithTarget(captureId, InboxCaptureKind.TASK, triagedAt, conn -> {
            String sql = """
                    INSERT INTO tasks(
                        title, notes, due_date, done, category, schedule_type,
                        priority, status
                    ) VALUES(?,?,?,0,'Geral','SINGLE','NORMAL','PENDENTE')
                    """;
            return insertTarget(conn, sql, title, notes, dueDate.toString());
        });
    }

    public InboxCapture triageToIdea(long captureId, String title, String description,
                                     Instant triagedAt) {
        return triageWithTarget(captureId, InboxCaptureKind.IDEA, triagedAt, conn -> {
            String sql = """
                    INSERT INTO project_ideas(
                        title, description, status, category, priority,
                        idea_type, impact_level, feasibility, estimated_hours
                    ) VALUES(?,?,'nova','Caixa de entrada','NORMAL','GERAL','MEDIO',3,0)
                    """;
            return insertTarget(conn, sql, title, description);
        });
    }

    public InboxCapture markTriaged(long captureId, InboxCaptureKind kind, Instant triagedAt) {
        if (kind != InboxCaptureKind.INTERRUPTION_NOTE && kind != InboxCaptureKind.ARCHIVED) {
            throw new IllegalArgumentException("Tipo sem destino invalido para triagem");
        }
        return triageWithTarget(captureId, kind, triagedAt, conn -> null);
    }

    private InboxCapture triageWithTarget(long captureId, InboxCaptureKind kind,
                                          Instant triagedAt, TargetCreator targetCreator) {
        try (Connection conn = database.connect()) {
            conn.setAutoCommit(false);
            try {
                InboxCapture pending = findById(conn, captureId)
                        .orElseThrow(() -> new IllegalArgumentException("Captura nao encontrada"));
                if (pending.kind() != InboxCaptureKind.UNCLASSIFIED) {
                    throw new IllegalStateException("Captura ja foi triada");
                }

                Long targetId = targetCreator.create(conn);
                String updateSql = """
                        UPDATE inbox_captures
                        SET kind=?, triaged_at=?, target_id=?
                        WHERE id=? AND kind='UNCLASSIFIED'
                        """;
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setString(1, kind.name());
                    update.setString(2, triagedAt.toString());
                    if (targetId == null) update.setNull(3, java.sql.Types.BIGINT);
                    else update.setLong(3, targetId);
                    update.setLong(4, captureId);
                    if (update.executeUpdate() != 1) {
                        throw new IllegalStateException("Captura foi alterada durante a triagem");
                    }
                }
                InboxCapture triaged = findById(conn, captureId).orElseThrow();
                conn.commit();
                return triaged;
            } catch (SQLException | RuntimeException error) {
                conn.rollback();
                throw error;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao triar captura", e);
        }
    }

    private static long insertTarget(Connection conn, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < values.length; index++) {
                insert.setObject(index + 1, values[index]);
            }
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Destino da captura nao retornou id");
                return keys.getLong(1);
            }
        }
    }

    private Optional<InboxCapture> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT * FROM inbox_captures WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private static InboxCapture map(ResultSet row) throws SQLException {
        long targetIdValue = row.getLong("target_id");
        Long targetId = row.wasNull() ? null : targetIdValue;
        return new InboxCapture(
                row.getLong("id"),
                row.getString("raw_text"),
                InboxCaptureKind.valueOf(row.getString("kind")),
                Instant.parse(row.getString("created_at")),
                parseInstant(row.getString("triaged_at")),
                targetId);
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    @FunctionalInterface
    private interface TargetCreator {
        Long create(Connection connection) throws SQLException;
    }
}
