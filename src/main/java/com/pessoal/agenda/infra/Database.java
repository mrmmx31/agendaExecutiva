package com.pessoal.agenda.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;

/**
 * Infraestrutura de banco de dados SQLite.
 * Responsavel por fornecer conexoes e executar a migracao de schema.
 * Todos os repositorios dependem desta classe.
 */
public class Database {

    private final String jdbcUrl;

    public Database() {
        this(defaultDatabasePath());
    }

    public Database(Path databaseFile) {
        Path absoluteFile = databaseFile.toAbsolutePath();
        try {
            Files.createDirectories(absoluteFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Nao foi possivel criar diretorio de dados", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + absoluteFile;
    }

    private static Path defaultDatabasePath() {
        return Path.of(System.getProperty("user.home"), ".agenda-pessoal", "agenda.db");
    }

    /** Abre uma nova conexao JDBC. O chamador e responsavel por fechar. */
    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    // ── Helpers compartilhados por repositorios ────────────────────────────

    /** Executa um UPDATE/INSERT/DELETE com parametros posicionais. */
    public void execute(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar SQL: " + sql, e);
        }
    }

    /** Executa um SELECT e retorna o primeiro inteiro da primeira linha. */
    public int queryInt(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar (int): " + sql, e);
        }
    }

