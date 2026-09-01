package com.pessoal.agenda.mobile.data

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.alert.AlertActionCommand
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertPolicy
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.AlertSuppression
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.QuietHours
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SensoryProfile
import com.pessoal.agenda.mobile.alert.SnoozePolicy
import com.pessoal.agenda.mobile.data.local.AlertActionEntity
import com.pessoal.agenda.mobile.data.local.AlertDefinitionEntity
import com.pessoal.agenda.mobile.data.local.AlertDeliveryEntity
import com.pessoal.agenda.mobile.data.local.AlertMaterializationEntity
import com.pessoal.agenda.mobile.data.local.AlertScheduleRow
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.SensoryProfileEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AlertStore(
    private val database: MobileDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.offline()
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    suspend fun ensureInstallationProfile(): StoredSensoryProfile = database.withTransaction {
        dao.sensoryProfile(PROFILE_ID)?.stored() ?: run {
            val now = instant()
            val profile = SensoryProfile.installationDefault()
            val snooze = SnoozePolicy.cautiousDefault()
            dao.insertSensoryProfile(profile.entity(snooze, now))
            requireNotNull(dao.sensoryProfile(PROFILE_ID)).stored()
        }
    }

    suspend fun saveProfile(profile: SensoryProfile, snooze: SnoozePolicy) {
        profile.validate()
        snooze.validate()
        dao.upsertSensoryProfile(profile.entity(snooze, instant()))
    }

    suspend fun scheduleEvaluation(alertId: String, nextAt: Instant): AlertSchedule? = database.withTransaction {
        UUID.fromString(alertId)
        val definition = requireNotNull(dao.alertDefinition(alertId)) { "Alerta inexistente." }
        val expiration = Instant.parse(definition.validUntil)
        val now = Instant.now(clock)
        if (!nextAt.isBefore(expiration) || !now.isBefore(expiration)) {
            dao.updateAlertEvaluationState(alertId, "EXPIRED", now.toString())
            return@withTransaction null
        }
        check(dao.scheduleAlertEvaluation(alertId, nextAt.toString(), now.toString()) == 1) {
            "Alerta não aceita agendamento."
        }
        AlertSchedule(alertId, nextAt)
    }

    suspend fun schedulesForReconciliation(now: Instant = Instant.now(clock)): List<AlertSchedule> =
        database.withTransaction {
            validSchedules(dao.alertSchedules(), now, reactivate = false)
        }

    suspend fun schedulesForActivation(now: Instant = Instant.now(clock)): List<AlertSchedule> =
        database.withTransaction {
            validSchedules(dao.alertSchedules() + dao.reactivatableAlertSchedules(), now, reactivate = true)
        }

    private suspend fun validSchedules(
        rows: List<AlertScheduleRow>,
        now: Instant,
        reactivate: Boolean,
    ): List<AlertSchedule> = rows.mapNotNull { row ->
        val expiration = Instant.parse(row.validUntil)
        if (!now.isBefore(expiration)) {
            dao.updateAlertEvaluationState(row.alertId, "EXPIRED", now.toString())
            null
        } else {
            val target = maxOf(now, Instant.parse(row.nextEligibleAt))
            if (reactivate) {
                check(dao.scheduleAlertEvaluation(row.alertId, target.toString(), now.toString()) == 1)
            }
            AlertSchedule(row.alertId, target)
        }
    }

    suspend fun cancelScheduling(alertId: String): Boolean {
        UUID.fromString(alertId)
        return dao.cancelAlert(alertId, instant()) == 1
    }

    suspend fun evaluateForWork(
        alertId: String,
        now: Instant = Instant.now(clock),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): AlertWorkEvaluation = database.withTransaction {
        UUID.fromString(alertId)
        val definitionEntity = dao.alertDefinition(alertId) ?: return@withTransaction AlertWorkEvaluation.Stop
        val materialization = dao.alertMaterialization(alertId) ?: return@withTransaction AlertWorkEvaluation.Stop
        if (materialization.state in TERMINAL_WORK_STATES) return@withTransaction AlertWorkEvaluation.Stop
        val storedProfile = requireNotNull(dao.sensoryProfile(PROFILE_ID)) { "Perfil sensorial ausente." }.stored()
        val definition = definitionEntity.definition()
        val decision = AlertPolicy(zoneId).evaluate(
            alert = definition,
            profile = storedProfile.profile,
            now = now,
            deliveryCount = materialization.deliveryCount,
            lastAlertDeliveryAt = materialization.lastDeliveryAt?.let(Instant::parse),
            lastSensoryDeliveryAt = dao.lastSensoryDeliveryAt()?.let(Instant::parse),
        )
        if (decision.shouldDeliver) {
            check(dao.updateAlertEvaluationState(alertId, "AWAITING_DELIVERY", now.toString()) == 1)
            val nextEvaluationAt = now
                .plusSeconds(definition.repeatPolicy.minimumIntervalMinutes * 60L)
                .takeIf {
                    materialization.deliveryCount + 1 < definition.repeatPolicy.maxDeliveries
                        && it.isBefore(Instant.parse(definition.validUntil))
                }
            return@withTransaction AlertWorkEvaluation.Ready(
                candidate = AlertDeliveryCandidate(
                    alertId = definition.alertId,
                    text = definition.text,
                    reason = definition.reason,
                    channels = decision.channels,
                    actions = definition.actions,
                    snoozeMinutes = storedProfile.snoozePolicy.presetMinutes.first(),
                    audioRoute = storedProfile.profile.audioRoute,
                ),
                nextEvaluationAt = nextEvaluationAt,
            )
        }
        val reason = requireNotNull(decision.suppression)
        val nextAt = when (reason) {
            AlertSuppression.NOT_DUE -> Instant.parse(definition.scheduledAt)
            AlertSuppression.PAUSED -> Instant.parse(requireNotNull(storedProfile.profile.pausedUntil))
            AlertSuppression.QUIET_HOURS -> requireNotNull(storedProfile.profile.quietHours)
                .nextEnd(now, zoneId)
            AlertSuppression.REPEAT_INTERVAL -> requireNotNull(materialization.lastDeliveryAt)
                .let(Instant::parse)
                .plusSeconds(definition.repeatPolicy.minimumIntervalMinutes * 60L)
            AlertSuppression.COOLDOWN -> requireNotNull(dao.lastSensoryDeliveryAt())
                .let(Instant::parse)
                .plusSeconds(storedProfile.profile.cooldownMinutes * 60L)
            AlertSuppression.SENSORY_OVERLAP -> now.plusSeconds(storedProfile.profile.cooldownMinutes * 60L)
            AlertSuppression.EXPIRED, AlertSuppression.DELIVERY_LIMIT -> null
            AlertSuppression.GLOBAL_DISABLED, AlertSuppression.NO_ALLOWED_CHANNEL -> null
        }
        if (nextAt != null && nextAt.isBefore(Instant.parse(definition.validUntil))) {
            check(dao.scheduleAlertEvaluation(alertId, nextAt.toString(), now.toString()) == 1)
            AlertWorkEvaluation.Reschedule(nextAt, reason)
        } else {
            val state = when (reason) {
                AlertSuppression.EXPIRED -> "EXPIRED"
                AlertSuppression.DELIVERY_LIMIT -> "DELIVERY_LIMIT_REACHED"
                else -> "SUPPRESSED"
            }
            dao.updateAlertEvaluationState(alertId, state, now.toString())
            AlertWorkEvaluation.Stop
        }
    }

    suspend fun materialize(definition: AlertDefinition): Boolean = database.withTransaction {
        definition.validate()
        val existing = dao.alertDefinition(definition.alertId)
        val createdAt = existing?.createdAt ?: instant()
        val entity = definition.entity(createdAt)
        if (existing != null) {
            check(existing == entity) { "Identificador de alerta reutilizado com outro conteúdo." }
            check(dao.alertMaterialization(definition.alertId) != null) { "Materialização de alerta ausente." }
            return@withTransaction false
        }
        dao.insertAlertDefinition(entity)
        dao.insertAlertMaterialization(
            AlertMaterializationEntity(
                alertId = definition.alertId,
                state = "READY",
                nextEligibleAt = definition.scheduledAt,
                deliveryCount = 0,
                snoozeCount = 0,
                lastDeliveryAt = null,
                completedAt = null,
                updatedAt = createdAt,
            ),
        )
        true
    }

    suspend fun recordDelivery(record: AlertDeliveryRecord): Boolean = database.withTransaction {
        record.validate()
        val existing = dao.alertDelivery(record.deliveryId)
        val entity = record.entity()
        if (existing != null) {
            check(existing == entity) { "Identificador de entrega reutilizado com outro conteúdo." }
            return@withTransaction false
        }
        val definition = requireNotNull(dao.alertDefinition(record.alertId)) { "Alerta da entrega inexistente." }
        dao.insertAlertDelivery(entity)
        when (record.outcome) {
            AlertDeliveryOutcome.DELIVERED -> check(
                dao.markAlertDelivered(record.alertId, record.attemptedAt, definition.maxDeliveries) == 1,
            ) { "Alerta não aceita nova entrega." }
            AlertDeliveryOutcome.SUPPRESSED -> dao.markAlertDeliveryOutcome(
                record.alertId, "SUPPRESSED", record.attemptedAt,
            ).also { check(it == 1) { "Alerta não aceita nova entrega." } }
            AlertDeliveryOutcome.FAILED -> dao.markAlertDeliveryOutcome(
                record.alertId, "DELIVERY_FAILED", record.attemptedAt,
            ).also { check(it == 1) { "Alerta não aceita nova entrega." } }
        }
        true
    }

    suspend fun recordAction(command: AlertActionCommand, policy: SnoozePolicy): Boolean = database.withTransaction {
        command.validate(policy)
        val existing = dao.alertAction(command.operationId)
        val entity = command.entity(instant())
        if (existing != null) {
            check(existing.sameCommand(entity)) {
                "Identificador de ação reutilizado com outro conteúdo."
            }
            return@withTransaction false
        }
        val definition = requireNotNull(dao.alertDefinition(command.alertId)) { "Alerta da ação inexistente." }
        val allowed = json.decodeFromString<List<String>>(definition.actionsJson).toSet()
        check(command.action.name in allowed) { "Ação não permitida pelo alerta." }
        when (command.action) {
            AlertActionType.COMPLETE -> {
                dao.insertAlertAction(entity)
                dao.completeAlert(command.alertId, command.occurredAt)
            }
            AlertActionType.SNOOZE -> {
                check(dao.snoozeAlert(
                    command.alertId,
                    requireNotNull(command.snoozeUntil),
                    command.occurredAt,
                    policy.maximumCount,
                ) == 1) { "Alerta não aceita novo adiamento." }
                dao.insertAlertAction(entity)
            }
        }
        true
    }

    private fun AlertDefinition.entity(createdAt: String) = AlertDefinitionEntity(
        id = alertId,
        contractVersion = contractVersion,
        origin = origin.name,
        referenceId = referenceId,
        text = text,
        reason = reason,
        sourceDeviceId = sourceDeviceId,
        scheduledAt = scheduledAt,
        validUntil = validUntil,
        criticality = criticality.name,
        allowedChannelsJson = json.encodeToString(allowedChannels.map(Enum<*>::name).sorted()),
        maxDeliveries = repeatPolicy.maxDeliveries,
        minimumIntervalMinutes = repeatPolicy.minimumIntervalMinutes,
        actionsJson = json.encodeToString(actions.map(Enum<*>::name).sorted()),
        createdAt = createdAt,
    )

    private fun AlertDefinitionEntity.definition() = AlertDefinition(
        contractVersion = contractVersion,
        alertId = id,
        origin = AlertOrigin.valueOf(origin),
        referenceId = referenceId,
        text = text,
        reason = reason,
        sourceDeviceId = sourceDeviceId,
        scheduledAt = scheduledAt,
        validUntil = validUntil,
        criticality = FunctionalCriticality.valueOf(criticality),
        allowedChannels = json.decodeFromString<List<String>>(allowedChannelsJson)
            .mapTo(mutableSetOf(), SensoryChannel::valueOf),
        repeatPolicy = AlertRepeatPolicy(maxDeliveries, minimumIntervalMinutes),
        actions = json.decodeFromString<List<String>>(actionsJson)
            .mapTo(mutableSetOf(), AlertActionType::valueOf),
    ).also(AlertDefinition::validate)

    private fun SensoryProfile.entity(snooze: SnoozePolicy, now: String) = SensoryProfileEntity(
        id = PROFILE_ID,
        contractVersion = contractVersion,
        globalEnabled = globalEnabled,
        enabledChannelsJson = json.encodeToString(enabledChannels.map(Enum<*>::name).sorted()),
        quietStartsAt = quietHours?.startsAt,
        quietEndsAt = quietHours?.endsAt,
        pausedUntil = pausedUntil,
        cooldownMinutes = cooldownMinutes,
        audioRoute = audioRoute.name,
        snoozePresetMinutesJson = json.encodeToString(snooze.presetMinutes),
        snoozeMinimumMinutes = snooze.minimumMinutes,
        snoozeMaximumMinutes = snooze.maximumMinutes,
        snoozeMaximumCount = snooze.maximumCount,
        updatedAt = now,
    )

    private fun SensoryProfileEntity.stored(): StoredSensoryProfile {
        val quiet = if (quietStartsAt == null && quietEndsAt == null) null else QuietHours(
            requireNotNull(quietStartsAt), requireNotNull(quietEndsAt),
        )
        val profile = SensoryProfile(
            contractVersion = contractVersion,
            globalEnabled = globalEnabled,
            enabledChannels = json.decodeFromString<List<String>>(enabledChannelsJson)
                .mapTo(mutableSetOf(), SensoryChannel::valueOf),
            quietHours = quiet,
            pausedUntil = pausedUntil,
            cooldownMinutes = cooldownMinutes,
            audioRoute = AudioRoutePolicy.valueOf(audioRoute),
        )
        val snooze = SnoozePolicy(
            presetMinutes = json.decodeFromString(snoozePresetMinutesJson),
            minimumMinutes = snoozeMinimumMinutes,
            maximumMinutes = snoozeMaximumMinutes,
            maximumCount = snoozeMaximumCount,
        )
        profile.validate()
        snooze.validate()
        return StoredSensoryProfile(profile, snooze)
    }

    private fun AlertDeliveryRecord.entity() = AlertDeliveryEntity(
        id = deliveryId,
        alertId = alertId,
        deviceId = deviceId,
        channelsJson = json.encodeToString(channels.map(Enum<*>::name).sorted()),
        state = outcome.name,
        technicalReason = technicalReason?.name,
        attemptedAt = attemptedAt,
    )

    private fun AlertActionCommand.entity(createdAt: String) = AlertActionEntity(
        operationId = operationId,
        alertId = alertId,
        sourceDeviceId = sourceDeviceId,
        action = action.name,
        occurredAt = occurredAt,
        snoozeUntil = snoozeUntil,
        syncState = "PENDING",
        createdAt = createdAt,
    )

    private fun AlertActionEntity.sameCommand(other: AlertActionEntity): Boolean =
        operationId == other.operationId
            && alertId == other.alertId
            && sourceDeviceId == other.sourceDeviceId
            && action == other.action
            && occurredAt == other.occurredAt
            && snoozeUntil == other.snoozeUntil

    private fun instant(): String = Instant.now(clock).toString()

    private companion object {
        const val PROFILE_ID = "default"
        val TERMINAL_WORK_STATES = setOf(
            "COMPLETED", "CANCELLED", "EXPIRED", "DELIVERY_LIMIT_REACHED",
        )
    }
}

