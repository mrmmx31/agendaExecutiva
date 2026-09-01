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
        assertKeys("snapshot-page.valid.json", SNAPSHOT_KEYS)
        assertKeys("conflict.valid.json", CONFLICT_KEYS)

        assertEquals("PENDING", fixture("pairing-response.valid.json").requiredText("status"))
        assertEquals("APPLIED", fixture("sync-result.valid.json").requiredText("status"))
        assertEquals("TEXT_DIVERGED", fixture("conflict.valid.json").requiredText("reason"))
    }

    @Test
    fun invalidFixturesAreRejectedByClosedFieldsAndEnums() {
        val extra = fixture("pairing-request.invalid-extra-field.json")
        assertFalse(extra.keys == PAIRING_REQUEST_KEYS)
        assertTrue("unexpected_secret" in extra)

        val result = fixture("sync-result.invalid-status.json")
        assertFalse(result.requiredText("status") in RESULT_STATES)
    }

    private fun assertKeys(name: String, expected: Set<String>) = assertEquals(expected, fixture(name).keys)

    private fun fixture(name: String): JsonObject {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/v1/$name"))
        return stream.bufferedReader().use { Json.parseToJsonElement(it.readText()) as JsonObject }
    }

    private fun JsonObject.requiredText(name: String): String = requireNotNull(this[name]).jsonPrimitive.content

    private companion object {
        val PAIRING_REQUEST_KEYS = setOf("contract_version", "session_id", "desktop_id", "device_id", "device_name", "one_time_code", "device_public_key", "invitation_nonce", "requested_roles")
        val PAIRING_RESPONSE_KEYS = setOf("request_id", "status", "retry_after_seconds", "completion_token", "device_id", "contract_min", "contract_max", "encrypted_credential", "granted_roles")
        val SYNC_BATCH_KEYS = setOf("contract_version", "device_id", "last_server_cursor", "operations")
        val SYNC_RESULT_KEYS = setOf("operation_id", "status", "error_code", "server_revision", "conflict_id")
        val SNAPSHOT_KEYS = setOf("snapshot_id", "server_cursor", "page", "has_more", "next_page_token", "tasks", "protocols")
        val CONFLICT_KEYS = setOf("conflict_id", "operation_id", "entity_type", "entity_id", "base_revision", "server_revision", "reason", "local_value", "server_value", "created_at")
        val RESULT_STATES = setOf("APPLIED", "CONFLICT", "REJECTED", "RETRYABLE")
    }
}
