package com.pessoal.agenda.mobile.recommendation

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.RecommendationDecisionEntity
import com.pessoal.agenda.mobile.data.local.RecommendationEventEntity
import com.pessoal.agenda.mobile.data.local.RecommendationSettingsEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class RecommendationEventType {
    ALERT_PRESENTED, ALERT_COMPLETED, ALERT_SNOOZED, ALERT_EXPIRED,
    PROTOCOL_STARTED, PROTOCOL_STEP_COMPLETED,
    RECOMMENDATION_SHOWN, RECOMMENDATION_ACCEPTED, RECOMMENDATION_CORRECTED,
}

enum class RecommendationSourceDevice { PHONE, WATCH }
enum class RecommendationActiveContext { NONE, FOCUS, PROTOCOL }
enum class RecommendationCapacityContext { STANDARD, REDUCED_EXPLICIT, PARALLEL_EXPLICIT }
enum class RecommendationAlertKind { TASK, FOCUS, PROTOCOL, ROUTINE, OTHER }
enum class RecommendationDeadlineBucket { OVERDUE, UNDER_15_MIN, UNDER_1_HOUR, TODAY, LATER, NONE }
enum class RecommendationChannel { VISUAL, PHONE_AUDIO, PHONE_VIBRATION, WATCH }
enum class RecommendationPurpose { SNOOZE_PRESET, ALERT_CHANNEL, PROTOCOL_SHORTCUT }
enum class RecommendationReason {
    CAUTIOUS_DEFAULT, MANUAL_PREFERENCE, ENOUGH_LOCAL_HISTORY, QUIET_HOURS_GUARD,
    DEVICE_AVAILABLE, ACTIVE_PROTOCOL, DOMAIN_LIMIT_APPLIED, PERSONAL_MODEL,
}
enum class RecommendationOptionCode {
    SNOOZE_5, SNOOZE_10, SNOOZE_15, SNOOZE_30, SNOOZE_60,
    CHANNEL_VISUAL, CHANNEL_AUDIO, CHANNEL_WATCH, PROTOCOL_EXIT,
}

data class RecommendationEventInput(
    val eventType: RecommendationEventType,
    val occurredAt: Instant,
    val sourceDevice: RecommendationSourceDevice,
    val activeContext: RecommendationActiveContext = RecommendationActiveContext.NONE,
    val capacityContext: RecommendationCapacityContext = RecommendationCapacityContext.STANDARD,
    val alertKind: RecommendationAlertKind? = null,
    val deadlineBucket: RecommendationDeadlineBucket? = null,
    val channel: RecommendationChannel? = null,
    val responseLatencySeconds: Int? = null,
    val snoozeMinutes: Int? = null,
    val recommendationId: String? = null,
    val optionCode: RecommendationOptionCode? = null,
)

@Serializable
data class RecommendationOption(
    @SerialName("option_code")
    val optionCode: RecommendationOptionCode,
    val rank: Int,
    @SerialName("reason_code")
    val reasonCode: RecommendationReason,
)

data class RecommendationDecision(
    val id: String,
    val generatedAt: Instant,
    val purpose: RecommendationPurpose,
    val sampleCount: Int,
    val minimumSamples: Int,
    val fallback: Boolean,
    val options: List<RecommendationOption>,
    val engineId: String = ENGINE_ID,
    val ruleVersion: String = RULE_VERSION,
) {
    companion object {
        const val ENGINE_ID = "DETERMINISTIC_RULES"
        const val RULE_VERSION = "rules-v1"
    }
}

data class RecommendationSettings(
    val personalizationEnabled: Boolean,
    val retentionDays: Int,
    val capacityContext: RecommendationCapacityContext,
    val preferredSnoozeMinutes: Int?,
    val preferredChannel: RecommendationChannel?,
)

data class RetentionResult(val deletedEvents: Int, val deletedDecisions: Int)

