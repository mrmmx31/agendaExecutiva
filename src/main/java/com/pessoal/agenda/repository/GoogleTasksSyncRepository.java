package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

public class GoogleTasksSyncRepository {
    private final Database database;
    private final Runnable afterTaskInsert;

    public GoogleTasksSyncRepository(Database database) {
        this(database, () -> {});
    }

    GoogleTasksSyncRepository(Database database, Runnable afterTaskInsert) {
        this.database = database;
        this.afterTaskInsert = afterTaskInsert;
    }

    public ImportResult importTask(String googleListId, String googleTaskId,
                                   String title, String notes, LocalDate dueDate,
                                   boolean completed) {
        return importTask(googleListId, googleTaskId, title, notes, dueDate,
                completed, null);
    }

    public ImportResult importTask(String googleListId, String googleTaskId,
                                   String title, String notes, LocalDate dueDate,
                                   boolean completed, String googleUpdatedAt) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                Long existingId = findMappedLocalId(connection, googleListId, googleTaskId);
                if (existingId != null) {
                    connection.commit();
                    return new ImportResult(existingId, false);
                }

                long localTaskId = insertTask(
                        connection, title, notes, dueDate, completed);
                afterTaskInsert.run();
                insertMapping(connection, localTaskId, googleListId, googleTaskId,
                        normalizedTitle(title), notes, dueDate, completed, googleUpdatedAt);
                connection.commit();
                return new ImportResult(localTaskId, true);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao importar tarefa Google de forma atômica", error);
        }
    }

    private static Long findMappedLocalId(Connection connection, String googleListId,
                                          String googleTaskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT local_task_id FROM google_tasks_mapping
                WHERE google_list_id=? AND google_task_id=?
                """)) {
            statement.setString(1, googleListId);
            statement.setString(2, googleTaskId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        }
    }

    private static long insertTask(Connection connection, String title, String notes,
                                   LocalDate dueDate, boolean completed) throws SQLException {
        String sql = """
                INSERT INTO tasks(
                    title, notes, due_date, done, category, schedule_type,
                    priority, status
                ) VALUES(?,?,?,?,?,'SINGLE','NORMAL',?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalizedTitle(title));
            statement.setString(2, notes);
            statement.setString(3, dueDate.toString());
            statement.setInt(4, completed ? 1 : 0);
            statement.setString(5, "Google Tasks");
            statement.setString(6, completed ? "CONCLUIDA" : "PENDENTE");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Tarefa importada não retornou ID");
                return keys.getLong(1);
            }
        }
    }

    private static void insertMapping(Connection connection, long localTaskId,
                                      String googleListId, String googleTaskId,
                                      String title, String notes, LocalDate dueDate,
                                      boolean completed, String googleUpdatedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO google_tasks_mapping(
                    local_task_id, google_list_id, google_task_id, last_synced_at,
                    synced_title, synced_notes, synced_due_date, synced_done,
                    google_updated_at, sync_state
                ) VALUES(?,?,?,datetime('now'),?,?,?,?,?,'ACTIVE')
                """)) {
            statement.setLong(1, localTaskId);
            statement.setString(2, googleListId);
            statement.setString(3, googleTaskId);
            statement.setString(4, title);
            statement.setString(5, notes == null || notes.isBlank() ? null : notes);
            statement.setString(6, dueDate.toString());
            statement.setInt(7, completed ? 1 : 0);
            statement.setString(8, googleUpdatedAt);
            statement.executeUpdate();
        }
    }

    private static String normalizedTitle(String title) {
        return title == null || title.isBlank() ? "(sem título)" : title;
    }

    public record ImportResult(long localTaskId, boolean created) {}
}