    /** Executa um SELECT e retorna o primeiro double da primeira linha. */
    public double queryDouble(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar (double): " + sql, e);
        }
    }

    private void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    // ── Migracao de schema ─────────────────────────────────────────────────

    /** Cria tabelas e aplica migrações incrementais. Idempotente. */
    public void runMigrations() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    notes TEXT,
                    due_date TEXT NOT NULL,
                    done INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS checklist_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    protocol_name TEXT NOT NULL,
                    item_text TEXT NOT NULL,
                    done INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS finance_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL,
                    due_date TEXT,
                    paid INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sales_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    unit_price REAL NOT NULL,
                    sale_date TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    minimum_quantity INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject TEXT NOT NULL,
                    session_date TEXT NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    notes TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_ideas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // Tabela de categorias personalizadas por dominio
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    color TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(name, domain)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_plans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    study_type TEXT NOT NULL DEFAULT 'GERAL',
                    category TEXT NOT NULL DEFAULT 'Geral',
                    description TEXT,
                    start_date TEXT,
                    target_date TEXT,
                    status TEXT NOT NULL DEFAULT 'PLANEJADO',
                    total_pages INTEGER NOT NULL DEFAULT 0,
                    current_page INTEGER NOT NULL DEFAULT 0,
                    progress_percent REAL NOT NULL DEFAULT 0.0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_id INTEGER NOT NULL,
                    entry_title TEXT,
                    entry_date TEXT NOT NULL,
                    content TEXT,
                    duration_minutes INTEGER NOT NULL DEFAULT 0,
                    page_start INTEGER NOT NULL DEFAULT 0,
                    page_end INTEGER NOT NULL DEFAULT 0,
                    entry_order INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // ── Protocolos Operacionais ────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocols (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    execution_type TEXT NOT NULL DEFAULT 'RECORRENTE',
                    category TEXT NOT NULL DEFAULT 'Geral',
                    description TEXT,
                    linked_task_id INTEGER,
                    validity_days INTEGER NOT NULL DEFAULT 0,
                    timing_mode TEXT NOT NULL DEFAULT 'NONE',
                    fixed_time TEXT,
                    lead_minutes INTEGER,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocol_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id INTEGER NOT NULL,
                    step_order INTEGER NOT NULL DEFAULT 0,
                    step_text TEXT NOT NULL,
                    notes TEXT,
                    critical INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocol_executions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id INTEGER NOT NULL,
                    iteration_number INTEGER NOT NULL DEFAULT 1,
                    started_at TEXT NOT NULL,
                    completed_at TEXT,
                    status TEXT NOT NULL DEFAULT 'ATIVA',
                    notes TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocol_execution_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    execution_id INTEGER NOT NULL,
                    step_id INTEGER NOT NULL,
                    step_text TEXT NOT NULL,
                    step_notes TEXT,
                    critical INTEGER NOT NULL DEFAULT 0,
                    step_order INTEGER NOT NULL DEFAULT 0,
                    checked INTEGER NOT NULL DEFAULT 0,
                    checked_at TEXT,
                    observation_notes TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // Protocolos: validade em dias (coluna pode já existir)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_plan_id INTEGER NOT NULL,
                    day_of_week INTEGER NOT NULL,
                    min_minutes INTEGER NOT NULL DEFAULT 30,
                    UNIQUE(study_plan_id, day_of_week)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_compensations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_plan_id INTEGER NOT NULL,
                    missed_date TEXT NOT NULL,
                    compensation_date TEXT,
                    compensation_minutes INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'PENDENTE',
                    notes TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_plan_status_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_plan_id INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    changed_at TEXT NOT NULL DEFAULT (date('now'))
                )""");

            // ── Checklist de Próximas Ações (Ideias / Projetos) ───────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS idea_checklist_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    idea_id INTEGER NOT NULL,
                    text TEXT NOT NULL DEFAULT '',
                    done INTEGER NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // ── Checklist de Tarefas da Agenda ─────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS task_checklist_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    text TEXT NOT NULL DEFAULT '',
                    done INTEGER NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL DEFAULT 0,
                    kanban_column TEXT NOT NULL DEFAULT 'backlog',
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar schema do banco", e);
        }

        // Migracoes incrementais — seguras para re-execucao
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        // Agendamento avancado (Palm-style): tipo, fim e dias da semana
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN schedule_type TEXT NOT NULL DEFAULT 'SINGLE'");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN end_date TEXT");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN recurrence_days TEXT");
        // Categorias para checklist, estudos e ideias
        applyAlterIfMissing("ALTER TABLE checklist_items ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        applyAlterIfMissing("ALTER TABLE study_sessions ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        applyAlterIfMissing("ALTER TABLE study_sessions ADD COLUMN task_id INTEGER");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        // Horário, prioridade e status nas tarefas
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN start_time TEXT");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN end_time TEXT");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDENTE'");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN linked_protocol_id INTEGER");
        // Protocolos: validade em dias
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN validity_days INTEGER NOT NULL DEFAULT 0");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN timing_mode TEXT NOT NULL DEFAULT 'NONE'");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN fixed_time TEXT");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN lead_minutes INTEGER");
        // Ideias e Projetos: campos avancados (pipeline cientifico)
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN idea_type TEXT NOT NULL DEFAULT 'GERAL'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN impact_level TEXT NOT NULL DEFAULT 'MEDIO'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN feasibility INTEGER NOT NULL DEFAULT 3");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN estimated_hours INTEGER NOT NULL DEFAULT 0");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN start_date TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN target_date TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN methodology TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN next_actions TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN keywords TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN references_text TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN parent_idea_id INTEGER");
        // Modo da seção Próximas Ações: 'text' ou 'checklist'
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN next_actions_mode TEXT NOT NULL DEFAULT 'text'");
        // Vendas e Estoque: suporte a materiais e serviços
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN item_type TEXT NOT NULL DEFAULT 'material'");
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN client_name TEXT");
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN notes TEXT");
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN status TEXT NOT NULL DEFAULT 'recebido'");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN item_type TEXT NOT NULL DEFAULT 'material'");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN unit_price REAL NOT NULL DEFAULT 0");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN description TEXT");
        // Checklist de Ideias/Projetos: coluna Kanban
        applyAlterIfMissing("ALTER TABLE idea_checklist_items ADD COLUMN kanban_column TEXT NOT NULL DEFAULT 'backlog'");
        // Checklist de Tarefas da Agenda: coluna kanban (já na tabela, mas garante compatibilidade)
        applyAlterIfMissing("ALTER TABLE task_checklist_items ADD COLUMN kanban_column TEXT NOT NULL DEFAULT 'backlog'");

        // Google Tasks: tabela de mapeamento local ↔ Google
        applyCreateIfMissing("""
            CREATE TABLE IF NOT EXISTS google_tasks_mapping (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                local_task_id  INTEGER NOT NULL,
                google_list_id TEXT    NOT NULL,
                google_task_id TEXT    NOT NULL,
                last_synced_at TEXT    NOT NULL DEFAULT (datetime('now')),
                UNIQUE(local_task_id),
                UNIQUE(google_list_id, google_task_id)
            )""");
        applyAlterIfMissing("ALTER TABLE google_tasks_mapping ADD COLUMN synced_title TEXT");
        applyAlterIfMissing("ALTER TABLE google_tasks_mapping ADD COLUMN synced_notes TEXT");
        applyAlterIfMissing("ALTER TABLE google_tasks_mapping ADD COLUMN synced_due_date TEXT");
        applyAlterIfMissing("ALTER TABLE google_tasks_mapping ADD COLUMN synced_done INTEGER");
        applyAlterIfMissing("ALTER TABLE google_tasks_mapping ADD COLUMN google_updated_at TEXT");
        applyAlterIfMissing("ALTER TABLE google_tasks_mapping ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'ACTIVE'");

        applyDailyPlanMigration();
        applyInboxCaptureMigration();
        applyFocusContextMigration();
        applyTimerRecoveryMigration();
        applyLocalMetricsMigration();
        applyMobileSyncMigration();
    }

    private void applyDailyPlanMigration() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS daily_plans (
                        plan_date TEXT PRIMARY KEY,
                        capacity TEXT NOT NULL DEFAULT 'NORMAL'
                            CHECK (capacity IN ('NORMAL', 'REDUCED')),
                        created_at TEXT NOT NULL,
                        closed_at TEXT,
                        closing_note TEXT
                    )""");
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS daily_plan_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        plan_date TEXT NOT NULL,
                        task_id INTEGER NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('ESSENTIAL', 'SUPPORT')),
                        position INTEGER NOT NULL,
                        FOREIGN KEY (plan_date) REFERENCES daily_plans(plan_date) ON DELETE CASCADE,
                        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
                        UNIQUE(plan_date, task_id),
                        UNIQUE(plan_date, role, position),
                        CHECK (
                            (role = 'ESSENTIAL' AND position = 0)
                            OR (role = 'SUPPORT' AND position BETWEEN 0 AND 1)
                        )
                    )""");
                stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_plan_one_essential
                    ON daily_plan_items(plan_date)
                    WHERE role = 'ESSENTIAL'
                    """);
                conn.commit();
            } catch (SQLException migrationError) {
                conn.rollback();
                throw migrationError;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao migrar planos diarios", e);
        }
    }

    private void applyInboxCaptureMigration() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS inbox_captures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        raw_text TEXT NOT NULL CHECK (length(trim(raw_text)) > 0),
                        kind TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
                            CHECK (kind IN (
                                'UNCLASSIFIED', 'TASK', 'IDEA',
                                'INTERRUPTION_NOTE', 'ARCHIVED'
                            )),
                        created_at TEXT NOT NULL,
                        triaged_at TEXT,
                        target_id INTEGER CHECK (target_id IS NULL OR target_id > 0),
                        CHECK (
                            (kind = 'UNCLASSIFIED' AND triaged_at IS NULL AND target_id IS NULL)
                            OR (kind <> 'UNCLASSIFIED' AND triaged_at IS NOT NULL)
                        )
                    )""");
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_inbox_captures_kind_created
                    ON inbox_captures(kind, created_at DESC, id DESC)
                    """);
                conn.commit();
            } catch (SQLException migrationError) {
                conn.rollback();
                throw migrationError;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao migrar caixa de entrada universal", e);
        }
    }

    private void applyFocusContextMigration() {
        applyCreateIfMissing("""
                CREATE TABLE IF NOT EXISTS focus_context (
                    task_id INTEGER PRIMARY KEY,
                    resume_note TEXT NOT NULL CHECK (length(trim(resume_note)) > 0),
                    interrupted_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
                """);
    }

    private void applyTimerRecoveryMigration() {
        applyCreateIfMissing("""
                CREATE TABLE IF NOT EXISTS timer_recovery (
                    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
                    task_id INTEGER NOT NULL,
                    elapsed_seconds INTEGER NOT NULL CHECK (elapsed_seconds >= 0),
                    was_running INTEGER NOT NULL CHECK (was_running IN (0, 1)),
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
                """);
    }

    private void applyLocalMetricsMigration() {
        applyCreateIfMissing("""
                CREATE TABLE IF NOT EXISTS local_metric_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    metric_type TEXT NOT NULL CHECK (metric_type IN (
                        'FOCUS_START_SECONDS',
                        'QUICK_CAPTURE_ACTIONS',
                        'INTERRUPTION_RESUME_ACTIONS'
                    )),
                    metric_value INTEGER NOT NULL CHECK (metric_value >= 0),
                    occurred_at TEXT NOT NULL
                )
                """);
        applyCreateIfMissing("""
                CREATE INDEX IF NOT EXISTS idx_local_metric_type_time
                ON local_metric_events(metric_type, occurred_at DESC, id DESC)
                """);
    }

    private void applyMobileSyncMigration() {
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN sync_uuid TEXT");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN sync_uuid TEXT");
        applyAlterIfMissing("ALTER TABLE protocol_steps ADD COLUMN sync_uuid TEXT");
        applyAlterIfMissing("ALTER TABLE inbox_captures ADD COLUMN mobile_source_operation_id TEXT");
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_desktop_identity (
                            singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
                            desktop_id TEXT NOT NULL UNIQUE CHECK (length(desktop_id) = 36),
                            created_at TEXT NOT NULL
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_devices (
                            device_id TEXT PRIMARY KEY CHECK (length(device_id) = 36),
                            device_name TEXT NOT NULL CHECK (length(trim(device_name)) BETWEEN 1 AND 100),
                            credential_hash TEXT NOT NULL CHECK (length(credential_hash) = 64),
                            contract_min INTEGER NOT NULL CHECK (contract_min >= 1),
                            contract_max INTEGER NOT NULL CHECK (contract_max >= contract_min),
                            status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
                            created_at TEXT NOT NULL,
                            approved_at TEXT NOT NULL,
                            last_seen_at TEXT,
                            revoked_at TEXT,
                            CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
                                OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_device_roles (
                            device_id TEXT NOT NULL,
                            role TEXT NOT NULL CHECK (role IN ('TASKS_READ', 'CAPTURES_WRITE', 'PROTOCOLS_EXECUTE')),
                            PRIMARY KEY (device_id, role),
                            FOREIGN KEY (device_id) REFERENCES mobile_devices(device_id) ON DELETE CASCADE
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_applied_operations (
                            operation_id TEXT PRIMARY KEY CHECK (length(operation_id) = 36),
                            device_id TEXT NOT NULL,
                            sequence INTEGER NOT NULL CHECK (sequence >= 1),
                            command_type TEXT NOT NULL,
                            entity_type TEXT NOT NULL,
                            entity_id TEXT NOT NULL,
                            payload_hash TEXT NOT NULL CHECK (length(payload_hash) = 64),
                            status TEXT NOT NULL CHECK (status IN ('APPLIED', 'CONFLICT', 'REJECTED')),
                            error_code TEXT,
                            server_revision INTEGER,
                            conflict_id TEXT,
                            result_json TEXT NOT NULL,
                            occurred_at TEXT NOT NULL,
                            processed_at TEXT NOT NULL,
                            UNIQUE (device_id, sequence),
                            FOREIGN KEY (device_id) REFERENCES mobile_devices(device_id) ON DELETE RESTRICT
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_sync_cursors (
                            device_id TEXT PRIMARY KEY,
                            client_contiguous_sequence INTEGER NOT NULL DEFAULT 0 CHECK (client_contiguous_sequence >= 0),
                            server_cursor INTEGER NOT NULL DEFAULT 0 CHECK (server_cursor >= 0),
                            updated_at TEXT NOT NULL,
                            FOREIGN KEY (device_id) REFERENCES mobile_devices(device_id) ON DELETE CASCADE
                        )
                        """);
                populateSyncUuids(conn, "tasks");
                populateSyncUuids(conn, "protocols");
                populateSyncUuids(conn, "protocol_steps");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_sync_uuid ON tasks(sync_uuid) WHERE sync_uuid IS NOT NULL");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_protocols_sync_uuid ON protocols(sync_uuid) WHERE sync_uuid IS NOT NULL");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_protocol_steps_sync_uuid ON protocol_steps(sync_uuid) WHERE sync_uuid IS NOT NULL");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_inbox_mobile_source_operation ON inbox_captures(mobile_source_operation_id) WHERE mobile_source_operation_id IS NOT NULL");
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_protocol_events (
                            operation_id TEXT PRIMARY KEY,
                            device_id TEXT NOT NULL,
                            event_type TEXT NOT NULL CHECK (event_type IN (
                                'PROTOCOL_RUN_STARTED', 'PROTOCOL_STEP_COMPLETED'
                            )),
                            entity_id TEXT NOT NULL,
                            payload_json TEXT NOT NULL,
                            occurred_at TEXT NOT NULL,
                            FOREIGN KEY (device_id) REFERENCES mobile_devices(device_id) ON DELETE RESTRICT
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_conflicts (
                            conflict_id TEXT PRIMARY KEY,
                            operation_id TEXT NOT NULL UNIQUE,
                            device_id TEXT NOT NULL,
                            entity_type TEXT NOT NULL,
                            entity_id TEXT NOT NULL,
                            base_revision INTEGER,
                            server_revision INTEGER NOT NULL,
                            reason TEXT NOT NULL CHECK (reason IN (
                                'TEXT_DIVERGED', 'STRUCTURE_DIVERGED',
                                'STATE_DIVERGED', 'TOMBSTONE_DIVERGED'
                            )),
                            local_value_json TEXT NOT NULL,
                            server_value_json TEXT NOT NULL,
                            status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
                            created_at TEXT NOT NULL,
                            resolved_at TEXT,
                            FOREIGN KEY (device_id) REFERENCES mobile_devices(device_id) ON DELETE RESTRICT
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_server_changes (
                            cursor INTEGER PRIMARY KEY AUTOINCREMENT,
                            entity_type TEXT NOT NULL CHECK (entity_type IN ('task', 'protocol')),
                            entity_id TEXT NOT NULL,
                            revision INTEGER NOT NULL CHECK (revision >= 1),
                            content_hash TEXT NOT NULL CHECK (length(content_hash) = 64),
                            payload_json TEXT NOT NULL,
                            changed_at TEXT NOT NULL
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mobile_entity_versions (
                            entity_type TEXT NOT NULL,
                            entity_id TEXT NOT NULL,
                            revision INTEGER NOT NULL CHECK (revision >= 1),
                            content_hash TEXT NOT NULL CHECK (length(content_hash) = 64),
                            payload_json TEXT NOT NULL,
                            server_cursor INTEGER NOT NULL,
                            changed_at TEXT NOT NULL,
                            PRIMARY KEY (entity_type, entity_id),
                            FOREIGN KEY (server_cursor) REFERENCES mobile_server_changes(cursor)
                        )
                        """);
                conn.commit();
            } catch (SQLException migrationError) {
                conn.rollback();
                throw migrationError;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao migrar persistencia de sincronizacao movel", e);
        }
    }

    private static void populateSyncUuids(Connection connection, String table) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM " + table + " WHERE sync_uuid IS NULL");
             ResultSet rows = select.executeQuery();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE " + table + " SET sync_uuid=? WHERE id=? AND sync_uuid IS NULL")) {
            while (rows.next()) {
                update.setString(1, UUID.randomUUID().toString());
                update.setLong(2, rows.getLong(1));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    /** Executa CREATE TABLE IF NOT EXISTS — usa o mesmo mecanismo idempotente. */
    private void applyCreateIfMissing(String createSql) {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
        } catch (SQLException ignored) { /* já existe */ }
    }

    private void applyAlterIfMissing(String alterSql) {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        } catch (SQLException ignored) { /* coluna ja existe — ignorar */ }
    }
}
