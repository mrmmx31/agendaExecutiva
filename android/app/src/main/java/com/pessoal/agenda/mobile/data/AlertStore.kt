package com.pessoal.agenda.mobile.data

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.alert.AlertActionCommand
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.QuietHours
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SensoryProfile
import com.pessoal.agenda.mobile.alert.SnoozePolicy
import com.pessoal.agenda.mobile.data.local.AlertActionEntity
import com.pessoal.agenda.mobile.data.local.AlertDefinitionEntity
import com.pessoal.agenda.mobile.data.local.AlertDeliveryEntity
import com.pessoal.agenda.mobile.data.local.AlertMaterializationEntity
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.SensoryProfileEntity
import java.time.Clock
import java.time.Instant
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

    private companion object { const val PROFILE_ID = "default" }
}

data class StoredSensoryProfile(val profile: SensoryProfile, val snoozePolicy: SnoozePolicy)

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
            require(channels.isNotEmpty() && technicalReason == null) { "Entrega confirmada inválida." }
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
}