data class StoredSensoryProfile(val profile: SensoryProfile, val snoozePolicy: SnoozePolicy)
data class AlertSchedule(val alertId: String, val nextAt: Instant)

sealed interface AlertWorkEvaluation {
    data class Ready(
        val candidate: AlertDeliveryCandidate,
        val nextEvaluationAt: Instant?,
    ) : AlertWorkEvaluation
    data class Reschedule(val nextAt: Instant, val reason: AlertSuppression) : AlertWorkEvaluation
    data object Stop : AlertWorkEvaluation
}

data class AlertDeliveryCandidate(
    val alertId: String,
    val text: String,
    val reason: String,
    val channels: Set<SensoryChannel>,
    val actions: Set<AlertActionType>,
    val snoozeMinutes: Int,
    val audioRoute: AudioRoutePolicy,
)

data class AlertDeliveryRecord(
    val deliveryId: String,
    val alertId: String,
    val deviceId: String,
    val channels: Set<SensoryChannel>,
    val outcome: AlertDeliveryOutcome,
    val technicalReason: AlertDeliveryReason?,
    val attemptedAt: String,
) {
    fun validate() {
        UUID.fromString(deliveryId)
        UUID.fromString(alertId)
        UUID.fromString(deviceId)
        Instant.parse(attemptedAt)
        if (outcome == AlertDeliveryOutcome.DELIVERED) {
            require(
                channels.isNotEmpty()
                    && technicalReason in setOf(null, AlertDeliveryReason.AUDIO_FALLBACK, AlertDeliveryReason.PARTIAL_DELIVERY),
            ) { "Entrega confirmada inválida." }
        } else {
            require(technicalReason != null) { "Resultado técnico sem razão." }
        }
    }
}

enum class AlertDeliveryOutcome { DELIVERED, SUPPRESSED, FAILED }

enum class AlertDeliveryReason {
    GLOBAL_DISABLED,
    NOT_DUE,
    EXPIRED,
    DELIVERY_LIMIT,
    PAUSED,
    QUIET_HOURS,
    REPEAT_INTERVAL,
    SENSORY_OVERLAP,
    COOLDOWN,
    NO_ALLOWED_CHANNEL,
    PERMISSION_DENIED,
    ROUTE_UNAVAILABLE,
    SYSTEM_FAILURE,
    SYSTEM_POLICY,
    AUDIO_FALLBACK,
    PARTIAL_DELIVERY,
}
