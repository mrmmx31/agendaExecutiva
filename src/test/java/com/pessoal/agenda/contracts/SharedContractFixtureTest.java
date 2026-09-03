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
        assertKeys("wear-protocol-step-state.valid.json", WEAR_PROTOCOL_STATE_KEYS);
        assertKeys("wear-protocol-step-action.valid.json", WEAR_PROTOCOL_ACTION_KEYS);
        assertKeys("health-consent.valid.json", HEALTH_CONSENT_KEYS);
        assertKeys("intake-log.valid.json", INTAKE_LOG_KEYS);
        assertKeys("symptom-log.valid.json", SYMPTOM_LOG_KEYS);
        assertKeys("health-summary.valid.json", HEALTH_SUMMARY_KEYS);
        assertKeys("health-report.valid.json", HEALTH_REPORT_KEYS);
        assertKeys("recommendation-event.valid.json", RECOMMENDATION_EVENT_KEYS);
        assertKeys("recommendation-decision.valid.json", RECOMMENDATION_DECISION_KEYS);
        assertKeys("personal-ranking-dataset.valid.json", PERSONAL_RANKING_DATASET_KEYS);
        assertKeys("personal-model-manifest.valid.json", PERSONAL_MODEL_MANIFEST_KEYS);

        assertEquals("PENDING", fixture("pairing-response.valid.json").get("status").getAsString());
        assertEquals("APPLIED", fixture("sync-result.valid.json").get("status").getAsString());
        assertEquals("TEXT_DIVERGED", fixture("conflict.valid.json").get("reason").getAsString());
        assertFalse(fixture("sensory-profile.valid.json").get("global_enabled").getAsBoolean());
        assertEquals("SNOOZE", fixture("alert-action.valid.json").get("action").getAsString());
        assertEquals("PENDING", fixture("wear-alert-state.valid.json").get("status").getAsString());
        assertEquals("SYMPTOM", fixture("health-consent.valid.json").get("category").getAsString());
        assertEquals("MANUAL", fixture("intake-log.valid.json").get("source").getAsString());
        assertEquals("MANUAL", fixture("symptom-log.valid.json").get("source").getAsString());
        assertEquals("HEART_RATE", fixture("health-summary.valid.json").get("category").getAsString());
        assertEquals(1, fixture("health-report.valid.json").get("contract_version").getAsInt());
        assertEquals("ALERT_SNOOZED", fixture("recommendation-event.valid.json").get("event_type").getAsString());
        assertTrue(fixture("recommendation-decision.valid.json").get("fallback").getAsBoolean());
        JsonObject dataset = fixture("personal-ranking-dataset.valid.json");
        assertEquals("SYNTHETIC_FIXTURE", dataset.get("source").getAsString());
        assertEquals(12, dataset.getAsJsonArray("samples").size());
        dataset.getAsJsonArray("samples").forEach(sample ->
                assertEquals(PERSONAL_RANKING_SAMPLE_KEYS, sample.getAsJsonObject().keySet()));
        JsonObject manifest = fixture("personal-model-manifest.valid.json");
        assertEquals("SHADOW", manifest.get("status").getAsString());
        assertEquals("AUDITABLE_LINEAR_KOTLIN", manifest.get("runtime").getAsString());
        assertEquals(PERSONAL_MODEL_METRIC_KEYS, manifest.getAsJsonObject("metrics").keySet());
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
    private static final Set<String> WEAR_ALERT_STATE_KEYS = Set.of("contract_version", "alert_id", "revision", "text", "reason", "source_device_id", "scheduled_at", "valid_until", "updated_at", "criticality", "actions", "snooze_options_minutes", "status", "acknowledged_operation_id");
    private static final Set<String> WEAR_PROTOCOL_STATE_KEYS = Set.of("contract_version", "run_id", "protocol_id", "revision", "protocol_title", "step_id", "step_label", "step_position", "step_count", "updated_at", "status", "acknowledged_operation_id");
    private static final Set<String> WEAR_PROTOCOL_ACTION_KEYS = Set.of("contract_version", "operation_id", "run_id", "step_id", "source_device_id", "occurred_at");
    private static final Set<String> HEALTH_CONSENT_KEYS = Set.of("contract_version", "consent_id", "category", "purpose", "enabled", "foreground_only", "retention_days", "granted_at", "revoked_at", "updated_at");
    private static final Set<String> INTAKE_LOG_KEYS = Set.of("contract_version", "entry_id", "kind", "name", "amount", "unit", "planned_at", "occurred_at", "time_zone", "context", "perceived_effect", "note", "source", "revision", "tombstone", "updated_at");
    private static final Set<String> SYMPTOM_LOG_KEYS = Set.of("contract_version", "entry_id", "kind", "label", "occurred_at", "time_zone", "intensity", "note", "source", "revision", "tombstone", "updated_at");
    private static final Set<String> HEALTH_SUMMARY_KEYS = Set.of("contract_version", "summary_id", "consent_id", "category", "period_start", "period_end", "coverage_start", "coverage_end", "sample_count", "metrics", "source_packages", "missing_reason", "imported_at");
    private static final Set<String> HEALTH_REPORT_KEYS = Set.of("contract_version", "snapshot_id", "generated_at", "period_start", "period_end", "time_zone", "subject_label", "selected_categories", "permissions", "sources", "limitations", "excluded_entry_count", "entries");
    private static final Set<String> RECOMMENDATION_EVENT_KEYS = Set.of("contract_version", "event_id", "event_type", "occurred_at", "local_hour", "day_of_week", "source_device", "active_context", "capacity_context", "alert_kind", "deadline_bucket", "channel", "response_latency_seconds", "snooze_minutes", "recommendation_id", "option_code");
    private static final Set<String> RECOMMENDATION_DECISION_KEYS = Set.of("contract_version", "recommendation_id", "generated_at", "engine_id", "rule_version", "purpose", "sample_count", "minimum_samples", "fallback", "options");
    private static final Set<String> PERSONAL_RANKING_DATASET_KEYS = Set.of("contract_version", "dataset_id", "purpose", "source", "generated_at", "samples");
    private static final Set<String> PERSONAL_RANKING_SAMPLE_KEYS = Set.of("day_part", "day_group", "source_device", "active_context", "capacity_context", "alert_kind", "deadline_bucket", "chosen_option");
    private static final Set<String> PERSONAL_MODEL_MANIFEST_KEYS = Set.of("contract_version", "model_id", "model_version", "purpose", "runtime", "feature_contract_version", "artifact_format", "artifact_sha256", "trained_at", "training_sample_count", "status", "metrics", "rollback_model_id");
    private static final Set<String> PERSONAL_MODEL_METRIC_KEYS = Set.of("evaluation_sample_count", "top1_accuracy", "baseline_top1_accuracy");
    private static final Set<String> RESULT_STATES = Set.of("APPLIED", "CONFLICT", "REJECTED", "RETRYABLE");
}
