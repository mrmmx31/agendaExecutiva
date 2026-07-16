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
                             Long linkedTaskId, int validityDays,
                             String timingMode, String fixedTime, Integer leadMinutes) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO protocols(name, execution_type, category, description, linked_task_id, validity_days, timing_mode, fixed_time, lead_minutes)"
                + " VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, type.name());
            ps.setString(3, category != null ? category : "Geral");
            ps.setString(4, description);
            ps.setObject(5, linkedTaskId);
            ps.setInt(6, validityDays);
            ps.setString(7, timingMode != null ? timingMode : "NONE");
            ps.setString(8, fixedTime);
            ps.setObject(9, leadMinutes);
            ps.executeUpdate();
            try (ResultSet rk = ps.getGeneratedKeys()) { return rk.next() ? rk.getLong(1) : -1; }
        } catch (SQLException e) { throw new RuntimeException("Erro ao salvar protocolo", e); }
    }

    public long saveProtocol(String name, ProtocolExecutionType type,
                             String category, String description,
                             Long linkedTaskId, int validityDays) {
        return saveProtocol(name, type, category, description, linkedTaskId, validityDays, "NONE", null, null);
    }

    /** Compatibilidade retroativa sem validity_days. */
    public long saveProtocol(String name, ProtocolExecutionType type,
                             String category, String description, Long linkedTaskId) {
        return saveProtocol(name, type, category, description, linkedTaskId, 0, "NONE", null, null);
    }

    public void updateProtocol(long id, String name, ProtocolExecutionType type,
                               String category, String description,
                               Long linkedTaskId, int validityDays,
                               String timingMode, String fixedTime, Integer leadMinutes) {
        db.execute("UPDATE protocols SET name=?, execution_type=?, category=?, description=?, linked_task_id=?, validity_days=?, timing_mode=?, fixed_time=?, lead_minutes=? WHERE id=?",
            name, type.name(), category != null ? category : "Geral",
            description, linkedTaskId, validityDays,
            timingMode != null ? timingMode : "NONE", fixedTime, leadMinutes, id);
    }

    public void updateProtocol(long id, String name, ProtocolExecutionType type,
                               String category, String description,
                               Long linkedTaskId, int validityDays) {
        updateProtocol(id, name, type, category, description, linkedTaskId, validityDays, "NONE", null, null);
    }

    /** Compatibilidade retroativa. */
    public void updateProtocol(long id, String name, ProtocolExecutionType type,
                               String category, String description, Long linkedTaskId) {
        updateProtocol(id, name, type, category, description, linkedTaskId, 0, "NONE", null, null);
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

    /** Cria (ou reaproveita) um protocolo padrão de saída de casa para evitar esquecimentos. */
    public long createLeavingHomeProtocolTemplate() {
        String name = "Protocolo de saída de casa";
        String category = "Rotina diária";
        Optional<Protocol> existing = findProtocolByName(name);
        if (existing.isPresent()) {
            return existing.get().id();
        }

        long templateId = saveProtocol(
                name,
                ProtocolExecutionType.RECORRENTE,
                category,
                "Checklist rápido antes de sair: confirme itens essenciais e reduza esquecimentos.",
                null,
                0
        );
        saveStep(templateId, 1, "Carteira e documentos", "RG, CPF, cartões e dinheiro", true);
        saveStep(templateId, 2, "Chaves", "Casa, portão, carro ou trabalho", true);
        saveStep(templateId, 3, "Celular", "Verificar bateria e sinal", true);
        saveStep(templateId, 4, "Carregador", "Cabo/fonte ou power bank", true);
        saveStep(templateId, 5, "Óculos / fone / crachá", "Itens pessoais úteis para o dia", false);
        return templateId;
    }

    /** Cria (ou reaproveita) um protocolo padrão para reuniões/saídas externas. */
    public long createMeetingProtocolTemplate() {
        String name = "Protocolo de reunião / saída externa";
        String category = "Saídas e reuniões";
        Optional<Protocol> existing = findProtocolByName(name);
        if (existing.isPresent()) {
            return existing.get().id();
        }

        long templateId = saveProtocol(
                name,
                ProtocolExecutionType.RECORRENTE,
                category,
                "Checklist para encontros, reuniões e compromissos fora de casa: leve o essencial e confirme materiais específicos.",
                null,
                0
        );
        saveStep(templateId, 1, "Carteira, chave e celular", "Itens básicos antes de sair", true);
        saveStep(templateId, 2, "Notebook / tablet", "Se a reunião exigir consulta ou apresentação", false);
        saveStep(templateId, 3, "Carregador / power bank", "Energia para celular e notebook", true);
        saveStep(templateId, 4, "Relatório impresso / documentos", "Levar qualquer material combinado para a reunião", true);
        saveStep(templateId, 5, "Endereço, horário e contato", "Confirmar local, horário e pessoa responsável", true);
        saveStep(templateId, 6, "Remédio / água / item pessoal importante", "Aquilo que você não pode esquecer no dia", false);
        return templateId;
    }

    /**
     * Cria (ou reaproveita) protocolos orientados por horário para rotinas diárias.
     *
     * @return IDs dos protocolos criados/encontrados na ordem: remédio 08:00, remédio 20:00,
     * preparar saída 30 min antes, reunião 1h antes.
     */
    public List<Long> createTimeBasedProtocolTemplates() {
        List<Long> ids = new ArrayList<>();
        ids.add(createOrReuseTimedTemplate(
                "Remédio 08:00",
                "Horários",
                "Tomar medicação da manhã às 08:00.",
                "FIXED_TIME",
                "08:00",
                null,
                List.of(
                        new StepSeed("Separar água", "Deixe a água pronta para reduzir atrito", true),
                        new StepSeed("Tomar remédio", "Dose da manhã", true),
                        new StepSeed("Registrar tomada", "Marque horário e observações rápidas", false)
                )));
        ids.add(createOrReuseTimedTemplate(
                "Remédio 20:00",
                "Horários",
                "Tomar medicação da noite às 20:00.",
                "FIXED_TIME",
                "20:00",
                null,
                List.of(
                        new StepSeed("Separar água", "Deixe a água pronta para reduzir atrito", true),
                        new StepSeed("Tomar remédio", "Dose da noite", true),
                        new StepSeed("Registrar tomada", "Marque horário e observações rápidas", false)
                )));
        ids.add(createOrReuseTimedTemplate(
                "Preparar saída 30 min antes",
                "Horários",
                "Checklist de preparação para sair, com antecedência de 30 minutos da tarefa/evento.",
                "BEFORE_TASK",
                null,
                30,
                List.of(
                        new StepSeed("Documentos e carteira", "Conferir itens obrigatórios", true),
                        new StepSeed("Celular e bateria", "Garantir autonomia durante o deslocamento", true),
                        new StepSeed("Material da agenda", "Levar o que foi combinado para o compromisso", true)
                )));
        ids.add(createOrReuseTimedTemplate(
                "Reunião 1h antes",
                "Horários",
                "Preparação de reunião com 1 hora de antecedência.",
                "BEFORE_TASK",
                null,
                60,
                List.of(
                        new StepSeed("Revisar pauta", "Relembrar objetivos e tópicos principais", true),
                        new StepSeed("Separar documentos", "Abrir arquivos e material de apoio", true),
                        new StepSeed("Checar logística", "Link/local, horário e lembrete final", true)
                )));
        return ids;
    }

    private long createOrReuseTimedTemplate(String name,
                                            String category,
                                            String description,
                                            String timingMode,
                                            String fixedTime,
                                            Integer leadMinutes,
                                            List<StepSeed> steps) {
        Optional<Protocol> existing = findProtocolByName(name);
        if (existing.isPresent()) {
            Protocol p = existing.get();
            updateProtocol(p.id(), p.name(), p.executionType(), p.category(), p.description(),
                    p.linkedTaskId(), p.validityDays(), timingMode, fixedTime, leadMinutes);
            return p.id();
        }
        long templateId = saveProtocol(name,
                ProtocolExecutionType.RECORRENTE,
                category,
                description,
                null,
                0,
                timingMode,
                fixedTime,
                leadMinutes);
        for (int i = 0; i < steps.size(); i++) {
            StepSeed s = steps.get(i);
            saveStep(templateId, i + 1, s.text(), s.notes(), s.critical());
        }
        return templateId;
    }

    private record StepSeed(String text, String notes, boolean critical) {}

    private Optional<Protocol> findProtocolByName(String name) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM protocols WHERE lower(name)=lower(?) ORDER BY id ASC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapProtocol(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar protocolo por nome", e); }
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
            readColumnOrDefault(rs, "timing_mode", "NONE"),
            readColumnOrDefault(rs, "fixed_time", null),
            readIntColumnOrNull(rs, "lead_minutes"),
            parseDateTime(createdStr)
        );
    }

    private String readColumnOrDefault(ResultSet rs, String column, String fallback) {
        try {
            String value = rs.getString(column);
            return value != null ? value : fallback;
        } catch (SQLException ignored) {
            return fallback;
        }
    }

    private Integer readIntColumnOrNull(ResultSet rs, String column) {
        try {
            Object value = rs.getObject(column);
            return value != null ? ((Number) value).intValue() : null;
        } catch (SQLException ignored) {
            return null;
        }
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






