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
        assertKeys("alert-definition.valid.json", ALERT_DEFINITION_KEYS);
        assertKeys("sensory-profile.valid.json", SENSORY_PROFILE_KEYS);
        assertKeys("alert-action.valid.json", ALERT_ACTION_KEYS);
        assertKeys("wear-alert-state.valid.json", WEAR_ALERT_STATE_KEYS);

        assertEquals("PENDING", fixture("pairing-response.valid.json").get("status").getAsString());
        assertEquals("APPLIED", fixture("sync-result.valid.json").get("status").getAsString());
        assertEquals("TEXT_DIVERGED", fixture("conflict.valid.json").get("reason").getAsString());
        assertFalse(fixture("sensory-profile.valid.json").get("global_enabled").getAsBoolean());
        assertEquals("SNOOZE", fixture("alert-action.valid.json").get("action").getAsString());
        assertEquals("PENDING", fixture("wear-alert-state.valid.json").get("status").getAsString());
    }

    @Test
    void invalidFixturesAreRejectedByClosedFieldsAndEnums() {
        JsonObject extra = fixture("pairing-request.invalid-extra-field.json");
        assertFalse(extra.keySet().equals(PAIRING_REQUEST_KEYS));
        assertTrue(extra.has("unexpected_secret"));

        String status = fixture("sync-result.invalid-status.json").get("status").getAsString();
        assertFalse(RESULT_STATES.contains(status));

        JsonObject wearExtra = fixture("wear-alert-state.invalid-extra-field.json");
        assertFalse(wearExtra.keySet().equals(WEAR_ALERT_STATE_KEYS));
        assertTrue(wearExtra.has("health_data"));
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
    private static final Set<String> ALERT_DEFINITION_KEYS = Set.of("contract_version", "alert_id", "origin", "reference_id", "text", "reason", "source_device_id", "scheduled_at", "valid_until", "criticality", "allowed_channels", "repeat_policy", "actions");
    private static final Set<String> SENSORY_PROFILE_KEYS = Set.of("contract_version", "global_enabled", "enabled_channels", "quiet_hours", "paused_until", "cooldown_minutes", "audio_route");
    private static final Set<String> ALERT_ACTION_KEYS = Set.of("contract_version", "operation_id", "alert_id", "source_device_id", "action", "occurred_at", "snooze_until");
    private static final Set<String> WEAR_ALERT_STATE_KEYS = Set.of("contract_version", "alert_id", "revision", "text", "reason", "source_device_id", "scheduled_at", "valid_until", "updated_at", "criticality", "actions", "snooze_options_minutes", "status");
    private static final Set<String> RESULT_STATES = Set.of("APPLIED", "CONFLICT", "REJECTED", "RETRYABLE");
}
