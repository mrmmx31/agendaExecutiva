package com.pessoal.agenda.infra.pairing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.DesktopSyncRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncBatchProcessorTest {
    private static final String DEVICE_ID = "10000000-0000-4000-8000-000000000060";
    private static final String OPERATION_ID = "10000000-0000-4000-8000-000000000061";
    private static final String RUN_ID = "10000000-0000-4000-8000-000000000062";

    @TempDir Path tempDir;

    @Test
    void divergentProtocolRevisionReturnsReplayableConflictWithBothVersions() throws Exception {
        Database database = new Database(tempDir.resolve("conflict.db"));
        database.runMigrations();
        database.execute("INSERT INTO protocols(name) VALUES('Protocolo desktop fictício')");
        DesktopSyncRepository repository = new DesktopSyncRepository(database);
        repository.refreshSnapshot();
        String protocolId = string(database, "SELECT sync_uuid FROM protocols LIMIT 1");
        repository.approveDevice(DEVICE_ID, "Android fictício", "a".repeat(64), 1, 1,
                Set.of("PROTOCOLS_EXECUTE"));
        SyncBatchProcessor processor = new SyncBatchProcessor(repository);
        byte[] batch = batch(protocolId);

        JsonObject first = processor.process(DEVICE_ID, batch);
        JsonObject replay = processor.process(DEVICE_ID, batch);

        assertEquals("CONFLICT", first.getAsJsonArray("results").get(0)
                .getAsJsonObject().get("status").getAsString());
        assertEquals(1, first.getAsJsonArray("conflicts").size());
        assertEquals("STRUCTURE_DIVERGED", first.getAsJsonArray("conflicts").get(0)
                .getAsJsonObject().get("reason").getAsString());
        assertEquals(first, replay);
        assertEquals(Set.of("operation_id", "status", "error_code", "server_revision", "conflict_id"),
                replay.getAsJsonArray("results").get(0).getAsJsonObject().keySet());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM mobile_conflicts"));
    }

    @Test
    void mobileProtocolProposalAlwaysEntersReviewWithoutReplacingDesktop() throws Exception {
        Database database = new Database(tempDir.resolve("proposal.db"));
        database.runMigrations();
        database.execute("INSERT INTO protocols(name) VALUES('Saída fictícia')");
        database.execute("INSERT INTO protocol_steps(template_id, step_text) VALUES(1, 'Chaves')");
        DesktopSyncRepository repository = new DesktopSyncRepository(database);
        repository.refreshSnapshot();
        String protocolId = string(database, "SELECT sync_uuid FROM protocols LIMIT 1");
        repository.approveDevice(DEVICE_ID, "Android fictício", "a".repeat(64), 1, 1,
                Set.of("PROTOCOLS_EXECUTE"));

        JsonObject response = new SyncBatchProcessor(repository).process(
                DEVICE_ID, proposalBatch(protocolId));

        assertEquals("CONFLICT", response.getAsJsonArray("results").get(0)
                .getAsJsonObject().get("status").getAsString());
        assertEquals("STRUCTURE_DIVERGED", response.getAsJsonArray("conflicts").get(0)
                .getAsJsonObject().get("reason").getAsString());
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM protocol_steps"));
        assertEquals(1, database.queryInt("SELECT COUNT(*) FROM mobile_conflicts WHERE status='OPEN'"));
    }

    private byte[] proposalBatch(String protocolId) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("protocol_id", protocolId);
        payload.addProperty("base_revision", 1);
        payload.addProperty("proposed_step_label", "Conferir crachá");
        payload.addProperty("proposed_at", "2026-09-01T12:00:00Z");
        String payloadJson = new Gson().toJson(payload);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payloadJson.getBytes(StandardCharsets.UTF_8)));
        JsonObject operation = new JsonObject();
        operation.addProperty("operation_id", OPERATION_ID);
        operation.addProperty("device_id", DEVICE_ID);
        operation.addProperty("sequence", 1);
        operation.addProperty("contract_version", 1);
        operation.addProperty("entity_type", "protocol");
        operation.addProperty("entity_id", protocolId);
        operation.addProperty("command_type", "PROTOCOL_STRUCTURE_PROPOSED");
        operation.addProperty("occurred_at", "2026-09-01T12:00:00Z");
        operation.addProperty("time_zone", "America/Manaus");
        operation.add("payload", payload);
        operation.addProperty("payload_hash", hash);
        operation.addProperty("base_revision", 1);
        JsonArray operations = new JsonArray();
        operations.add(operation);
        JsonObject batch = new JsonObject();
        batch.addProperty("contract_version", 1);
        batch.addProperty("device_id", DEVICE_ID);
        batch.addProperty("last_server_cursor", 0);
        batch.add("operations", operations);
        return new Gson().toJson(batch).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] batch(String protocolId) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("run_id", RUN_ID);
        payload.addProperty("protocol_id", protocolId);
        payload.addProperty("protocol_revision", 99);
        payload.addProperty("started_at", "2026-09-01T12:00:00Z");
        String payloadJson = new Gson().toJson(payload);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payloadJson.getBytes(StandardCharsets.UTF_8)));
        JsonObject operation = new JsonObject();
        operation.addProperty("operation_id", OPERATION_ID);
        operation.addProperty("device_id", DEVICE_ID);
        operation.addProperty("sequence", 1);
        operation.addProperty("contract_version", 1);
        operation.addProperty("entity_type", "protocol_run");
        operation.addProperty("entity_id", RUN_ID);
        operation.addProperty("command_type", "PROTOCOL_RUN_STARTED");
        operation.addProperty("occurred_at", "2026-09-01T12:00:00Z");
        operation.addProperty("time_zone", "America/Manaus");
        operation.add("payload", payload);
        operation.addProperty("payload_hash", hash);
        operation.add("base_revision", JsonNull.INSTANCE);
        JsonArray operations = new JsonArray();
        operations.add(operation);
        JsonObject batch = new JsonObject();
        batch.addProperty("contract_version", 1);
        batch.addProperty("device_id", DEVICE_ID);
        batch.addProperty("last_server_cursor", 0);
        batch.add("operations", operations);
        return new Gson().toJson(batch).getBytes(StandardCharsets.UTF_8);
    }

    private static String string(Database database, String sql) throws Exception {
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
