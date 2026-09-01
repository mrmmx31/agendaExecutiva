package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DesktopSyncRepository {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "TASKS_READ", "CAPTURES_WRITE", "PROTOCOLS_EXECUTE");
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "APPLIED", "CONFLICT", "REJECTED");

    private final Database database;
    private final Clock clock;

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
}
