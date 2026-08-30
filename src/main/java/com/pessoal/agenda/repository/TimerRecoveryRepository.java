package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.TimerRecovery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class TimerRecoveryRepository {
    private final Database database;

    public TimerRecoveryRepository(Database database) {
        this.database = database;
    }

    public Optional<TimerRecovery> findPending() {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT task_id, elapsed_seconds, was_running, updated_at
                     FROM timer_recovery
                     WHERE singleton_id=1
                     """);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) return Optional.empty();
            return Optional.of(new TimerRecovery(
                    rows.getLong("task_id"),
                    rows.getLong("elapsed_seconds"),
                    rows.getInt("was_running") != 0,
                    Instant.parse(rows.getString("updated_at"))));
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao consultar recuperação do timer", error);
        }
    }

    public void save(TimerRecovery recovery) {
        database.execute("""
                INSERT INTO timer_recovery(
                    singleton_id, task_id, elapsed_seconds, was_running, updated_at
                ) VALUES(1,?,?,?,?)
                ON CONFLICT(singleton_id) DO UPDATE SET
                    task_id=excluded.task_id,
                    elapsed_seconds=excluded.elapsed_seconds,
                    was_running=excluded.was_running,
                    updated_at=excluded.updated_at
                """, recovery.taskId(), recovery.elapsedSeconds(),
                recovery.wasRunning() ? 1 : 0, recovery.updatedAt().toString());
    }

    public void clear() {
        database.execute("DELETE FROM timer_recovery WHERE singleton_id=1");
    }
}
