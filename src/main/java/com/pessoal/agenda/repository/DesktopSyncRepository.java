package com.pessoal.agenda.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pessoal.agenda.infra.Database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DesktopSyncRepository {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "TASKS_READ", "CAPTURES_WRITE", "PROTOCOLS_EXECUTE");
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "APPLIED", "CONFLICT", "REJECTED");

    private final Database database;
    private final Clock clock;
    private final Gson gson = new Gson();

    public DesktopSyncRepository(Database database) {
        this(database, Clock.systemUTC());
    }

    DesktopSyncRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public String desktopId() {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                String existing = queryString(connection,
                        "SELECT desktop_id FROM mobile_desktop_identity WHERE singleton_id=1");
                if (existing != null) {
                    connection.commit();
                    return existing;
                }
                String created = UUID.randomUUID().toString();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mobile_desktop_identity(singleton_id, desktop_id, created_at)
                        VALUES(1, ?, ?)
                        """)) {
                    statement.setString(1, created);
                    statement.setString(2, now());
                    statement.executeUpdate();
                }
                connection.commit();
                return created;
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw failure("identidade desktop", error);
        }
    }

    public void approveDevice(String deviceId, String deviceName, String credentialHash,
                              int contractMin, int contractMax, Set<String> roles) {
        requireUuid(deviceId, "device_id");
        String name = normalizeName(deviceName);
        if (!credentialHash.matches("[0-9a-f]{64}")) invalid("credential_hash");
        if (contractMin < 1 || contractMax < contractMin) invalid("contract_range");
        Set<String> granted = new LinkedHashSet<>(roles == null ? Set.of() : roles);
        if (granted.isEmpty() || !ALLOWED_ROLES.containsAll(granted)) invalid("roles");
        String timestamp = now();

        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mobile_devices(
                            device_id, device_name, credential_hash, contract_min, contract_max,
                            status, created_at, approved_at
                        ) VALUES(?,?,?,?,?,'ACTIVE',?,?)
                        """)) {
                    statement.setString(1, deviceId);
                    statement.setString(2, name);
                    statement.setString(3, credentialHash);
                    statement.setInt(4, contractMin);
                    statement.setInt(5, contractMax);
                    statement.setString(6, timestamp);
                    statement.setString(7, timestamp);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mobile_device_roles(device_id, role) VALUES(?,?)")) {
                    for (String role : granted) {
                        statement.setString(1, deviceId);
                        statement.setString(2, role);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mobile_sync_cursors(
                            device_id, client_contiguous_sequence, server_cursor, updated_at
                        ) VALUES(?,0,0,?)
                        """)) {
                    statement.setString(1, deviceId);
                    statement.setString(2, timestamp);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw failure("aprovação do dispositivo", error);
        }
    }

    public DeviceRecord findDevice(String deviceId) {
        requireUuid(deviceId, "device_id");
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT device_id, device_name, contract_min, contract_max,
                            status, approved_at, revoked_at
                     FROM mobile_devices WHERE device_id=?
                     """)) {
            statement.setString(1, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new DeviceRecord(
                        rows.getString("device_id"), rows.getString("device_name"),
                        rows.getInt("contract_min"), rows.getInt("contract_max"),
                        rows.getString("status"), rows.getString("approved_at"),
                        rows.getString("revoked_at"), roles(connection, deviceId));
            }
        } catch (SQLException error) {
            throw failure("consulta do dispositivo", error);
        }
    }

    public List<DeviceRecord> listDevices() {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT device_id, device_name, contract_min, contract_max,
                            status, approved_at, revoked_at
                     FROM mobile_devices
                     ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, approved_at DESC
                     """);
             ResultSet rows = statement.executeQuery()) {
            List<DeviceRecord> devices = new java.util.ArrayList<>();
            while (rows.next()) {
                String deviceId = rows.getString("device_id");
                devices.add(new DeviceRecord(
                        deviceId, rows.getString("device_name"), rows.getInt("contract_min"),
                        rows.getInt("contract_max"), rows.getString("status"),
                        rows.getString("approved_at"), rows.getString("revoked_at"),
                        roles(connection, deviceId)));
            }
            return List.copyOf(devices);
        } catch (SQLException error) {
            throw failure("lista de dispositivos", error);
        }
    }

    public boolean revokeDevice(String deviceId) {
        requireUuid(deviceId, "device_id");
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE mobile_devices SET status='REVOKED', revoked_at=?
                     WHERE device_id=? AND status='ACTIVE'
                     """)) {
            statement.setString(1, now());
            statement.setString(2, deviceId);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            throw failure("revogação do dispositivo", error);
        }
    }

    public boolean credentialsMatch(String deviceId, byte[] credential) {
        requireUuid(deviceId, "device_id");
        if (credential == null || credential.length != 32) return false;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT credential_hash FROM mobile_devices
                     WHERE device_id=? AND status='ACTIVE'
                     """)) {
            statement.setString(1, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return false;
                byte[] expected = java.util.HexFormat.of().parseHex(rows.getString(1));
                byte[] actual = MessageDigest.getInstance("SHA-256").digest(credential);
                return MessageDigest.isEqual(expected, actual);
            }
        } catch (SQLException error) {
            throw failure("autenticação do dispositivo", error);
        } catch (Exception error) {
            throw new IllegalStateException("Falha criptográfica ao autenticar dispositivo.", error);
        }
    }

    public boolean hasRole(String deviceId, String role) {
        requireUuid(deviceId, "device_id");
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM mobile_device_roles r
                     JOIN mobile_devices d ON d.device_id=r.device_id
                     WHERE r.device_id=? AND r.role=? AND d.status='ACTIVE'
                     """)) {
            statement.setString(1, deviceId);
            statement.setString(2, role);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException error) {
            throw failure("papel do dispositivo", error);
        }
    }

    public StoredOperation findStoredOperation(String operationId) {
        requireUuid(operationId, "operation_id");
        try (Connection connection = database.connect()) {
            return operation(connection, operationId);
        } catch (SQLException error) {
            throw failure("consulta da operação", error);
        }
    }

    public StoredOperation applyCapture(OperationInput input, String text, Instant createdAt) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty() || normalized.length() > 4000) invalid("capture_text");
        if (!"CAPTURE_CREATED".equals(input.commandType()) || !"capture".equals(input.entityType())) {
            invalid("capture_operation");
        }
        return applyEffect(input, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO inbox_captures(
                        raw_text, created_at, mobile_source_operation_id
                    ) VALUES(?,?,?)
                    """)) {
                statement.setString(1, normalized);
                statement.setString(2, createdAt.toString());
                statement.setString(3, input.operationId());
                statement.executeUpdate();
            }
        });
    }

    public StoredOperation applyProtocolEvent(OperationInput input, String payloadJson) {
        if (!Set.of("PROTOCOL_RUN_STARTED", "PROTOCOL_STEP_COMPLETED", "PROTOCOL_RUN_CANCELLED")
                .contains(input.commandType())) invalid("protocol_event");
        return applyEffect(input, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO mobile_protocol_events(
                        operation_id, device_id, event_type, entity_id, payload_json, occurred_at
                    ) VALUES(?,?,?,?,?,?)
                    """)) {
                statement.setString(1, input.operationId());
                statement.setString(2, input.deviceId());
                statement.setString(3, input.commandType());
                statement.setString(4, input.entityId());
                statement.setString(5, payloadJson);
                statement.setString(6, input.occurredAt());
                statement.executeUpdate();
            }
        });
    }

    public StoredOperation storeConflict(OperationInput input, Long baseRevision, String reason,
                                         String localValueJson, String serverValueJson,
                                         long serverRevision) {
        if (!Set.of("TEXT_DIVERGED", "STRUCTURE_DIVERGED", "STATE_DIVERGED",
                "TOMBSTONE_DIVERGED").contains(reason)) invalid("conflict_reason");
        String conflictId = UUID.randomUUID().toString();
        OperationInput terminal = new OperationInput(
                input.operationId(), input.deviceId(), input.sequence(), input.commandType(),
                input.entityType(), input.entityId(), input.payloadHash(), "CONFLICT",
                "STATE_CONFLICT", serverRevision, conflictId,
                resultJson(input.operationId(), "CONFLICT", "STATE_CONFLICT",
                        serverRevision, conflictId), input.occurredAt());
        validate(terminal);
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                StoredOperation replay = replayOrReject(connection, terminal);
                if (replay != null) { connection.commit(); return replay; }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mobile_conflicts(
                            conflict_id, operation_id, device_id, entity_type, entity_id,
                            base_revision, server_revision, reason, local_value_json,
                            server_value_json, created_at
                        ) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setString(1, conflictId);
                    statement.setString(2, terminal.operationId());
                    statement.setString(3, terminal.deviceId());
                    statement.setString(4, terminal.entityType());
                    statement.setString(5, terminal.entityId());
                    if (baseRevision == null) statement.setNull(6, java.sql.Types.BIGINT);
                    else statement.setLong(6, baseRevision);
                    statement.setLong(7, serverRevision);
                    statement.setString(8, reason);
                    statement.setString(9, localValueJson);
                    statement.setString(10, serverValueJson);
                    statement.setString(11, now());
                    statement.executeUpdate();
                }
                insertOperation(connection, terminal);
                advanceCursor(connection, terminal.deviceId());
                StoredOperation stored = operation(connection, terminal.operationId());
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw failure("registro do conflito", error);
        }
    }

    public synchronized SnapshotRecord refreshSnapshot() {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                Map<EntityKey, String> current = readCurrentEntities(connection);
                reconcileEntities(connection, current);
                List<String> tasks = new java.util.ArrayList<>();
                List<String> protocols = new java.util.ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT entity_type, payload_json FROM mobile_entity_versions
                        ORDER BY entity_type, entity_id
                        """); ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        (rows.getString(1).equals("task") ? tasks : protocols)
                                .add(rows.getString(2));
                    }
                }
                long cursor = 0;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(MAX(cursor),0) FROM mobile_server_changes");
                     ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) cursor = rows.getLong(1);
                }
                connection.commit();
                return new SnapshotRecord(cursor, List.copyOf(tasks), List.copyOf(protocols));
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw failure("snapshot móvel", error);
        }
    }

    public long currentRevision(String entityType, String entityId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT revision FROM mobile_entity_versions
                     WHERE entity_type=? AND entity_id=?
                     """)) {
            statement.setString(1, entityType);
            statement.setString(2, entityId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        } catch (SQLException error) {
            throw failure("revisão da entidade", error);
        }
    }

    public String currentEntityJson(String entityType, String entityId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT payload_json FROM mobile_entity_versions
                     WHERE entity_type=? AND entity_id=?
                     """)) {
            statement.setString(1, entityType);
            statement.setString(2, entityId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : "{}"; }
        } catch (SQLException error) {
            throw failure("conteúdo da entidade", error);
        }
    }

    public ConflictRecord findConflict(String conflictId) {
        requireUuid(conflictId, "conflict_id");
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT conflict_id, operation_id, entity_type, entity_id, base_revision,
                            server_revision, reason, local_value_json, server_value_json, created_at
                     FROM mobile_conflicts WHERE conflict_id=?
                     """)) {
            statement.setString(1, conflictId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                long base = rows.getLong("base_revision");
                Long baseRevision = rows.wasNull() ? null : base;
                return new ConflictRecord(
                        rows.getString("conflict_id"), rows.getString("operation_id"),
                        rows.getString("entity_type"), rows.getString("entity_id"),
                        baseRevision, rows.getLong("server_revision"),
                        rows.getString("reason"), rows.getString("local_value_json"),
                        rows.getString("server_value_json"), rows.getString("created_at"));
            }
        } catch (SQLException error) {
            throw failure("consulta do conflito", error);
        }
    }

    public StoredOperation storeTerminal(OperationInput input) {
        validate(input);
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                StoredOperation existing = operation(connection, input.operationId());
                if (existing != null) {
                    if (!existing.sameContent(input)) throw new SyncPersistenceException("ID_REUSED");
                    connection.commit();
                    return existing.asReplay();
                }
                if (!isActive(connection, input.deviceId())) throw new SyncPersistenceException("DEVICE_REVOKED");
                if (sequenceExists(connection, input.deviceId(), input.sequence())) {
                    throw new SyncPersistenceException("SEQUENCE_REUSED");
                }
                insertOperation(connection, input);
                advanceCursor(connection, input.deviceId());
                StoredOperation stored = operation(connection, input.operationId());
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw failure("operação idempotente", error);
        }
    }

    private StoredOperation applyEffect(OperationInput input, SqlEffect effect) {
        validate(input);
        if (!"APPLIED".equals(input.status())) invalid("effect_status");
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                StoredOperation replay = replayOrReject(connection, input);
                if (replay != null) { connection.commit(); return replay; }
                effect.apply(connection);
                insertOperation(connection, input);
                advanceCursor(connection, input.deviceId());
                StoredOperation stored = operation(connection, input.operationId());
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw failure("efeito da operação", error);
        }
    }

    private StoredOperation replayOrReject(Connection connection, OperationInput input)
            throws SQLException {
        StoredOperation existing = operation(connection, input.operationId());
        if (existing != null) {
            if (!existing.sameContent(input)) throw new SyncPersistenceException("ID_REUSED");
            return existing.asReplay();
        }
        if (!isActive(connection, input.deviceId())) {
            throw new SyncPersistenceException("DEVICE_REVOKED");
        }
        if (sequenceExists(connection, input.deviceId(), input.sequence())) {
            throw new SyncPersistenceException("SEQUENCE_REUSED");
        }
        return null;
    }

    private Map<EntityKey, String> readCurrentEntities(Connection connection) throws SQLException {
        ensureSyncUuids(connection, "tasks");
        ensureSyncUuids(connection, "protocols");
        ensureSyncUuids(connection, "protocol_steps");
        Map<EntityKey, String> entities = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sync_uuid, title, done, status FROM tasks
                WHERE sync_uuid IS NOT NULL ORDER BY sync_uuid
                """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                JsonObject value = new JsonObject();
                value.addProperty("id", rows.getString("sync_uuid"));
                value.addProperty("title", rows.getString("title"));
                value.addProperty("status", rows.getInt("done") == 1
                        ? "COMPLETED" : normalizeTaskStatus(rows.getString("status")));
                value.addProperty("tombstone", false);
                entities.put(new EntityKey("task", rows.getString("sync_uuid")), gson.toJson(value));
            }
        }
        try (PreparedStatement protocols = connection.prepareStatement("""
                SELECT sync_uuid, name, created_at FROM protocols
                WHERE sync_uuid IS NOT NULL ORDER BY sync_uuid
                """); ResultSet rows = protocols.executeQuery();
             PreparedStatement steps = connection.prepareStatement("""
                SELECT sync_uuid, step_order, step_text FROM protocol_steps
                WHERE template_id=(SELECT id FROM protocols WHERE sync_uuid=?)
                ORDER BY step_order, id
                """)) {
            while (rows.next()) {
                String id = rows.getString("sync_uuid");
                JsonObject value = new JsonObject();
                value.addProperty("id", id);
                value.addProperty("title", rows.getString("name"));
                value.addProperty("created_at", normalizeSqlTimestamp(rows.getString("created_at")));
                value.addProperty("tombstone", false);
                JsonArray items = new JsonArray();
                steps.setString(1, id);
                try (ResultSet stepRows = steps.executeQuery()) {
                    while (stepRows.next()) {
                        JsonObject step = new JsonObject();
                        step.addProperty("id", stepRows.getString("sync_uuid"));
                        step.addProperty("position", stepRows.getInt("step_order"));
                        step.addProperty("label", stepRows.getString("step_text"));
                        items.add(step);
                    }
                }
                value.add("steps", items);
                entities.put(new EntityKey("protocol", id), gson.toJson(value));
            }
        }
        return entities;
    }

    private static void ensureSyncUuids(Connection connection, String table) throws SQLException {
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

    private void reconcileEntities(Connection connection, Map<EntityKey, String> current)
            throws SQLException {
        Map<EntityKey, EntityVersion> previous = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT entity_type, entity_id, revision, content_hash, payload_json
                FROM mobile_entity_versions
                """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                previous.put(new EntityKey(rows.getString(1), rows.getString(2)),
                        new EntityVersion(rows.getLong(3), rows.getString(4), rows.getString(5)));
            }
        }

        for (Map.Entry<EntityKey, String> entry : current.entrySet()) {
            String hash = sha256(entry.getValue());
            EntityVersion old = previous.get(entry.getKey());
            if (old != null && old.contentHash.equals(hash)) continue;
            long revision = old == null ? 1 : old.revision + 1;
            JsonObject payload = gson.fromJson(entry.getValue(), JsonObject.class);
            payload.addProperty("revision", revision);
            payload.addProperty("updated_at", now());
            upsertEntityVersion(connection, entry.getKey(), revision, hash, gson.toJson(payload));
        }
        for (Map.Entry<EntityKey, EntityVersion> entry : previous.entrySet()) {
            if (current.containsKey(entry.getKey())) continue;
            JsonObject oldPayload = gson.fromJson(entry.getValue().payloadJson, JsonObject.class);
            if (oldPayload.has("tombstone") && oldPayload.get("tombstone").getAsBoolean()) continue;
            long revision = entry.getValue().revision + 1;
            oldPayload.addProperty("revision", revision);
            oldPayload.addProperty("updated_at", now());
            oldPayload.addProperty("tombstone", true);
            oldPayload.remove("steps");
            String hash = sha256("tombstone:" + entry.getValue().contentHash);
            upsertEntityVersion(connection, entry.getKey(), revision, hash, gson.toJson(oldPayload));
        }
    }

    private void upsertEntityVersion(Connection connection, EntityKey key, long revision,
                                     String hash, String payload) throws SQLException {
        long cursor;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mobile_server_changes(
                    entity_type, entity_id, revision, content_hash, payload_json, changed_at
                ) VALUES(?,?,?,?,?,?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, key.type);
            statement.setString(2, key.id);
            statement.setLong(3, revision);
            statement.setString(4, hash);
            statement.setString(5, payload);
            statement.setString(6, now());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Cursor do servidor não retornado");
                cursor = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mobile_entity_versions(
                    entity_type, entity_id, revision, content_hash,
                    payload_json, server_cursor, changed_at
                ) VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(entity_type, entity_id) DO UPDATE SET
                    revision=excluded.revision,
                    content_hash=excluded.content_hash,
                    payload_json=excluded.payload_json,
                    server_cursor=excluded.server_cursor,
                    changed_at=excluded.changed_at
                """)) {
            statement.setString(1, key.type);
            statement.setString(2, key.id);
            statement.setLong(3, revision);
            statement.setString(4, hash);
            statement.setString(5, payload);
            statement.setLong(6, cursor);
            statement.setString(7, now());
            statement.executeUpdate();
        }
    }

    private static String normalizeTaskStatus(String value) {
        return switch (value == null ? "PENDENTE" : value) {
            case "CONCLUIDA" -> "COMPLETED";
            case "CANCELADA" -> "CANCELLED";
            case "EM_ANDAMENTO" -> "IN_PROGRESS";
            case "BLOQUEADA" -> "BLOCKED";
            default -> "PENDING";
        };
    }

    private static String normalizeSqlTimestamp(String value) {
        if (value == null || value.isBlank()) return Instant.EPOCH.toString();
        if (value.contains("T")) return value.endsWith("Z") ? value : value + "Z";
        return value.replace(' ', 'T') + "Z";
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 indisponível", error);
        }
    }

    public static String resultJson(String operationId, String status, String errorCode,
                                    Long serverRevision, String conflictId) {
        JsonObject result = new JsonObject();
        result.addProperty("operation_id", operationId);
        result.addProperty("status", status);
        if (errorCode == null) result.add("error_code", com.google.gson.JsonNull.INSTANCE);
        else result.addProperty("error_code", errorCode);
        if (serverRevision == null) result.add("server_revision", com.google.gson.JsonNull.INSTANCE);
        else result.addProperty("server_revision", serverRevision);
        if (conflictId == null) result.add("conflict_id", com.google.gson.JsonNull.INSTANCE);
        else result.addProperty("conflict_id", conflictId);
        return new GsonBuilder().serializeNulls().create().toJson(result);
    }

    public CursorRecord cursor(String deviceId) {
        requireUuid(deviceId, "device_id");
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT client_contiguous_sequence, server_cursor, updated_at
                     FROM mobile_sync_cursors WHERE device_id=?
                     """)) {
            statement.setString(1, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new CursorRecord(rows.getLong(1), rows.getLong(2), rows.getString(3));
            }
        } catch (SQLException error) {
            throw failure("cursor", error);
        }
    }

    public void acknowledgeServerCursor(String deviceId, long serverCursor) {
        requireUuid(deviceId, "device_id");
        if (serverCursor < 0) invalid("server_cursor");
        try (Connection connection = database.connect()) {
            long maximum;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT COALESCE(MAX(cursor),0) FROM mobile_server_changes");
                 ResultSet rows = query.executeQuery()) {
                maximum = rows.next() ? rows.getLong(1) : 0;
            }
            if (serverCursor > maximum) invalid("server_cursor");
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE mobile_sync_cursors SET server_cursor=?, updated_at=?
                    WHERE device_id=? AND server_cursor<=?
                    """)) {
                statement.setLong(1, serverCursor);
                statement.setString(2, now());
                statement.setString(3, deviceId);
                statement.setLong(4, serverCursor);
                statement.executeUpdate();
            }
        } catch (SQLException error) {
            throw failure("confirmação do cursor", error);
        }
    }

    private void insertOperation(Connection connection, OperationInput input) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mobile_applied_operations(
                    operation_id, device_id, sequence, command_type, entity_type, entity_id,
                    payload_hash, status, error_code, server_revision, conflict_id,
                    result_json, occurred_at, processed_at
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, input.operationId());
            statement.setString(2, input.deviceId());
            statement.setLong(3, input.sequence());
            statement.setString(4, input.commandType());
            statement.setString(5, input.entityType());
            statement.setString(6, input.entityId());
            statement.setString(7, input.payloadHash());
            statement.setString(8, input.status());
            statement.setString(9, input.errorCode());
            if (input.serverRevision() == null) statement.setNull(10, java.sql.Types.BIGINT);
            else statement.setLong(10, input.serverRevision());
            statement.setString(11, input.conflictId());
            statement.setString(12, input.resultJson());
            statement.setString(13, input.occurredAt());
            statement.setString(14, now());
            statement.executeUpdate();
        }
    }

    private void advanceCursor(Connection connection, String deviceId) throws SQLException {
        long cursor;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT client_contiguous_sequence FROM mobile_sync_cursors WHERE device_id=?")) {
            statement.setString(1, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Cursor do dispositivo ausente");
                cursor = rows.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sequence FROM mobile_applied_operations
                WHERE device_id=? AND sequence>? ORDER BY sequence
                """)) {
            statement.setString(1, deviceId);
            statement.setLong(2, cursor);
            try (ResultSet rows = statement.executeQuery()) {
                long expected = cursor + 1;
                while (rows.next() && rows.getLong(1) == expected) expected++;
                cursor = expected - 1;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mobile_sync_cursors SET client_contiguous_sequence=?, updated_at=?
                WHERE device_id=?
                """)) {
            statement.setLong(1, cursor);
            statement.setString(2, now());
            statement.setString(3, deviceId);
            statement.executeUpdate();
        }
    }

    private static StoredOperation operation(Connection connection, String operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id, device_id, sequence, command_type, entity_type, entity_id,
                       payload_hash, status, error_code, server_revision, conflict_id, result_json
                FROM mobile_applied_operations WHERE operation_id=?
                """)) {
            statement.setString(1, operationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                long revision = rows.getLong("server_revision");
                Long nullableRevision = rows.wasNull() ? null : revision;
                return new StoredOperation(
                        rows.getString("operation_id"), rows.getString("device_id"),
                        rows.getLong("sequence"), rows.getString("command_type"),
                        rows.getString("entity_type"), rows.getString("entity_id"),
                        rows.getString("payload_hash"), rows.getString("status"),
                        rows.getString("error_code"), nullableRevision,
                        rows.getString("conflict_id"), rows.getString("result_json"), false);
            }
        }
    }

    private static boolean isActive(Connection connection, String deviceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM mobile_devices WHERE device_id=? AND status='ACTIVE'")) {
            statement.setString(1, deviceId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static boolean sequenceExists(Connection connection, String deviceId, long sequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM mobile_applied_operations WHERE device_id=? AND sequence=?")) {
            statement.setString(1, deviceId);
            statement.setLong(2, sequence);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static Set<String> roles(Connection connection, String deviceId) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT role FROM mobile_device_roles WHERE device_id=? ORDER BY role")) {
            statement.setString(1, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
        }
        return Set.copyOf(result);
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private void validate(OperationInput input) {
        if (input == null) invalid("operation");
        requireUuid(input.operationId(), "operation_id");
        requireUuid(input.deviceId(), "device_id");
        requireUuid(input.entityId(), "entity_id");
        if (input.conflictId() != null) requireUuid(input.conflictId(), "conflict_id");
        if (input.sequence() < 1 || !input.payloadHash().matches("[0-9a-f]{64}")) invalid("operation");
        if (!TERMINAL_STATUSES.contains(input.status()) || input.resultJson() == null) invalid("result");
        Instant.parse(input.occurredAt());
    }

    private static void requireUuid(String value, String field) {
        try { UUID.fromString(value); } catch (RuntimeException error) { invalid(field); }
    }

    private static String normalizeName(String name) {
        if (name == null) invalid("device_name");
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > 100) invalid("device_name");
        return normalized;
    }

    private String now() { return Instant.now(clock).toString(); }
    private static void invalid(String field) { throw new IllegalArgumentException("Campo inválido: " + field); }
    private static RuntimeException failure(String action, SQLException error) {
        return new RuntimeException("Erro na persistência móvel: " + action, error);
    }

    public record DeviceRecord(String deviceId, String deviceName, int contractMin,
                               int contractMax, String status, String approvedAt,
                               String revokedAt, Set<String> roles) {}
    public record CursorRecord(long clientContiguousSequence, long serverCursor, String updatedAt) {}
    public record SnapshotRecord(long serverCursor, List<String> taskJson,
                                 List<String> protocolJson) {}
    public record ConflictRecord(String conflictId, String operationId, String entityType,
                                 String entityId, Long baseRevision, long serverRevision,
                                 String reason, String localValueJson, String serverValueJson,
                                 String createdAt) {}
    public record OperationInput(String operationId, String deviceId, long sequence,
                                 String commandType, String entityType, String entityId,
                                 String payloadHash, String status, String errorCode,
                                 Long serverRevision, String conflictId, String resultJson,
                                 String occurredAt) {}
    public record StoredOperation(String operationId, String deviceId, long sequence,
                                  String commandType, String entityType, String entityId,
                                  String payloadHash, String status, String errorCode,
                                  Long serverRevision, String conflictId, String resultJson,
                                  boolean replay) {
        boolean sameContent(OperationInput input) {
            return deviceId.equals(input.deviceId()) && sequence == input.sequence()
                    && commandType.equals(input.commandType()) && entityType.equals(input.entityType())
                    && entityId.equals(input.entityId()) && payloadHash.equals(input.payloadHash());
        }
        StoredOperation asReplay() {
            return new StoredOperation(operationId, deviceId, sequence, commandType, entityType,
                    entityId, payloadHash, status, errorCode, serverRevision, conflictId,
                    resultJson, true);
        }
    }

    public static final class SyncPersistenceException extends RuntimeException {
        private final String code;
        public SyncPersistenceException(String code) { super("Operação de sincronização recusada."); this.code = code; }
        public String code() { return code; }
    }

    @FunctionalInterface
    private interface SqlEffect { void apply(Connection connection) throws SQLException; }
    private record EntityKey(String type, String id) {}
    private record EntityVersion(long revision, String contentHash, String payloadJson) {}
}
