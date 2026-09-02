package com.pessoal.agenda.wear.data

import androidx.room.withTransaction
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.contract.WearAlertAction
import com.pessoal.agenda.wear.contract.WearAlertState
import com.pessoal.agenda.wear.contract.WearAlertStatus
import com.pessoal.agenda.wear.contract.WearContractCodec
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class WearIngestResult { INSERTED, UPDATED, STALE }

data class WearVisibleAlert(
    val alertId: String,
    val text: String,
    val reason: String,
    val snoozeOptionsMinutes: List<Int>,
    val status: WearAlertStatus,
    val feedback: WearFeedback?,
)

data class WearFeedback(val action: WearActionType, val snoozeMinutes: Int?)

class WearAlertStore(
    private val database: WearDatabase,
    private val deviceIdProvider: () -> String,
    private val operationIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.wear()
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    fun observeVisibleAlert(): Flow<WearVisibleAlert?> = dao.observeAlerts().map { alerts ->
        val now = Instant.now(clock)
        alerts.asSequence()
            .filter { Instant.parse(it.validUntil).isAfter(now) }
            .filter { it.status == WearAlertStatus.PENDING.name || it.localFeedbackAction != null }
            .map { it.visible() }
            .firstOrNull()
    }

    suspend fun ingest(state: WearAlertState): WearIngestResult = database.withTransaction {
        state.validate()
        val existing = dao.alert(state.alertId)
        if (existing != null && state.revision <= existing.revision) {
            return@withTransaction WearIngestResult.STALE
        }
        val acknowledgedLocal = state.acknowledgedOperationId?.let { dao.action(it) != null } == true
        state.acknowledgedOperationId?.let { dao.deleteAction(it) }
        val feedbackAction = existing?.localFeedbackAction.takeIf { acknowledgedLocal }
        val feedbackMinutes = existing?.localFeedbackMinutes.takeIf { feedbackAction != null }
        dao.upsertAlert(state.entity(feedbackAction, feedbackMinutes))
        dao.deleteExpired(Instant.now(clock).toString())
        if (existing == null) WearIngestResult.INSERTED else WearIngestResult.UPDATED
    }

    suspend fun removeRemoteState(alertId: String): Boolean = database.withTransaction {
        UUID.fromString(alertId)
        val hasPendingAction = dao.actionsForSync().any { it.alertId == alertId }
        !hasPendingAction && dao.deleteAlert(alertId) == 1
    }

    suspend fun recordAction(
        alertId: String,
        actionType: WearActionType,
        snoozeMinutes: Int? = null,
    ): WearAlertAction = database.withTransaction {
        val alert = requireNotNull(dao.alert(alertId)) { "Alerta Wear inexistente." }
        require(alert.status == WearAlertStatus.PENDING.name) { "Alerta Wear já respondido." }
        val options = json.decodeFromString<List<Int>>(alert.snoozeOptionsJson)
        when (actionType) {
            WearActionType.COMPLETE -> require(snoozeMinutes == null)
            WearActionType.SNOOZE -> require(requireNotNull(snoozeMinutes) in options) {
                "Adiamento não oferecido pelo telefone."
            }
        }
        val occurredAt = Instant.now(clock)
        val operationId = operationIdProvider().also(UUID::fromString)
        val action = WearAlertAction(
            contractVersion = WearAlertState.CONTRACT_VERSION,
            operationId = operationId,
            alertId = alertId,
            sourceDeviceId = deviceIdProvider(),
            action = actionType,
            occurredAt = occurredAt.toString(),
            snoozeUntil = snoozeMinutes?.let { occurredAt.plusSeconds(it * 60L).toString() },
        ).also(WearAlertAction::validate)
        val payload = WearContractCodec.encodeAction(action)
        dao.insertAction(
            WearActionOutboxEntity(
                operationId = operationId,
                alertId = alertId,
                payload = payload,
                state = "PENDING",
                attemptCount = 0,
                createdAt = occurredAt.toString(),
                updatedAt = occurredAt.toString(),
            ),
        )
        val status = if (actionType == WearActionType.COMPLETE) {
            WearAlertStatus.COMPLETED
        } else {
            WearAlertStatus.SNOOZED
        }
        dao.upsertAlert(alert.copy(
            status = status.name,
            updatedAt = occurredAt.toString(),
            localFeedbackAction = actionType.name,
            localFeedbackMinutes = snoozeMinutes,
        ))
        action
    }

    suspend fun actionsForSync(): List<WearActionOutboxEntity> = dao.actionsForSync()

    suspend fun markActionStored(operationId: String) {
        UUID.fromString(operationId)
        check(dao.markActionStored(operationId, Instant.now(clock).toString()) == 1)
    }

    suspend fun dismissFeedback(alertId: String) = database.withTransaction {
        val alert = dao.alert(alertId) ?: return@withTransaction
        val pending = dao.actionsForSync().any { it.alertId == alertId }
        if (!pending && alert.status != WearAlertStatus.PENDING.name) {
            dao.deleteAlert(alertId)
        } else {
            dao.upsertAlert(alert.copy(localFeedbackAction = null, localFeedbackMinutes = null))
        }
    }

    private fun WearAlertState.entity(feedbackAction: String?, feedbackMinutes: Int?) = WearAlertEntity(
        alertId = alertId,
        revision = revision,
        text = text,
        reason = reason,
        sourceDeviceId = sourceDeviceId,
        scheduledAt = scheduledAt,
        validUntil = validUntil,
        updatedAt = updatedAt,
        criticality = criticality.name,
        actionsJson = json.encodeToString(actions.map(Enum<*>::name)),
        snoozeOptionsJson = json.encodeToString(snoozeOptionsMinutes),
        status = status.name,
        acknowledgedOperationId = acknowledgedOperationId,
        localFeedbackAction = feedbackAction,
        localFeedbackMinutes = feedbackMinutes,
    )

    private fun WearAlertEntity.visible() = WearVisibleAlert(
        alertId = alertId,
        text = text,
        reason = reason,
        snoozeOptionsMinutes = json.decodeFromString(snoozeOptionsJson),
        status = WearAlertStatus.valueOf(status),
        feedback = localFeedbackAction?.let {
            WearFeedback(WearActionType.valueOf(it), localFeedbackMinutes)
        },
    )
}
