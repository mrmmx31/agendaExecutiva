package com.pessoal.agenda.repository;

import com.pessoal.agenda.infra.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopSyncRepositoryTest {
    private static final String DEVICE_ID = "10000000-0000-4000-8000-000000000001";
    private static final String ENTITY_ID = "10000000-0000-4000-8000-000000000010";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @TempDir
    Path tempDir;

    private Database database;
    private DesktopSyncRepository repository;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("mobile-sync-test.db"));
        database.runMigrations();
        repository = new DesktopSyncRepository(
                database,
                Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void desktopIdentityIsStableAndDistinctFromDevice() {
        String first = repository.desktopId();
        String second = repository.desktopId();

        assertEquals(first, second);
        assertNotEquals(DEVICE_ID, first);
        assertEquals(36, first.length());
    }

    @Test
    void migrationAddsStableSyncUuidsToLegacyEntities() throws Exception {
        database.execute("INSERT INTO tasks(title, due_date) VALUES('Fictícia','2026-09-01')");
        database.execute("INSERT INTO protocols(name) VALUES('Protocolo fictício')");
        database.execute("INSERT INTO protocol_steps(template_id, step_text) VALUES(1,'Passo fictício')");

        database.runMigrations();
        String taskUuid = scalar("SELECT sync_uuid FROM tasks WHERE id=1");
        String protocolUuid = scalar("SELECT sync_uuid FROM protocols WHERE id=1");
        String stepUuid = scalar("SELECT sync_uuid FROM protocol_steps WHERE id=1");
        database.runMigrations();

        assertNotNull(taskUuid);
        assertNotNull(protocolUuid);
        assertNotNull(stepUuid);
        assertEquals(taskUuid, scalar("SELECT sync_uuid FROM tasks WHERE id=1"));
        assertEquals(protocolUuid, scalar("SELECT sync_uuid FROM protocols WHERE id=1"));
        assertEquals(stepUuid, scalar("SELECT sync_uuid FROM protocol_steps WHERE id=1"));
    }

    @Test
    void approvalPersistsOnlyHashRolesAndRevocationState() {
        approveDevice();

        var device = repository.findDevice(DEVICE_ID);
        assertEquals("Android fictício", device.deviceName());
        assertEquals(Set.of("TASKS_READ", "CAPTURES_WRITE"), device.roles());
        assertEquals("ACTIVE", device.status());
        assertNull(device.revokedAt());
        assertEquals(HASH_A, scalarUnchecked(
                "SELECT credential_hash FROM mobile_devices WHERE device_id='" + DEVICE_ID + "'"));

        assertTrue(repository.revokeDevice(DEVICE_ID));
        assertFalse(repository.revokeDevice(DEVICE_ID));
        assertEquals("REVOKED", repository.findDevice(DEVICE_ID).status());
        assertNotNull(repository.findDevice(DEVICE_ID).revokedAt());
    }

    @Test
    void deviceListIncludesActiveAndRevokedDevicesWithoutCredentialHash() {
        approveDevice();

        var active = repository.listDevices();
        assertEquals(1, active.size());
        assertEquals(DEVICE_ID, active.getFirst().deviceId());
        assertEquals("ACTIVE", active.getFirst().status());

        repository.revokeDevice(DEVICE_ID);
        var revoked = repository.listDevices();
        assertEquals(1, revoked.size());
        assertEquals("REVOKED", revoked.getFirst().status());
        assertNotNull(revoked.getFirst().revokedAt());
    }

    @Test
    void repeatedOperationReturnsStoredResultWithoutDuplicatingEffect() {
        approveDevice();
        var input = operation("10000000-0000-4000-8000-000000000101", 1, HASH_A);

        var first = repository.storeTerminal(input);
        var repeated = repository.storeTerminal(input);

        assertFalse(first.replay());
        assertTrue(repeated.replay());
        assertEquals(first.resultJson(), repeated.resultJson());
        assertEquals(1, count("SELECT COUNT(*) FROM mobile_applied_operations"));
        assertEquals(1, repository.cursor(DEVICE_ID).clientContiguousSequence());
    }

    @Test
    void rejectedOperationPreservesNullServerRevision() {
        approveDevice();
        var input = new DesktopSyncRepository.OperationInput(
                "10000000-0000-4000-8000-000000000101", DEVICE_ID, 1,
                "CAPTURE_CREATED", "capture", ENTITY_ID, HASH_A,
                "REJECTED", "PAYLOAD_INVALID", null, null,
                "{\"status\":\"REJECTED\"}", "2026-09-01T11:59:00Z");

        var stored = repository.storeTerminal(input);

        assertNull(stored.serverRevision());
        assertEquals("PAYLOAD_INVALID", stored.errorCode());
    }

    @Test
    void cursorWaitsForGapAndThenAdvancesContiguously() {
        approveDevice();
        repository.storeTerminal(operation("10000000-0000-4000-8000-000000000102", 2, HASH_A));
        assertEquals(0, repository.cursor(DEVICE_ID).clientContiguousSequence());

        repository.storeTerminal(operation("10000000-0000-4000-8000-000000000101", 1, HASH_A));
        assertEquals(2, repository.cursor(DEVICE_ID).clientContiguousSequence());
    }

    @Test
    void reusedIdAndSequenceAreRejectedWithStableCodes() {
        approveDevice();
        repository.storeTerminal(operation("10000000-0000-4000-8000-000000000101", 1, HASH_A));

        var reusedId = assertThrows(DesktopSyncRepository.SyncPersistenceException.class,
                () -> repository.storeTerminal(operation(
                        "10000000-0000-4000-8000-000000000101", 1, HASH_B)));
        var reusedSequence = assertThrows(DesktopSyncRepository.SyncPersistenceException.class,
                () -> repository.storeTerminal(operation(
                        "10000000-0000-4000-8000-000000000102", 1, HASH_A)));

        assertEquals("ID_REUSED", reusedId.code());
        assertEquals("SEQUENCE_REUSED", reusedSequence.code());
        assertEquals(1, count("SELECT COUNT(*) FROM mobile_applied_operations"));
    }

    @Test
    void revokedDeviceCannotAddOperation() {
        approveDevice();
        repository.revokeDevice(DEVICE_ID);

        var error = assertThrows(DesktopSyncRepository.SyncPersistenceException.class,
                () -> repository.storeTerminal(operation(
                        "10000000-0000-4000-8000-000000000101", 1, HASH_A)));

        assertEquals("DEVICE_REVOKED", error.code());
        assertEquals(0, count("SELECT COUNT(*) FROM mobile_applied_operations"));
    }

    @Test
    void credentialAuthenticationUsesHashAndHonorsRevocation() throws Exception {
        byte[] credential = new byte[32];
        java.util.Arrays.fill(credential, (byte) 7);
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(credential));
        repository.approveDevice(DEVICE_ID, "Android fictício", hash, 1, 1,
                Set.of("CAPTURES_WRITE"));

        assertTrue(repository.credentialsMatch(DEVICE_ID, credential));
        credential[0] = 8;
        assertFalse(repository.credentialsMatch(DEVICE_ID, credential));
        repository.revokeDevice(DEVICE_ID);
        credential[0] = 7;
        assertFalse(repository.credentialsMatch(DEVICE_ID, credential));
    }

    @Test
    void captureEffectAndTerminalResultAreIdempotentTogether() {
        approveDevice();
        var input = operation("10000000-0000-4000-8000-000000000101", 1, HASH_A);

        var first = repository.applyCapture(
                input, "Captura móvel fictícia", Instant.parse("2026-09-01T11:59:00Z"));
        var replay = repository.applyCapture(
                input, "Captura móvel fictícia", Instant.parse("2026-09-01T11:59:00Z"));

        assertFalse(first.replay());
        assertTrue(replay.replay());
        assertEquals(1, count("SELECT COUNT(*) FROM inbox_captures"));
        assertEquals(1, count("SELECT COUNT(*) FROM mobile_applied_operations"));
    }

    @Test
    void snapshotCursorChangesOnlyWhenDesktopContentChanges() {
        database.execute("INSERT INTO tasks(title, due_date) VALUES('Tarefa fictícia','2026-09-01')");
        database.execute("INSERT INTO protocols(name) VALUES('Protocolo fictício')");

        var first = repository.refreshSnapshot();
        var unchanged = repository.refreshSnapshot();
        assertEquals(1, first.taskJson().size());
        assertEquals(1, first.protocolJson().size());
        assertEquals(first.serverCursor(), unchanged.serverCursor());

        database.execute("UPDATE tasks SET title='Tarefa fictícia alterada'");
        var changed = repository.refreshSnapshot();
        assertTrue(changed.serverCursor() > first.serverCursor());
        assertTrue(changed.taskJson().getFirst().contains("Tarefa fictícia alterada"));
        assertTrue(changed.taskJson().getFirst().contains("\"revision\":2"));
    }

    private void approveDevice() {
        repository.approveDevice(
                DEVICE_ID, "  Android   fictício  ", HASH_A, 1, 1,
                Set.of("TASKS_READ", "CAPTURES_WRITE"));
    }

    private DesktopSyncRepository.OperationInput operation(String id, long sequence, String hash) {
        return new DesktopSyncRepository.OperationInput(
                id, DEVICE_ID, sequence, "CAPTURE_CREATED", "capture", ENTITY_ID,
                hash, "APPLIED", null, 1L, null,
                DesktopSyncRepository.resultJson(id, "APPLIED", null, 1L, null),
                "2026-09-01T11:59:00Z");
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private String scalarUnchecked(String sql) {
        try { return scalar(sql); } catch (Exception error) { throw new RuntimeException(error); }
    }

    private int count(String sql) {
        return database.queryInt(sql);
    }
}
