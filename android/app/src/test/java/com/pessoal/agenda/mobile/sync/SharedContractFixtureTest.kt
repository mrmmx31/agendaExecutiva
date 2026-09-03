package com.pessoal.agenda.mobile.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContractFixtureTest {
    @Test
    fun validFixturesHaveClosedV1Shapes() {
        assertKeys("pairing-request.valid.json", PAIRING_REQUEST_KEYS)
        assertKeys("pairing-response.valid.json", PAIRING_RESPONSE_KEYS)
        assertKeys("sync-batch.valid.json", SYNC_BATCH_KEYS)
        assertKeys("sync-result.valid.json", SYNC_RESULT_KEYS)
        assertKeys("sync-batch-response.valid.json", SYNC_BATCH_RESPONSE_KEYS)
        assertKeys("snapshot-page.valid.json", SNAPSHOT_KEYS)
        assertKeys("conflict.valid.json", CONFLICT_KEYS)
        assertKeys("alert-definition.valid.json", ALERT_DEFINITION_KEYS)
        assertKeys("sensory-profile.valid.json", SENSORY_PROFILE_KEYS)
        assertKeys("alert-action.valid.json", ALERT_ACTION_KEYS)
        assertKeys("wear-alert-state.valid.json", WEAR_ALERT_STATE_KEYS)
        assertKeys("wear-protocol-step-state.valid.json", WEAR_PROTOCOL_STATE_KEYS)
        assertKeys("wear-protocol-step-action.valid.json", WEAR_PROTOCOL_ACTION_KEYS)
        assertKeys("health-consent.valid.json", HEALTH_CONSENT_KEYS)
        assertKeys("intake-log.valid.json", INTAKE_LOG_KEYS)
        assertKeys("symptom-log.valid.json", SYMPTOM_LOG_KEYS)
        assertKeys("health-summary.valid.json", HEALTH_SUMMARY_KEYS)
        assertKeys("health-report.valid.json", HEALTH_REPORT_KEYS)
        assertKeys("recommendation-event.valid.json", RECOMMENDATION_EVENT_KEYS)
        assertKeys("recommendation-decision.valid.json", RECOMMENDATION_DECISION_KEYS)
        assertKeys("personal-ranking-dataset.valid.json", PERSONAL_RANKING_DATASET_KEYS)
        assertKeys("personal-model-manifest.valid.json", PERSONAL_MODEL_MANIFEST_KEYS)

        assertEquals("PENDING", fixture("pairing-response.valid.json").requiredText("status"))
        assertEquals("APPLIED", fixture("sync-result.valid.json").requiredText("status"))
        assertEquals("TEXT_DIVERGED", fixture("conflict.valid.json").requiredText("reason"))
        assertFalse(fixture("sensory-profile.valid.json").requiredText("global_enabled").toBoolean())
        assertEquals("SNOOZE", fixture("alert-action.valid.json").requiredText("action"))
        assertEquals("PENDING", fixture("wear-alert-state.valid.json").requiredText("status"))
        assertEquals("SYMPTOM", fixture("health-consent.valid.json").requiredText("category"))
        assertEquals("MANUAL", fixture("intake-log.valid.json").requiredText("source"))
        assertEquals("MANUAL", fixture("symptom-log.valid.json").requiredText("source"))
        assertEquals("HEART_RATE", fixture("health-summary.valid.json").requiredText("category"))
        assertEquals("1", fixture("health-report.valid.json").requiredText("contract_version"))
        assertEquals("ALERT_SNOOZED", fixture("recommendation-event.valid.json").requiredText("event_type"))
        assertTrue(fixture("recommendation-decision.valid.json").requiredText("fallback").toBoolean())
        val dataset = fixture("personal-ranking-dataset.valid.json")
        assertEquals("SYNTHETIC_FIXTURE", dataset.requiredText("source"))
        val samples = requireNotNull(dataset["samples"] as? kotlinx.serialization.json.JsonArray)
        assertEquals(12, samples.size)
        samples.forEach { assertEquals(PERSONAL_RANKING_SAMPLE_KEYS, (it as JsonObject).keys) }
        val manifest = fixture("personal-model-manifest.valid.json")
        assertEquals("SHADOW", manifest.requiredText("status"))
        assertEquals("AUDITABLE_LINEAR_KOTLIN", manifest.requiredText("runtime"))
        assertEquals(PERSONAL_MODEL_METRIC_KEYS, (manifest["metrics"] as JsonObject).keys)
    }

    @Test
    fun invalidFixturesAreRejectedByClosedFieldsAndEnums() {
        val extra = fixture("pairing-request.invalid-extra-field.json")
        assertFalse(extra.keys == PAIRING_REQUEST_KEYS)
        assertTrue("unexpected_secret" in extra)

        val result = fixture("sync-result.invalid-status.json")
        assertFalse(result.requiredText("status") in RESULT_STATES)

        val wearExtra = fixture("wear-alert-state.invalid-extra-field.json")
        assertFalse(wearExtra.keys == WEAR_ALERT_STATE_KEYS)
        assertTrue("health_data" in wearExtra)
    }

    private fun assertKeys(name: String, expected: Set<String>) = assertEquals(expected, fixture(name).keys)

    private fun fixture(name: String): JsonObject {
        val classLoader = requireNotNull(javaClass.classLoader)
        val stream = requireNotNull(classLoader.getResourceAsStream("fixtures/v1/$name"))
        return stream.bufferedReader().use { Json.parseToJsonElement(it.readText()) as JsonObject }
    }

    private fun JsonObject.requiredText(name: String): String = requireNotNull(this[name]).jsonPrimitive.content

    private companion object {
        val PAIRING_REQUEST_KEYS = setOf("contract_version", "session_id", "desktop_id", "device_id", "device_name", "one_time_code", "device_public_key", "invitation_nonce", "requested_roles")
        val PAIRING_RESPONSE_KEYS = setOf("request_id", "status", "retry_after_seconds", "completion_token", "device_id", "contract_min", "contract_max", "encrypted_credential", "granted_roles")
        val SYNC_BATCH_KEYS = setOf("contract_version", "device_id", "last_server_cursor", "operations")
        val SYNC_RESULT_KEYS = setOf("operation_id", "status", "error_code", "server_revision", "conflict_id")
        val SYNC_BATCH_RESPONSE_KEYS = setOf("contract_version", "client_contiguous_sequence", "server_cursor", "results", "conflicts")
        val SNAPSHOT_KEYS = setOf("snapshot_id", "server_cursor", "page", "has_more", "next_page_token", "tasks", "protocols")
        val CONFLICT_KEYS = setOf("conflict_id", "operation_id", "entity_type", "entity_id", "base_revision", "server_revision", "reason", "local_value", "server_value", "created_at")
        val ALERT_DEFINITION_KEYS = setOf("contract_version", "alert_id", "origin", "reference_id", "text", "reason", "source_device_id", "scheduled_at", "valid_until", "criticality", "allowed_channels", "repeat_policy", "actions")
        val SENSORY_PROFILE_KEYS = setOf("contract_version", "global_enabled", "enabled_channels", "quiet_hours", "paused_until", "cooldown_minutes", "audio_route")
        val ALERT_ACTION_KEYS = setOf("contract_version", "operation_id", "alert_id", "source_device_id", "action", "occurred_at", "snooze_until")
        val WEAR_ALERT_STATE_KEYS = setOf("contract_version", "alert_id", "revision", "text", "reason", "source_device_id", "scheduled_at", "valid_until", "updated_at", "criticality", "actions", "snooze_options_minutes", "status", "acknowledged_operation_id")
        val WEAR_PROTOCOL_STATE_KEYS = setOf("contract_version", "run_id", "protocol_id", "revision", "protocol_title", "step_id", "step_label", "step_position", "step_count", "updated_at", "status", "acknowledged_operation_id")
        val WEAR_PROTOCOL_ACTION_KEYS = setOf("contract_version", "operation_id", "run_id", "step_id", "source_device_id", "occurred_at")
        val HEALTH_CONSENT_KEYS = setOf("contract_version", "consent_id", "category", "purpose", "enabled", "foreground_only", "retention_days", "granted_at", "revoked_at", "updated_at")
        val INTAKE_LOG_KEYS = setOf("contract_version", "entry_id", "kind", "name", "amount", "unit", "planned_at", "occurred_at", "time_zone", "context", "perceived_effect", "note", "source", "revision", "tombstone", "updated_at")
        val SYMPTOM_LOG_KEYS = setOf("contract_version", "entry_id", "kind", "label", "occurred_at", "time_zone", "intensity", "note", "source", "revision", "tombstone", "updated_at")
        val HEALTH_SUMMARY_KEYS = setOf("contract_version", "summary_id", "consent_id", "category", "period_start", "period_end", "coverage_start", "coverage_end", "sample_count", "metrics", "source_packages", "missing_reason", "imported_at")
        val HEALTH_REPORT_KEYS = setOf("contract_version", "snapshot_id", "generated_at", "period_start", "period_end", "time_zone", "subject_label", "selected_categories", "permissions", "sources", "limitations", "excluded_entry_count", "entries")
        val RECOMMENDATION_EVENT_KEYS = setOf("contract_version", "event_id", "event_type", "occurred_at", "local_hour", "day_of_week", "source_device", "active_context", "capacity_context", "alert_kind", "deadline_bucket", "channel", "response_latency_seconds", "snooze_minutes", "recommendation_id", "option_code")
        val RECOMMENDATION_DECISION_KEYS = setOf("contract_version", "recommendation_id", "generated_at", "engine_id", "rule_version", "purpose", "sample_count", "minimum_samples", "fallback", "options")
        val PERSONAL_RANKING_DATASET_KEYS = setOf("contract_version", "dataset_id", "purpose", "source", "generated_at", "samples")
        val PERSONAL_RANKING_SAMPLE_KEYS = setOf("day_part", "day_group", "source_device", "active_context", "capacity_context", "alert_kind", "deadline_bucket", "chosen_option")
        val PERSONAL_MODEL_MANIFEST_KEYS = setOf("contract_version", "model_id", "model_version", "purpose", "runtime", "feature_contract_version", "artifact_format", "artifact_sha256", "trained_at", "training_sample_count", "status", "metrics", "rollback_model_id")
        val PERSONAL_MODEL_METRIC_KEYS = setOf("evaluation_sample_count", "top1_accuracy", "baseline_top1_accuracy")
        val RESULT_STATES = setOf("APPLIED", "CONFLICT", "REJECTED", "RETRYABLE")
    }
}
