package com.pessoal.agenda.contracts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedContractFixtureTest {
    @Test
    void validFixturesHaveClosedV1Shapes() {
        assertKeys("pairing-request.valid.json", PAIRING_REQUEST_KEYS);
        assertKeys("pairing-response.valid.json", PAIRING_RESPONSE_KEYS);
        assertKeys("sync-batch.valid.json", SYNC_BATCH_KEYS);
        assertKeys("sync-result.valid.json", SYNC_RESULT_KEYS);
        assertKeys("sync-batch-response.valid.json", SYNC_BATCH_RESPONSE_KEYS);
        assertKeys("snapshot-page.valid.json", SNAPSHOT_KEYS);
        assertKeys("conflict.valid.json", CONFLICT_KEYS);

        assertEquals("PENDING", fixture("pairing-response.valid.json").get("status").getAsString());
        assertEquals("APPLIED", fixture("sync-result.valid.json").get("status").getAsString());
        assertEquals("TEXT_DIVERGED", fixture("conflict.valid.json").get("reason").getAsString());
    }

    @Test
    void invalidFixturesAreRejectedByClosedFieldsAndEnums() {
        JsonObject extra = fixture("pairing-request.invalid-extra-field.json");
        assertFalse(extra.keySet().equals(PAIRING_REQUEST_KEYS));
        assertTrue(extra.has("unexpected_secret"));

        String status = fixture("sync-result.invalid-status.json").get("status").getAsString();
        assertFalse(RESULT_STATES.contains(status));
    }

    private void assertKeys(String name, Set<String> expected) {
        assertEquals(expected, fixture(name).keySet());
    }

    private JsonObject fixture(String name) {
        var stream = getClass().getResourceAsStream("/agenda-contracts/fixtures/v1/" + name);
        assertNotNull(stream, "Fixture compartilhada ausente: " + name);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static final Set<String> PAIRING_REQUEST_KEYS = Set.of("contract_version", "session_id", "desktop_id", "device_id", "device_name", "one_time_code", "device_public_key", "invitation_nonce", "requested_roles");
    private static final Set<String> PAIRING_RESPONSE_KEYS = Set.of("request_id", "status", "retry_after_seconds", "completion_token", "device_id", "contract_min", "contract_max", "encrypted_credential", "granted_roles");
    private static final Set<String> SYNC_BATCH_KEYS = Set.of("contract_version", "device_id", "last_server_cursor", "operations");
    private static final Set<String> SYNC_RESULT_KEYS = Set.of("operation_id", "status", "error_code", "server_revision", "conflict_id");
    private static final Set<String> SYNC_BATCH_RESPONSE_KEYS = Set.of("contract_version", "client_contiguous_sequence", "server_cursor", "results", "conflicts");
    private static final Set<String> SNAPSHOT_KEYS = Set.of("snapshot_id", "server_cursor", "page", "has_more", "next_page_token", "tasks", "protocols");
    private static final Set<String> CONFLICT_KEYS = Set.of("conflict_id", "operation_id", "entity_type", "entity_id", "base_revision", "server_revision", "reason", "local_value", "server_value", "created_at");
    private static final Set<String> RESULT_STATES = Set.of("APPLIED", "CONFLICT", "REJECTED", "RETRYABLE");
}
