package com.pessoal.agenda.wear.data

import androidx.room.withTransaction
import com.pessoal.agenda.wear.contract.WearProtocolCodec
import com.pessoal.agenda.wear.contract.WearProtocolStatus
import com.pessoal.agenda.wear.contract.WearProtocolStepAction
import com.pessoal.agenda.wear.contract.WearProtocolStepState
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WearVisibleProtocolStep(
    val runId: String,
    val protocolTitle: String,
    val stepId: String,
    val stepLabel: String,
    val stepPosition: Int,
    val stepCount: Int,
    val feedback: Boolean,
    val actionPending: Boolean,
)

class WearProtocolStore(
    private val database: WearDatabase,
    private val deviceIdProvider: () -> String,
    private val operationIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.wear()

    fun observeCurrentStep(): Flow<WearVisibleProtocolStep?> = dao.observeProtocolStates().map { states ->
        states.firstOrNull { it.status == WearProtocolStatus.ACTIVE.name }?.visible()
    }

    suspend fun ingest(state: WearProtocolStepState): WearIngestResult = database.withTransaction {
        state.validate()
        val existing = dao.protocolState(state.runId)
        if (existing != null && state.revision <= existing.revision) {
            return@withTransaction WearIngestResult.STALE
        }
        val acknowledgedLocal = state.acknowledgedOperationId?.let { dao.protocolAction(it) != null } == true
        state.acknowledgedOperationId?.let { dao.deleteProtocolAction(it) }
        dao.upsertProtocolState(state.entity(
            feedback = existing?.localFeedback == true && acknowledgedLocal,
            actionPending = existing?.localActionPending == true && !acknowledgedLocal,
        ))
        if (existing == null) WearIngestResult.INSERTED else WearIngestResult.UPDATED
    }

    suspend fun removeRemoteState(runId: String): Boolean = database.withTransaction {
        UUID.fromString(runId)
        dao.protocolActionCount(runId) == 0 && dao.deleteProtocolState(runId) == 1
    }

    suspend fun recordCompletion(runId: String): WearProtocolStepAction = database.withTransaction {
        val state = requireNotNull(dao.protocolState(runId)) { "Protocolo Wear inexistente." }
        require(state.status == WearProtocolStatus.ACTIVE.name && state.stepId != null) {
            "Etapa Wear já respondida."
        }
        require(!state.localActionPending) { "Confirmação da etapa ainda pendente." }
        val occurredAt = Instant.now(clock).toString()
        val action = WearProtocolStepAction(
            contractVersion = WearProtocolStepState.CONTRACT_VERSION,
            operationId = operationIdProvider().also(UUID::fromString),
            runId = runId,
            stepId = state.stepId,
            sourceDeviceId = deviceIdProvider().also(UUID::fromString),
            occurredAt = occurredAt,
        ).also(WearProtocolStepAction::validate)
        dao.insertProtocolAction(WearProtocolActionOutboxEntity(
            operationId = action.operationId,
            runId = runId,
            payload = WearProtocolCodec.encodeAction(action),
            state = "PENDING",
            attemptCount = 0,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        ))
        dao.upsertProtocolState(state.copy(
            localFeedback = true,
            localActionPending = true,
            updatedAt = occurredAt,
        ))
        action
    }

    suspend fun actionsForSync() = dao.protocolActionsForSync()

    suspend fun markActionStored(operationId: String) {
        UUID.fromString(operationId)
        check(dao.markProtocolActionStored(operationId, Instant.now(clock).toString()) == 1)
    }

    suspend fun dismissFeedback(runId: String) = database.withTransaction {
        val state = dao.protocolState(runId) ?: return@withTransaction
        dao.upsertProtocolState(state.copy(localFeedback = false))
    }

    private fun WearProtocolStepState.entity(feedback: Boolean, actionPending: Boolean) = WearProtocolStateEntity(
        runId, protocolId, revision, protocolTitle, stepId, stepLabel, stepPosition,
        stepCount, updatedAt, status.name, acknowledgedOperationId, feedback, actionPending,
    )

    private fun WearProtocolStateEntity.visible() = WearVisibleProtocolStep(
        runId = runId,
        protocolTitle = protocolTitle,
        stepId = requireNotNull(stepId),
        stepLabel = requireNotNull(stepLabel),
        stepPosition = requireNotNull(stepPosition),
        stepCount = stepCount,
        feedback = localFeedback,
        actionPending = localActionPending,
    )
}
