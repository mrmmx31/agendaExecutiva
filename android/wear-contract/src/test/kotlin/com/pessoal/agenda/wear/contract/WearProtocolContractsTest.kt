package com.pessoal.agenda.wear.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolContractsTest {
    @Test
    fun activeStepRoundTripsWithoutAdditionalContext() {
        val state = state()
        assertEquals(state, WearProtocolCodec.decodeState(WearProtocolCodec.encodeState(state)))
    }

    @Test
    fun completedStateCannotRetainStepText() {
        assertTrue(runCatching {
            state().copy(status = WearProtocolStatus.COMPLETED).validate()
        }.isFailure)
    }

    @Test
    fun pathsAreStrictAndCanonical() {
        assertEquals(RUN_ID, WearDataPaths.protocolRunId(WearDataPaths.protocolState(RUN_ID)))
        assertEquals(OPERATION_ID, WearDataPaths.protocolOperationId(WearDataPaths.protocolAction(OPERATION_ID)))
        assertEquals(null, WearDataPaths.protocolRunId("${WearDataPaths.PROTOCOL_STATE_PREFIX}$RUN_ID/extra"))
    }

    private fun state() = WearProtocolStepState(
        contractVersion = 1,
        runId = RUN_ID,
        protocolId = PROTOCOL_ID,
        revision = 2,
        protocolTitle = "Saída rápida",
        stepId = STEP_ID,
        stepLabel = "Levar as chaves",
        stepPosition = 1,
        stepCount = 4,
        updatedAt = "2026-09-02T12:00:00Z",
        status = WearProtocolStatus.ACTIVE,
        acknowledgedOperationId = null,
    )

    private companion object {
        const val RUN_ID = "92000000-0000-4000-8000-000000000001"
        const val PROTOCOL_ID = "92000000-0000-4000-8000-000000000002"
        const val STEP_ID = "92000000-0000-4000-8000-000000000003"
        const val OPERATION_ID = "92000000-0000-4000-8000-000000000004"
    }
}