class RecommendationStore(
    private val database: MobileDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.offline()
    private val json = Json { encodeDefaults = true }

    suspend fun ensureSettings(): RecommendationSettings = database.withTransaction {
        dao.recommendationSettings(SETTINGS_ID)?.toModel() ?: run {
            dao.insertRecommendationSettings(defaultSettingsEntity())
            requireNotNull(dao.recommendationSettings(SETTINGS_ID)).toModel()
        }
    }

    suspend fun settings(): RecommendationSettings = ensureSettings()

    suspend fun saveSettings(settings: RecommendationSettings) = database.withTransaction {
        validate(settings)
        dao.upsertRecommendationSettings(settings.toEntity(now()))
    }

    suspend fun recordEvent(input: RecommendationEventInput): String? = database.withTransaction {
        validate(input)
        if (!currentSettings().personalizationEnabled) return@withTransaction null
        val id = validId()
        dao.insertRecommendationEvent(input.toEntity(id, correctedAt = null))
        id
    }

    suspend fun correctEvent(id: String, corrected: RecommendationEventInput) = database.withTransaction {
        UUID.fromString(id)
        validate(corrected)
        requireNotNull(dao.recommendationEvent(id))
        dao.upsertRecommendationEvent(corrected.toEntity(id, correctedAt = now()))
    }

    suspend fun correctEventContext(
        id: String,
        activeContext: RecommendationActiveContext,
        capacityContext: RecommendationCapacityContext,
    ) = database.withTransaction {
        UUID.fromString(id)
        val current = requireNotNull(dao.recommendationEvent(id))
        val corrected = current.toInput().copy(
            activeContext = activeContext,
            capacityContext = capacityContext,
        )
        validate(corrected)
        dao.upsertRecommendationEvent(corrected.toEntity(id, correctedAt = now()))
    }

    suspend fun events(): List<RecommendationEventEntity> = dao.recommendationEvents()

    suspend fun observations(): List<RecommendationObservation> = dao.recommendationEvents().map { row ->
        RecommendationObservation(
            eventType = RecommendationEventType.valueOf(row.eventType),
            localHour = row.localHour,
            dayOfWeek = row.dayOfWeek,
            activeContext = RecommendationActiveContext.valueOf(row.activeContext),
            capacityContext = RecommendationCapacityContext.valueOf(row.capacityContext),
            alertKind = row.alertKind?.let(RecommendationAlertKind::valueOf),
            optionCode = row.optionCode?.let(RecommendationOptionCode::valueOf),
            sourceDevice = RecommendationSourceDevice.valueOf(row.sourceDevice),
            deadlineBucket = row.deadlineBucket?.let(RecommendationDeadlineBucket::valueOf),
            occurredAt = Instant.parse(row.occurredAt),
        )
    }

    suspend fun recordDecision(decision: RecommendationDecision): Boolean = database.withTransaction {
        validate(decision)
        if (!currentSettings().personalizationEnabled) return@withTransaction false
        dao.insertRecommendationDecision(decision.toEntity())
        true
    }

    suspend fun decisions(): List<RecommendationDecisionEntity> = dao.recommendationDecisions()

    suspend fun enforceRetention(): RetentionResult = database.withTransaction {
        val settings = currentSettings()
        val cutoff = Instant.now(clock).minusSeconds(settings.retentionDays * SECONDS_PER_DAY).toString()
        RetentionResult(
            deletedEvents = dao.deleteRecommendationEventsBefore(cutoff),
            deletedDecisions = dao.deleteRecommendationDecisionsBefore(cutoff),
        )
    }

    suspend fun clearHistory(): RetentionResult = database.withTransaction {
        val result = RetentionResult(
            deletedEvents = dao.deleteRecommendationEvents(),
            deletedDecisions = dao.deleteRecommendationDecisions(),
        )
        dao.deletePersonalModelArtifacts()
        dao.deletePersonalModelShadowMetrics()
        result
    }

    private suspend fun currentSettings(): RecommendationSettings =
        dao.recommendationSettings(SETTINGS_ID)?.toModel() ?: run {
            dao.insertRecommendationSettings(defaultSettingsEntity())
            requireNotNull(dao.recommendationSettings(SETTINGS_ID)).toModel()
        }

    private fun RecommendationEventInput.toEntity(id: String, correctedAt: String?): RecommendationEventEntity {
        val local = occurredAt.atZone(zoneId)
        return RecommendationEventEntity(
            id = id, contractVersion = CONTRACT_VERSION, eventType = eventType.name,
            occurredAt = occurredAt.toString(), localHour = local.hour,
            dayOfWeek = local.dayOfWeek.value, sourceDevice = sourceDevice.name,
            activeContext = activeContext.name, capacityContext = capacityContext.name,
            alertKind = alertKind?.name, deadlineBucket = deadlineBucket?.name,
            channel = channel?.name, responseLatencySeconds = responseLatencySeconds,
            snoozeMinutes = snoozeMinutes, recommendationId = recommendationId,
            optionCode = optionCode?.name, correctedAt = correctedAt,
        )
    }

    private fun RecommendationEventEntity.toInput() = RecommendationEventInput(
        eventType = RecommendationEventType.valueOf(eventType),
        occurredAt = Instant.parse(occurredAt),
        sourceDevice = RecommendationSourceDevice.valueOf(sourceDevice),
        activeContext = RecommendationActiveContext.valueOf(activeContext),
        capacityContext = RecommendationCapacityContext.valueOf(capacityContext),
        alertKind = alertKind?.let(RecommendationAlertKind::valueOf),
        deadlineBucket = deadlineBucket?.let(RecommendationDeadlineBucket::valueOf),
        channel = channel?.let(RecommendationChannel::valueOf),
        responseLatencySeconds = responseLatencySeconds,
        snoozeMinutes = snoozeMinutes,
        recommendationId = recommendationId,
        optionCode = optionCode?.let(RecommendationOptionCode::valueOf),
    )

    private fun RecommendationDecision.toEntity() = RecommendationDecisionEntity(
        id = id, contractVersion = CONTRACT_VERSION, generatedAt = generatedAt.toString(),
        engineId = engineId, ruleVersion = ruleVersion, purpose = purpose.name,
        sampleCount = sampleCount, minimumSamples = minimumSamples, fallback = fallback,
        optionsJson = json.encodeToString(options),
    )

    private fun defaultSettingsEntity() = RecommendationSettingsEntity(
        id = SETTINGS_ID, personalizationEnabled = false, retentionDays = DEFAULT_RETENTION_DAYS,
        capacityContext = RecommendationCapacityContext.STANDARD.name,
        preferredSnoozeMinutes = null, preferredChannel = null, updatedAt = now(),
    )

    private fun RecommendationSettings.toEntity(updatedAt: String) = RecommendationSettingsEntity(
        id = SETTINGS_ID, personalizationEnabled = personalizationEnabled,
        retentionDays = retentionDays, capacityContext = capacityContext.name,
        preferredSnoozeMinutes = preferredSnoozeMinutes,
        preferredChannel = preferredChannel?.name, updatedAt = updatedAt,
    )

    private fun RecommendationSettingsEntity.toModel() = RecommendationSettings(
        personalizationEnabled = personalizationEnabled, retentionDays = retentionDays,
        capacityContext = RecommendationCapacityContext.valueOf(capacityContext),
        preferredSnoozeMinutes = preferredSnoozeMinutes,
        preferredChannel = preferredChannel?.let(RecommendationChannel::valueOf),
    )

    private fun validate(input: RecommendationEventInput) {
        require(input.responseLatencySeconds == null || input.responseLatencySeconds in 0..86_400)
        require(input.snoozeMinutes == null || input.snoozeMinutes in 5..240)
        input.recommendationId?.let(UUID::fromString)
    }

    private fun validate(decision: RecommendationDecision) {
        UUID.fromString(decision.id)
        require(
            (decision.engineId == RecommendationDecision.ENGINE_ID &&
                decision.ruleVersion == RecommendationDecision.RULE_VERSION) ||
                (decision.engineId == ActivePersonalModelRecommendationEngine.MODEL_ENGINE_ID &&
                    decision.ruleVersion.matches(Regex("[a-zA-Z0-9._-]{1,64}"))),
        )
        require(decision.sampleCount >= 0 && decision.minimumSamples > 0)
        require(decision.fallback || decision.sampleCount >= decision.minimumSamples)
        require(decision.options.size in 1..3)
        require(decision.options.map { it.rank } == (1..decision.options.size).toList())
        require(decision.options.map { it.optionCode }.distinct().size == decision.options.size)
    }

    private fun validate(settings: RecommendationSettings) {
        require(settings.retentionDays in MIN_RETENTION_DAYS..MAX_RETENTION_DAYS)
        require(settings.preferredSnoozeMinutes == null || settings.preferredSnoozeMinutes in ALLOWED_SNOOZE_MINUTES)
        require(settings.preferredChannel != RecommendationChannel.PHONE_VIBRATION)
    }

    private fun validId() = newId().also(UUID::fromString)
    private fun now() = Instant.now(clock).toString()

    companion object {
        const val CONTRACT_VERSION = 1
        const val SETTINGS_ID = "installation"
        const val DEFAULT_RETENTION_DAYS = 90
        const val MIN_RETENTION_DAYS = 7
        const val MAX_RETENTION_DAYS = 365
        private const val SECONDS_PER_DAY = 86_400L
        private val ALLOWED_SNOOZE_MINUTES = setOf(5, 10, 15, 30, 60)
    }
}
