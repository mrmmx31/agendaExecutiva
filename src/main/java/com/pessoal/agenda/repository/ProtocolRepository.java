package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de Protocolos Operacionais.
 *
 * Gerencia 4 tabelas:
 *   protocols                — templates (definições)
 *   protocol_steps           — passos dos templates
 *   protocol_executions      — instâncias de execução
 *   protocol_execution_steps — estado dos passos em cada execução
 */
public class ProtocolRepository {

    private final Database db;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ProtocolRepository(Database db) { this.db = db; }

    // ══════════════════════════════════════════════════════════════════════
    // Templates (Protocolos)
    // ══════════════════════════════════════════════════════════════════════

    public long saveProtocol(String name, ProtocolExecutionType type,
                             String category, String description,
                             Long linkedTaskId, int validityDays) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO protocols(name, execution_type, category, description, linked_task_id, validity_days)"
                + " VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, type.name());
            ps.setString(3, category != null ? category : "Geral");
            ps.setString(4, description);
            ps.setObject(5, linkedTaskId);
            ps.setInt(6, validityDays);
            ps.executeUpdate();
            try (ResultSet rk = ps.getGeneratedKeys()) { return rk.next() ? rk.getLong(1) : -1; }
        } catch (SQLException e) { throw new RuntimeException("Erro ao salvar protocolo", e); }
    }

    /** Compatibilidade retroativa sem validity_days. */
    public long saveProtocol(String name, ProtocolExecutionType type,
                             String category, String description, Long linkedTaskId) {
        return saveProtocol(name, type, category, description, linkedTaskId, 0);
    }

    public void updateProtocol(long id, String name, ProtocolExecutionType type,
                               String category, String description,
                               Long linkedTaskId, int validityDays) {
        db.execute("UPDATE protocols SET name=?, execution_type=?, category=?, description=?, linked_task_id=?, validity_days=? WHERE id=?",
            name, type.name(), category != null ? category : "Geral",
            description, linkedTaskId, validityDays, id);
    }

    /** Compatibilidade retroativa. */
    public void updateProtocol(long id, String name, ProtocolExecutionType type,
                               String category, String description, Long linkedTaskId) {
        updateProtocol(id, name, type, category, description, linkedTaskId, 0);
    }

    public void deleteProtocol(long id) {
        db.execute(
            "DELETE FROM protocol_execution_steps WHERE execution_id IN "
            + "(SELECT id FROM protocol_executions WHERE template_id=?)", id);
        db.execute("DELETE FROM protocol_executions WHERE template_id=?", id);
        db.execute("DELETE FROM protocol_steps WHERE template_id=?", id);
        db.execute("DELETE FROM protocols WHERE id=?", id);
    }

    /**
     * Lista protocolos com filtros combinados.
     *
     * @param categoryFilter  categoria exata ou null para todas
     * @param typeFilter      nome do enum {@link ProtocolExecutionType} ou null para todos
     * @param statusFilter    "COM_ATIVA" | "SEM_ATIVA" | "VALIDADE_VENCIDA" | "VENCE_7DIAS" | null
     */
    public List<Protocol> findAllProtocols(String categoryFilter,
                                           String typeFilter,
                                           String statusFilter) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (categoryFilter != null && !categoryFilter.isBlank()) {
            where.append(" AND p.category=?");
            params.add(categoryFilter);
        }
        if (typeFilter != null && !typeFilter.isBlank()) {
            where.append(" AND p.execution_type=?");
            params.add(typeFilter);
        }
        if (statusFilter != null) {
            switch (statusFilter) {
                case "COM_ATIVA" ->
                    where.append(" AND EXISTS (SELECT 1 FROM protocol_executions e"
                            + " WHERE e.template_id=p.id AND e.status='ATIVA')");
                case "SEM_ATIVA" ->
                    where.append(" AND NOT EXISTS (SELECT 1 FROM protocol_executions e"
                            + " WHERE e.template_id=p.id AND e.status='ATIVA')");
                case "VALIDADE_VENCIDA" ->
                    where.append(" AND p.validity_days > 0"
                            + " AND EXISTS (SELECT 1 FROM protocol_executions e"
                            + "   WHERE e.template_id=p.id AND e.status='CONCLUIDA')"
                            + " AND date((SELECT MAX(e2.completed_at) FROM protocol_executions e2"
                            + "   WHERE e2.template_id=p.id AND e2.status='CONCLUIDA'),"
                            + "   '+' || p.validity_days || ' days') < date('now')");
                case "VENCE_7DIAS" ->
                    where.append(" AND p.validity_days > 0"
                            + " AND EXISTS (SELECT 1 FROM protocol_executions e"
                            + "   WHERE e.template_id=p.id AND e.status='CONCLUIDA')"
                            + " AND date((SELECT MAX(e2.completed_at) FROM protocol_executions e2"
                            + "   WHERE e2.template_id=p.id AND e2.status='CONCLUIDA'),"
                            + "   '+' || p.validity_days || ' days')"
                            + " BETWEEN date('now') AND date('now', '+7 days')");
                default -> { /* sem filtro extra */ }
            }
        }

        String sql = "SELECT p.*, t.title AS linked_task_title FROM protocols p"
                + " LEFT JOIN tasks t ON t.id = p.linked_task_id"
                + (where.length() > 0 ? " WHERE 1=1 " + where : "")
                + " ORDER BY p.name ASC";

        List<Protocol> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapProtocol(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar protocolos", e); }
        return list;
    }

    /** Retrocompatibilidade — apenas filtro de categoria. */
    public List<Protocol> findAllProtocols(String categoryFilter) {
        return findAllProtocols(categoryFilter, null, null);
    }

    /** Retorna o título da tarefa vinculada, ou null. */
    public String findLinkedTaskTitle(long taskId) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT title FROM tasks WHERE id=?")) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("title");
            }
        } catch (SQLException e) { /* ignora */ }
        return null;
    }

    public Optional<Protocol> findProtocolById(long id) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM protocols WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapProtocol(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar protocolo", e); }
        return Optional.empty();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Passos do Template
    // ══════════════════════════════════════════════════════════════════════

    public void saveStep(long templateId, int order, String text, String notes, boolean critical) {
        db.execute(
            "INSERT INTO protocol_steps(template_id, step_order, step_text, notes, critical) VALUES(?,?,?,?,?)",
            templateId, order, text, notes != null ? notes : "", critical ? 1 : 0);
    }

    public void deleteStep(long stepId) {
        db.execute("DELETE FROM protocol_steps WHERE id=?", stepId);
    }

    public void deleteAllSteps(long templateId) {
        db.execute("DELETE FROM protocol_steps WHERE template_id=?", templateId);
    }

    public List<ProtocolStep> findSteps(long templateId) {
        List<ProtocolStep> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM protocol_steps WHERE template_id=? ORDER BY step_order ASC")) {
            ps.setLong(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapStep(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar passos", e); }
        return list;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Execuções
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Inicia uma nova execução: copia os passos do template como snapshot.
     * @return id da execução criada
     */
    public long startExecution(long templateId) {
        String now = LocalDateTime.now().format(DT);

        // Calcula número da iteração
        int iteration = db.queryInt(
            "SELECT COUNT(*) + 1 FROM protocol_executions WHERE template_id=?", templateId);

        long execId;
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO protocol_executions(template_id, started_at, status, iteration_number)"
                + " VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, templateId);
            ps.setString(2, now);
            ps.setString(3, "ATIVA");
            ps.setInt(4, iteration);
            ps.executeUpdate();
            try (ResultSet rk = ps.getGeneratedKeys()) { execId = rk.next() ? rk.getLong(1) : -1; }
        } catch (SQLException e) { throw new RuntimeException("Erro ao iniciar execução", e); }

        // Snapshot dos passos do template nesta execução
        for (ProtocolStep s : findSteps(templateId)) {
            db.execute(
                "INSERT INTO protocol_execution_steps"
                + "(execution_id, step_id, step_text, step_notes, critical, step_order)"
                + " VALUES(?,?,?,?,?,?)",
                execId, s.id(), s.stepText(), s.notes() != null ? s.notes() : "",
                s.critical() ? 1 : 0, s.stepOrder());
        }
        return execId;
    }

    public void checkStep(long execStepId, String observationNotes) {
        db.execute(
            "UPDATE protocol_execution_steps SET checked=1, checked_at=?, observation_notes=? WHERE id=?",
            LocalDateTime.now().format(DT),
            observationNotes != null ? observationNotes : "",
            execStepId);
    }

    public void uncheckStep(long execStepId) {
        db.execute(
            "UPDATE protocol_execution_steps SET checked=0, checked_at=NULL, observation_notes=NULL WHERE id=?",
            execStepId);
    }

    public void completeExecution(long execId, String notes) {
        db.execute(
            "UPDATE protocol_executions SET status='CONCLUIDA', completed_at=?, notes=? WHERE id=?",
            LocalDateTime.now().format(DT), notes != null ? notes : "", execId);
    }

    public void cancelExecution(long execId) {
        db.execute("UPDATE protocol_executions SET status='CANCELADA', completed_at=? WHERE id=?",
            LocalDateTime.now().format(DT), execId);
    }

    public Optional<ProtocolExecution> findActiveExecution(long templateId) {
        return findExecutions(templateId, "ATIVA").stream().findFirst();
    }

    /** @param status null = todos os status */
    public List<ProtocolExecution> findExecutions(long templateId, String status) {
        String sql =
            "SELECT e.*, "
            + "(SELECT p.name FROM protocols p WHERE p.id=e.template_id) AS template_name, "
            + "(SELECT p.execution_type FROM protocols p WHERE p.id=e.template_id) AS exec_type, "
            + "(SELECT COUNT(*) FROM protocol_execution_steps s WHERE s.execution_id=e.id) AS total_steps, "
            + "(SELECT COUNT(*) FROM protocol_execution_steps s WHERE s.execution_id=e.id AND s.checked=1) AS checked_steps "
            + "FROM protocol_executions e WHERE e.template_id=? "
            + (status != null ? "AND e.status=? " : "")
            + "ORDER BY e.started_at DESC";
        List<ProtocolExecution> list = new ArrayList<>();
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, templateId);
            if (status != null) ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapExecution(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar execuções", e); }
        return list;
    }

    public List<ProtocolExecutionStep> findExecutionSteps(long execId) {
        List<ProtocolExecutionStep> list = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM protocol_execution_steps WHERE execution_id=? ORDER BY step_order ASC")) {
            ps.setLong(1, execId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapExecStep(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar passos", e); }
        return list;
    }

    /** Conta execuções ativas (para dashboard). */
    public int countActiveExecutions() {
        return db.queryInt("SELECT COUNT(*) FROM protocol_executions WHERE status='ATIVA'");
    }

    /** Conta execuções ativas de um protocolo específico. */
    public int countActiveExecutionsOf(long templateId) {
        return db.queryInt(
            "SELECT COUNT(*) FROM protocol_executions WHERE template_id=? AND status='ATIVA'",
            templateId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mapeamento
    // ══════════════════════════════════════════════════════════════════════

    private Protocol mapProtocol(ResultSet rs) throws SQLException {
        String typeStr    = rs.getString("execution_type");
        String createdStr = rs.getString("created_at");
        Object linkedId   = rs.getObject("linked_task_id");
        // linked_task_title só existe em queries com LEFT JOIN — ignora se ausente
        String linkedTitle = null;
        try { linkedTitle = rs.getString("linked_task_title"); } catch (SQLException ignored) {}
        return new Protocol(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("category"),
            typeStr != null ? ProtocolExecutionType.valueOf(typeStr) : ProtocolExecutionType.RECORRENTE,
            rs.getString("description"),
            linkedId != null ? ((Number) linkedId).longValue() : null,
            linkedTitle,
            rs.getInt("validity_days"),
            parseDateTime(createdStr)
        );
    }

    /**
     * Calcula a próxima data de execução com base na última conclusão + validity_days.
     * Retorna null se o protocolo não tiver validity_days configurado ou nenhuma execução concluída.
     */
    public java.time.LocalDate nextDueDate(long templateId, int validityDays) {
        if (validityDays <= 0) return null;
        String sql =
            "SELECT completed_at FROM protocol_executions "
            + "WHERE template_id=? AND status='CONCLUIDA' ORDER BY completed_at DESC LIMIT 1";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime last = parseDateTime(rs.getString("completed_at"));
                    if (last != null)
                        return last.toLocalDate().plusDays(validityDays);
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao calcular próxima data", e); }
        return null;
    }

    private ProtocolStep mapStep(ResultSet rs) throws SQLException {
        return new ProtocolStep(
            rs.getLong("id"),
            rs.getLong("template_id"),
            rs.getInt("step_order"),
            rs.getString("step_text"),
            rs.getString("notes"),
            rs.getInt("critical") == 1
        );
    }

    private ProtocolExecution mapExecution(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("exec_type");
        return new ProtocolExecution(
            rs.getLong("id"),
            rs.getLong("template_id"),
            rs.getString("template_name"),
            typeStr != null ? ProtocolExecutionType.valueOf(typeStr) : ProtocolExecutionType.RECORRENTE,
            rs.getInt("iteration_number"),
            parseDateTime(rs.getString("started_at")),
            parseDateTime(rs.getString("completed_at")),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getInt("total_steps"),
            rs.getInt("checked_steps")
        );
    }

    private ProtocolExecutionStep mapExecStep(ResultSet rs) throws SQLException {
        return new ProtocolExecutionStep(
            rs.getLong("id"),
            rs.getLong("execution_id"),
            rs.getLong("step_id"),
            rs.getString("step_text"),
            rs.getString("step_notes"),
            rs.getInt("critical") == 1,
            rs.getInt("step_order"),
            rs.getInt("checked") == 1,
            parseDateTime(rs.getString("checked_at")),
            rs.getString("observation_notes")
        );
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s, DT); } catch (Exception e) { return null; }
    }
}






