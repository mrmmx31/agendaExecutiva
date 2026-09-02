package com.pessoal.agenda.wear.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WearContractsTest {
    @Test
    fun stateRoundTripKeepsClosedShape() {
        val state = validState()
        assertEquals(state, WearContractCodec.decodeState(WearContractCodec.encodeState(state)))
    }

    @Test
    fun stateRejectsExtraActionsAndUnsafeSnoozeOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            validState().copy(actions = listOf(WearActionType.SNOOZE)).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            validState().copy(snoozeOptionsMinutes = listOf(30, 10, 500)).validate()
        }
    }

    @Test
    fun decoderRejectsUnknownFields() {
        val payload = WearContractCodec.encodeState(validState()).decodeToString()
            .replaceFirst("{", "{\"health_data\":true,")
        assertThrows(Exception::class.java) {
            WearContractCodec.decodeState(payload.encodeToByteArray())
        }
    }

    @Test
    fun completeAndSnoozeHaveExclusiveTemporalShapes() {
        validAction(WearActionType.COMPLETE, null).validate()
        validAction(WearActionType.SNOOZE, "2026-09-01T14:30:00Z").validate()
        assertThrows(IllegalArgumentException::class.java) {
            validAction(WearActionType.COMPLETE, "2026-09-01T14:30:00Z").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            validAction(WearActionType.SNOOZE, "2026-09-02T14:30:00Z").validate()
        }
    }

    @Test
    fun pathsAcceptOnlyCanonicalAlertIds() {
        val id = "30000000-0000-4000-8000-000000000001"
        assertEquals(id, WearDataPaths.alertId(WearDataPaths.alertState(id)))
        assertNull(WearDataPaths.alertId("/agenda/v1/alerts/not-a-uuid"))
        assertNull(WearDataPaths.alertId("/agenda/v1/alerts/$id/extra"))
        assertNull(WearDataPaths.alertId("/other/$id"))

        val operationId = "40000000-0000-4000-8000-000000000001"
        assertEquals(operationId, WearDataPaths.operationId(WearDataPaths.action(operationId)))
        assertNull(WearDataPaths.operationId("/agenda/v1/actions/not-a-uuid"))
    }

    private fun validState() = WearAlertState(
        contractVersion = 1,
        alertId = "30000000-0000-4000-8000-000000000001",
        revision = 2,
        text = "Separar documentos",
        reason = "Protocolo de saída",
        sourceDeviceId = "30000000-0000-4000-8000-000000000003",
        scheduledAt = "2026-09-01T14:00:00Z",
        validUntil = "2026-09-01T18:00:00Z",
        updatedAt = "2026-09-01T13:55:00Z",
        criticality = WearCriticality.ROUTINE,
        actions = WearAlertState.REQUIRED_ACTIONS,
        snoozeOptionsMinutes = listOf(10, 30, 60),
        status = WearAlertStatus.PENDING,
        acknowledgedOperationId = null,
    )

    private fun validAction(action: WearActionType, snoozeUntil: String?) = WearAlertAction(
        contractVersion = 1,
        operationId = "40000000-0000-4000-8000-000000000001",
        alertId = "30000000-0000-4000-8000-000000000001",
        sourceDeviceId = "30000000-0000-4000-8000-000000000004",
        action = action,
        occurredAt = "2026-09-01T14:00:00Z",
        snoozeUntil = snoozeUntil,
    )
}
