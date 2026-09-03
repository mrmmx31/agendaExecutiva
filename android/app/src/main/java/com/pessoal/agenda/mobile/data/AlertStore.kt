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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.contract.WearAlertState
import com.pessoal.agenda.wear.contract.WearAlertStatus
import com.pessoal.agenda.wear.contract.WearCriticality
import com.pessoal.agenda.mobile.recommendation.RecommendationActiveContext
import com.pessoal.agenda.mobile.recommendation.RecommendationAlertKind
import com.pessoal.agenda.mobile.recommendation.RecommendationChannel
import com.pessoal.agenda.mobile.recommendation.RecommendationDeadlineBucket
import com.pessoal.agenda.mobile.recommendation.RecommendationEventType
import com.pessoal.agenda.mobile.recommendation.RecommendationOptionCode
import com.pessoal.agenda.mobile.recommendation.RecommendationSourceDevice
import com.pessoal.agenda.mobile.recommendation.RecommendationStore
import com.pessoal.agenda.mobile.recommendation.RecommendationTelemetry
import com.pessoal.agenda.mobile.recommendation.PersonalSnoozeOptionRanker

class AlertStore(
    private val database: MobileDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val recommendationTelemetry: RecommendationTelemetry = RecommendationTelemetry(
        RecommendationStore(database, clock),
    ),
    private val personalSnoozeOptionRanker: PersonalSnoozeOptionRanker = PersonalSnoozeOptionRanker(
        database,
        clock,
    ),
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
    ): AlertWorkEvaluation {
        UUID.fromString(alertId)
        val previousState = dao.alertMaterialization(alertId)?.state
        val evaluation = database.withTransaction {
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
        val current = dao.alertMaterialization(alertId)
        if (previousState != "EXPIRED" && current?.state == "EXPIRED") {
            dao.alertDefinition(alertId)?.telemetryEvent(
                type = RecommendationEventType.ALERT_EXPIRED,
                at = now,
                source = RecommendationSourceDevice.PHONE,
            )?.record()
        }
        return evaluation
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
                wearRevision = 1,
            ),
        )
        true
    }

    suspend fun recordDelivery(record: AlertDeliveryRecord): Boolean {
        var event: AlertTelemetryEvent? = null
        val inserted = database.withTransaction {
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
                AlertDeliveryOutcome.DELIVERED -> {
                    check(dao.markAlertDelivered(record.alertId, record.attemptedAt, definition.maxDeliveries) == 1) {
                        "Alerta não aceita nova entrega."
                    }
                    event = definition.telemetryEvent(
                        type = RecommendationEventType.ALERT_PRESENTED,
                        at = Instant.parse(record.attemptedAt),
                        source = RecommendationSourceDevice.PHONE,
                        channel = record.channels.primaryRecommendationChannel(),
                    )
                }
                AlertDeliveryOutcome.SUPPRESSED -> dao.markAlertDeliveryOutcome(
                    record.alertId, "SUPPRESSED", record.attemptedAt,
                ).also { check(it == 1) { "Alerta não aceita nova entrega." } }
                AlertDeliveryOutcome.FAILED -> dao.markAlertDeliveryOutcome(
                    record.alertId, "DELIVERY_FAILED", record.attemptedAt,
                ).also { check(it == 1) { "Alerta não aceita nova entrega." } }
            }
            true
        }
        event?.record()
        return inserted
    }

    suspend fun recordAction(
        command: AlertActionCommand,
        policy: SnoozePolicy,
        sourceDevice: RecommendationSourceDevice = RecommendationSourceDevice.PHONE,
    ): Boolean {
        var event: AlertTelemetryEvent? = null
        val inserted = database.withTransaction {
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
            val materialization = requireNotNull(dao.alertMaterialization(command.alertId))
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
            val occurredAt = Instant.parse(command.occurredAt)
            val snoozeMinutes = command.snoozeUntil?.let {
                Duration.between(occurredAt, Instant.parse(it)).toMinutes().toInt()
            }
            event = definition.telemetryEvent(
                type = if (command.action == AlertActionType.COMPLETE) {
                    RecommendationEventType.ALERT_COMPLETED
                } else {
                    RecommendationEventType.ALERT_SNOOZED
                },
                at = occurredAt,
                source = sourceDevice,
                responseLatencySeconds = materialization.lastDeliveryAt?.let {
                    Duration.between(Instant.parse(it), occurredAt).seconds.takeIf { seconds -> seconds in 0..86_400 }
                        ?.toInt()
                },
                snoozeMinutes = snoozeMinutes,
                optionCode = snoozeMinutes?.recommendationOption(),
            )
            true
        }
        event?.record()
        return inserted
    }

    suspend fun wearState(alertId: String): WearAlertState? = database.withTransaction {
        UUID.fromString(alertId)
        val definition = dao.alertDefinition(alertId) ?: return@withTransaction null
        val materialization = dao.alertMaterialization(alertId) ?: return@withTransaction null
        val availableActions = json.decodeFromString<List<String>>(definition.actionsJson).toSet()
        if (availableActions != setOf(AlertActionType.COMPLETE.name, AlertActionType.SNOOZE.name)) {
            return@withTransaction null
        }
        val profile = dao.sensoryProfile(PROFILE_ID)?.stored() ?: return@withTransaction null
        val snoozeOptions = personalSnoozeOptionRanker.rank(
            defaults = profile.snoozePolicy.presetMinutes.take(WearAlertState.MAX_SNOOZE_OPTIONS),
            alertKind = when (AlertOrigin.valueOf(definition.origin)) {
                AlertOrigin.TASK -> RecommendationAlertKind.TASK
                AlertOrigin.PROTOCOL -> RecommendationAlertKind.PROTOCOL
                AlertOrigin.MANUAL -> RecommendationAlertKind.OTHER
            },
            deadline = Instant.parse(definition.validUntil),
        )
        val latestAction = dao.latestAlertAction(alertId)
        val lastDelivery = materialization.lastDeliveryAt?.let(Instant::parse)
        val latestOccurred = latestAction?.occurredAt?.let(Instant::parse)
        val status = when {
            materialization.state == "COMPLETED" -> WearAlertStatus.COMPLETED
            materialization.state == "CANCELLED" -> WearAlertStatus.CANCELLED
            materialization.state in setOf("EXPIRED", "DELIVERY_LIMIT_REACHED") -> WearAlertStatus.EXPIRED
            latestAction?.action == AlertActionType.SNOOZE.name &&
                (lastDelivery == null || requireNotNull(latestOccurred).isAfter(lastDelivery)) -> WearAlertStatus.SNOOZED
            else -> WearAlertStatus.PENDING
        }
        WearAlertState(
            contractVersion = WearAlertState.CONTRACT_VERSION,
            alertId = definition.id,
            revision = materialization.wearRevision,
            text = definition.text,
            reason = definition.reason,
            sourceDeviceId = definition.sourceDeviceId,
            scheduledAt = definition.scheduledAt,
            validUntil = definition.validUntil,
            updatedAt = materialization.updatedAt,
            criticality = WearCriticality.valueOf(definition.criticality),
            actions = WearAlertState.REQUIRED_ACTIONS,
            snoozeOptionsMinutes = snoozeOptions,
            status = status,
            acknowledgedOperationId = latestAction?.operationId,
        ).also(WearAlertState::validate)
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

    private suspend fun AlertDefinitionEntity.telemetryEvent(
        type: RecommendationEventType,
        at: Instant,
        source: RecommendationSourceDevice,
        channel: RecommendationChannel? = null,
        responseLatencySeconds: Int? = null,
        snoozeMinutes: Int? = null,
        optionCode: RecommendationOptionCode? = null,
    ) = AlertTelemetryEvent(
        type = type,
        at = at,
        source = source,
        activeContext = if (dao.activeRun() == null) {
            RecommendationActiveContext.NONE
        } else {
            RecommendationActiveContext.PROTOCOL
        },
        alertKind = when (AlertOrigin.valueOf(origin)) {
            AlertOrigin.TASK -> RecommendationAlertKind.TASK
            AlertOrigin.PROTOCOL -> RecommendationAlertKind.PROTOCOL
            AlertOrigin.MANUAL -> RecommendationAlertKind.OTHER
        },
        deadlineBucket = deadlineBucket(at, Instant.parse(validUntil)),
        channel = channel,
        responseLatencySeconds = responseLatencySeconds,
        snoozeMinutes = snoozeMinutes,
        optionCode = optionCode,
    )

    private suspend fun AlertTelemetryEvent.record() {
        recommendationTelemetry.record(
            eventType = type,
            occurredAt = at,
            sourceDevice = source,
            activeContext = activeContext,
            alertKind = alertKind,
            deadlineBucket = deadlineBucket,
            channel = channel,
            responseLatencySeconds = responseLatencySeconds,
            snoozeMinutes = snoozeMinutes,
            optionCode = optionCode,
        )
    }

    private fun Set<SensoryChannel>.primaryRecommendationChannel(): RecommendationChannel? = when {
        SensoryChannel.WEAR_VIBRATION in this -> RecommendationChannel.WATCH
        SensoryChannel.AUDIO in this -> RecommendationChannel.PHONE_AUDIO
        SensoryChannel.PHONE_VIBRATION in this -> RecommendationChannel.PHONE_VIBRATION
        SensoryChannel.VISUAL in this -> RecommendationChannel.VISUAL
        else -> null
    }

    private fun Int.recommendationOption(): RecommendationOptionCode? = when (this) {
        5 -> RecommendationOptionCode.SNOOZE_5
        10 -> RecommendationOptionCode.SNOOZE_10
        15 -> RecommendationOptionCode.SNOOZE_15
        30 -> RecommendationOptionCode.SNOOZE_30
        60 -> RecommendationOptionCode.SNOOZE_60
        else -> null
    }

    private fun deadlineBucket(at: Instant, deadline: Instant): RecommendationDeadlineBucket {
        val seconds = Duration.between(at, deadline).seconds
        return when {
            seconds <= 0 -> RecommendationDeadlineBucket.OVERDUE
            seconds <= 15 * 60 -> RecommendationDeadlineBucket.UNDER_15_MIN
            seconds <= 60 * 60 -> RecommendationDeadlineBucket.UNDER_1_HOUR
            seconds <= 24 * 60 * 60 -> RecommendationDeadlineBucket.TODAY
            else -> RecommendationDeadlineBucket.LATER
        }
    }

    private data class AlertTelemetryEvent(
        val type: RecommendationEventType,
        val at: Instant,
        val source: RecommendationSourceDevice,
        val activeContext: RecommendationActiveContext,
        val alertKind: RecommendationAlertKind,
        val deadlineBucket: RecommendationDeadlineBucket,
        val channel: RecommendationChannel?,
        val responseLatencySeconds: Int?,
        val snoozeMinutes: Int?,
        val optionCode: RecommendationOptionCode?,
    )

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
