package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.FocusContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class FocusContextRepository {
    private final Database database;

    public FocusContextRepository(Database database) {
        this.database = database;
    }

    public Optional<FocusContext> findCurrent() {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT task_id, resume_note, interrupted_at, updated_at
                     FROM focus_context
                     ORDER BY updated_at DESC, task_id DESC
                     LIMIT 1
                     """);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(map(rows)) : Optional.empty();
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao consultar pista de retomada", error);
        }
    }

    public void replaceCurrent(FocusContext context) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement clear = connection.prepareStatement(
                        "DELETE FROM focus_context")) {
                    clear.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO focus_context(
                            task_id, resume_note, interrupted_at, updated_at
                        ) VALUES(?,?,?,?)
                        """)) {
                    insert.setLong(1, context.taskId());
                    insert.setString(2, context.resumeNote());
                    insert.setString(3, context.interruptedAt().toString());
                    insert.setString(4, context.updatedAt().toString());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao salvar pista de retomada", error);
        }
    }

    public void clearCurrent(long taskId) {
        database.execute("DELETE FROM focus_context WHERE task_id=?", taskId);
    }

    private static FocusContext map(ResultSet rows) throws SQLException {
        return new FocusContext(rows.getLong("task_id"), rows.getString("resume_note"),
                Instant.parse(rows.getString("interrupted_at")),
                Instant.parse(rows.getString("updated_at")));
    }
}
