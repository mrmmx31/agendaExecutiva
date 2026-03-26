package com.pessoal.agenda.repository;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Acesso a dados da entidade Task. Sem lógica de negócio. */
public class TaskRepository {
    private final Database db;
    public TaskRepository(Database db) { this.db = db; }

    // ── INSERT ────────────────────────────────────────────────────────────

    /** Compatibilidade retroativa. */
    public void save(String title, String notes, LocalDate dueDate, String category) {
        save(title, notes, dueDate, category, ScheduleType.SINGLE, null, null);
    }

    /** Compatibilidade com agendamento (sem tempo/prioridade). */
    public void save(String title, String notes, LocalDate dueDate, String category,
                     ScheduleType scheduleType, LocalDate endDate, String recurrenceDays) {
        save(title, notes, dueDate, category, scheduleType, endDate, recurrenceDays,
             null, null, TaskPriority.NORMAL, TaskStatus.PENDENTE);
    }

    /** Insere tarefa com todos os campos. */
    public void save(String title, String notes, LocalDate dueDate, String category,
                     ScheduleType scheduleType, LocalDate endDate, String recurrenceDays,
                     String startTime, String endTime, TaskPriority priority, TaskStatus status) {
        db.execute(
            "INSERT INTO tasks(title,notes,due_date,done,category,schedule_type,"
            + "end_date,recurrence_days,start_time,end_time,priority,status)"
            + " VALUES(?,?,?,0,?,?,?,?,?,?,?,?)",
            title, notes, dueDate.toString(),
            category != null ? category : "Geral",
            (scheduleType != null ? scheduleType : ScheduleType.SINGLE).name(),
            endDate != null ? endDate.toString() : null,
            recurrenceDays, startTime, endTime,
            (priority  != null ? priority  : TaskPriority.NORMAL).name(),
            (status    != null ? status    : TaskStatus.PENDENTE).name());
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    public void markDone(long id) {
        db.execute("UPDATE tasks SET done=1, status='CONCLUIDA' WHERE id=?", id);
    }

    public void update(long id, String title, String notes, LocalDate dueDate, String category,
                       ScheduleType scheduleType, LocalDate endDate, String recurrenceDays,
                       String startTime, String endTime, TaskPriority priority, TaskStatus status) {
        db.execute(
            "UPDATE tasks SET title=?,notes=?,due_date=?,category=?,schedule_type=?,"
            + "end_date=?,recurrence_days=?,start_time=?,end_time=?,priority=?,status=?"
            + " WHERE id=?",
            title, notes, dueDate.toString(),
            category != null ? category : "Geral",
            (scheduleType != null ? scheduleType : ScheduleType.SINGLE).name(),
            endDate != null ? endDate.toString() : null,
            recurrenceDays, startTime, endTime,
            (priority  != null ? priority  : TaskPriority.NORMAL).name(),
            (status    != null ? status    : TaskStatus.PENDENTE).name(),
            id);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public void deleteById(long id) {
        db.execute("DELETE FROM tasks WHERE id=?", id);
    }

    // ── QUERY ─────────────────────────────────────────────────────────────

    public Optional<Task> findById(long id) {
        List<Task> list = query("SELECT * FROM tasks WHERE id=?", new Object[]{id});
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private static final String DAY_ACTIVE_CLAUSE =
        " (   (schedule_type = 'SINGLE' AND due_date = ?)"
        + " OR (schedule_type = 'RANGE'  AND due_date <= ? AND end_date >= ?)"
        + " OR (schedule_type = 'WEEKLY' AND due_date <= ? AND end_date >= ?"
        + "     AND instr(',' || recurrence_days || ',', ',' || CAST(strftime('%w', ?) AS TEXT) || ',') > 0)"
        + " ) ";

    public List<Task> findByDay(LocalDate date, String categoryFilter) {
        String d = date.toString();
        String sql = "SELECT * FROM tasks WHERE" + DAY_ACTIVE_CLAUSE
                   + catClause(categoryFilter) + " ORDER BY start_time ASC NULLS LAST, done ASC, id ASC";
        Object[] params = categoryFilter == null
            ? new Object[]{d,d,d,d,d,d}
            : new Object[]{d,d,d,d,d,d, categoryFilter};
        return query(sql, params);
    }

    public List<Task> findByMonth(YearMonth month, String categoryFilter) {
        String start = month.atDay(1).toString(), end = month.atEndOfMonth().toString();
        String sql = "SELECT * FROM tasks WHERE ("
            + "  (schedule_type = 'SINGLE' AND due_date >= ? AND due_date <= ?)"
            + "  OR (schedule_type IN ('RANGE','WEEKLY') AND due_date <= ? AND end_date >= ?)"
            + ")" + catClause(categoryFilter)
            + " ORDER BY due_date ASC, start_time ASC NULLS LAST, done ASC";
        Object[] params = categoryFilter == null
            ? new Object[]{start,end,end,start}
            : new Object[]{start,end,end,start, categoryFilter};
        return query(sql, params);
    }

    public List<MonthSummary> yearOverview(int year, String categoryFilter) {
        String sql = "SELECT CAST(strftime('%m', due_date) AS INTEGER) as m, COUNT(*) as total, "
                + "SUM(CASE WHEN done=0 THEN 1 ELSE 0 END) as open_count "
                + "FROM tasks WHERE due_date >= ? AND due_date <= ?"
                + catClause(categoryFilter) + " GROUP BY m";
        Object[] params = categoryFilter == null
                ? new Object[]{year+"-01-01", year+"-12-31"}
                : new Object[]{year+"-01-01", year+"-12-31", categoryFilter};
        int[] totals = new int[13], opens = new int[13];
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i+1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int m = rs.getInt("m");
                    if (m >= 1 && m <= 12) { totals[m] = rs.getInt("total"); opens[m] = rs.getInt("open_count"); }
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao obter visao anual", e); }
        String[] names = {"Janeiro","Fevereiro","Marco","Abril","Maio","Junho",
                          "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"};
        List<MonthSummary> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) result.add(new MonthSummary(m, names[m-1], totals[m], opens[m]));
        return result;
    }

    public List<Task> findOverdue() {
        return query(
            "SELECT * FROM tasks WHERE done=0 AND status != 'CANCELADA' AND ("
            + "  (schedule_type = 'SINGLE' AND due_date < date('now'))"
            + "  OR (schedule_type IN ('RANGE','WEEKLY') AND end_date < date('now'))"
            + ") ORDER BY due_date ASC", new Object[0]);
    }

    public int countOpen()    { return db.queryInt("SELECT COUNT(*) FROM tasks WHERE done=0 AND status != 'CANCELADA'"); }

    /** Lista tarefas abertas para o seletor de vínculo em protocolos do tipo TAREFA. */
    public List<Task> findOpenTasks() {
        return query(
            "SELECT * FROM tasks WHERE done=0 AND status NOT IN ('CANCELADA','CONCLUIDA')"
            + " ORDER BY due_date ASC, title ASC", new Object[0]);
    }
    public int countOverdue() {
        return db.queryInt(
            "SELECT COUNT(*) FROM tasks WHERE done=0 AND status != 'CANCELADA' AND ("
            + "  (schedule_type = 'SINGLE' AND due_date < date('now'))"
            + "  OR (schedule_type IN ('RANGE','WEEKLY') AND end_date < date('now'))"
            + ")");
    }

    // ── helpers ───────────────────────────────────────────────────────────
    private String catClause(String f) { return f != null ? " AND category=?" : ""; }

    private List<Task> query(String sql, Object[] params) {
        List<Task> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i+1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao consultar tarefas", e); }
        return list;
    }

    private Task mapRow(ResultSet rs) throws SQLException {
        String d        = rs.getString("due_date");
        String endStr   = rs.getString("end_date");
        String schedStr = rs.getString("schedule_type");
        String prioStr  = rs.getString("priority");
        String statStr  = rs.getString("status");
        return new Task(
            rs.getLong("id"), rs.getString("title"), rs.getString("notes"),
            d != null ? LocalDate.parse(d) : LocalDate.now(),
            rs.getInt("done") == 1,
            rs.getString("category"),
            schedStr != null ? ScheduleType.valueOf(schedStr) : ScheduleType.SINGLE,
            endStr   != null ? LocalDate.parse(endStr) : null,
            rs.getString("recurrence_days"),
            rs.getString("start_time"),
            rs.getString("end_time"),
            prioStr  != null ? TaskPriority.valueOf(prioStr) : TaskPriority.NORMAL,
            statStr  != null ? TaskStatus.valueOf(statStr)   : TaskStatus.PENDENTE);
    }
}
