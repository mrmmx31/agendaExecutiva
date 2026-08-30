package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.DayReviewDecision;
import com.pessoal.agenda.model.ScheduleType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Escrita atômica das decisões tomadas no encerramento do dia. */
public class DayReviewRepository {
    private final Database database;

    public DayReviewRepository(Database database) {
        this.database = database;
    }

    public void applyAndClose(LocalDate date, Map<Long, DayReviewDecision> decisions,
                              Long tomorrowInitialTaskId, String closingNote, Instant closedAt) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                ensureOpenPlan(connection, date);
                Map<Long, TaskState> tasks = loadOpenPlanTasks(connection, date);
                if (!tasks.keySet().equals(decisions.keySet())) {
                    throw new IllegalStateException("Os itens abertos mudaram durante a revisão");
                }

                LocalDate tomorrow = date.plusDays(1);
                for (Map.Entry<Long, DayReviewDecision> entry : decisions.entrySet()) {
                    applyDecision(connection, tasks.get(entry.getKey()), entry.getValue(),
                            tomorrow, closedAt);
                }
                if (tomorrowInitialTaskId != null) {
                    prepareTomorrow(connection, tomorrow, tomorrowInitialTaskId, closedAt);
                }
                closePlan(connection, date, closingNote, closedAt);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RuntimeException("Erro ao aplicar revisão do dia", error);
        }
    }

    private static void ensureOpenPlan(Connection connection, LocalDate date) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT closed_at FROM daily_plans WHERE plan_date=?")) {
            statement.setString(1, date.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Não há plano para encerrar");
                if (row.getString("closed_at") != null) {
                    throw new IllegalStateException("O dia já foi encerrado");
                }
            }
        }
    }

    private static Map<Long, TaskState> loadOpenPlanTasks(Connection connection, LocalDate date)
            throws SQLException {
        String sql = """
                SELECT t.id, t.title, t.notes, t.due_date, t.schedule_type,
                       t.end_date, t.recurrence_days
                FROM daily_plan_items dpi
                JOIN tasks t ON t.id=dpi.task_id
                WHERE dpi.plan_date=?
                  AND t.done=0 AND t.status NOT IN ('CONCLUIDA','CANCELADA')
                ORDER BY CASE dpi.role WHEN 'ESSENTIAL' THEN 0 ELSE 1 END,
                         dpi.position, dpi.id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            try (ResultSet rows = statement.executeQuery()) {
                Map<Long, TaskState> tasks = new java.util.LinkedHashMap<>();
                while (rows.next()) {
                    long id = rows.getLong("id");
                    tasks.put(id, new TaskState(
                            id, rows.getString("title"), rows.getString("notes"),
                            LocalDate.parse(rows.getString("due_date")),
                            ScheduleType.valueOf(rows.getString("schedule_type")),
                            parseDate(rows.getString("end_date")),
                            rows.getString("recurrence_days")));
                }
                return tasks;
            }
        }
    }

    private static void applyDecision(Connection connection, TaskState task,
                                      DayReviewDecision decision, LocalDate tomorrow,
                                      Instant changedAt) throws SQLException {
        switch (decision) {
            case KEEP_DATE -> { }
            case COMPLETE -> updateOne(connection,
                    "UPDATE tasks SET done=1, status='CONCLUIDA' WHERE id=? AND done=0",
                    task.id());
            case RETURN_TO_INBOX -> {
                String rawText = task.notes() == null || task.notes().isBlank()
                        ? task.title()
                        : task.title() + "\n" + task.notes();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO inbox_captures(raw_text, created_at) VALUES(?,?)")) {
                    insert.setString(1, rawText);
                    insert.setString(2, changedAt.toString());
                    insert.executeUpdate();
                }
                updateOne(connection,
                        "UPDATE tasks SET status='CANCELADA' WHERE id=? AND done=0",
                        task.id());
            }
            case TOMORROW -> reschedule(connection, task, tomorrow);
        }
    }

    private static void reschedule(Connection connection, TaskState task, LocalDate tomorrow)
            throws SQLException {
        long shift = ChronoUnit.DAYS.between(task.dueDate(), tomorrow);
        LocalDate shiftedEnd = task.endDate() != null ? task.endDate().plusDays(shift) : null;
        String recurrenceDays = task.recurrenceDays();
        if (task.scheduleType() == ScheduleType.WEEKLY) {
            recurrenceDays = includeDay(recurrenceDays, tomorrow.getDayOfWeek());
        }
        String sql = """
                UPDATE tasks
                SET due_date=?, end_date=?, recurrence_days=?
                WHERE id=? AND done=0 AND status NOT IN ('CONCLUIDA','CANCELADA')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tomorrow.toString());
            statement.setString(2, shiftedEnd != null ? shiftedEnd.toString() : null);
            statement.setString(3, recurrenceDays);
            statement.setLong(4, task.id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("A tarefa mudou durante a revisão: " + task.title());
            }
        }
    }

    private static void prepareTomorrow(Connection connection, LocalDate tomorrow, long taskId,
                                        Instant createdAt) throws SQLException {
        Long existingEssential = findEssential(connection, tomorrow);
        if (existingEssential != null) {
            if (existingEssential == taskId) return;
            throw new IllegalStateException("Amanhã já possui uma tarefa inicial diferente");
        }
        if (planExists(connection, tomorrow)) {
            throw new IllegalStateException("Amanhã já possui um plano sem tarefa inicial");
        }
        try (PreparedStatement plan = connection.prepareStatement(
                "INSERT INTO daily_plans(plan_date,capacity,created_at) VALUES(?,'REDUCED',?)")) {
            plan.setString(1, tomorrow.toString());
            plan.setString(2, createdAt.toString());
            plan.executeUpdate();
        }
        try (PreparedStatement item = connection.prepareStatement("""
                INSERT INTO daily_plan_items(plan_date,task_id,role,position)
                VALUES(?,?,'ESSENTIAL',0)
                """)) {
            item.setString(1, tomorrow.toString());
            item.setLong(2, taskId);
            item.executeUpdate();
        }
    }

    private static void closePlan(Connection connection, LocalDate date, String note,
                                  Instant closedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE daily_plans SET closed_at=?, closing_note=?
                WHERE plan_date=? AND closed_at IS NULL
                """)) {
            statement.setString(1, closedAt.toString());
            statement.setString(2, note);
            statement.setString(3, date.toString());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("O plano mudou durante o encerramento");
            }
        }
    }

    private static Long findEssential(Connection connection, LocalDate date) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT task_id FROM daily_plan_items
                WHERE plan_date=? AND role='ESSENTIAL'
                """)) {
            statement.setString(1, date.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : null;
            }
        }
    }

    private static boolean planExists(Connection connection, LocalDate date) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM daily_plans WHERE plan_date=?")) {
            statement.setString(1, date.toString());
            return statement.executeQuery().next();
        }
    }

    private static void updateOne(Connection connection, String sql, long taskId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("A tarefa mudou durante a revisão");
            }
        }
    }

    private static String includeDay(String value, DayOfWeek day) {
        Set<Integer> days = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            for (String token : value.split(",")) {
                if (!token.isBlank()) days.add(Integer.parseInt(token.trim()));
            }
        }
        days.add(day.getValue() % 7);
        List<Integer> sorted = new ArrayList<>(days);
        sorted.sort(Comparator.naturalOrder());
        return String.join(",", sorted.stream().map(String::valueOf).toList());
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private record TaskState(long id, String title, String notes, LocalDate dueDate,
                             ScheduleType scheduleType, LocalDate endDate,
                             String recurrenceDays) { }
}
