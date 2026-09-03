package com.pessoal.agenda.mobile.recommendation

import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PersonalSnoozeOptionRanker(
    private val database: MobileDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val recommendationStore = RecommendationStore(database, clock, zoneId)
    private val artifactStore = PersonalModelArtifactStore(database, clock)

    suspend fun rank(
        defaults: List<Int>,
        alertKind: RecommendationAlertKind,
        deadline: Instant,
    ): List<Int> = runCatching {
        val settings = recommendationStore.settings()
        if (!settings.personalizationEnabled) return defaults
        val active = artifactStore.active() ?: return defaults
        val observations = recommendationStore.observations()
        val context = RecommendationContext(
            purpose = RecommendationPurpose.SNOOZE_PRESET,
            generatedAt = Instant.now(clock),
            activeContext = if (database.offline().activeRun() == null) {
                RecommendationActiveContext.NONE
            } else {
                RecommendationActiveContext.PROTOCOL
            },
            capacityContext = settings.capacityContext,
            alertKind = alertKind,
            deadlineBucket = deadlineBucket(Instant.now(clock), deadline),
        )
        val decision = ActivePersonalModelRecommendationEngine(
            primary = DeterministicRecommendationEngine(zoneId),
            storedModel = active,
            zoneId = zoneId,
        ).recommend(context, settings, observations)
        if (decision?.engineId != ActivePersonalModelRecommendationEngine.MODEL_ENGINE_ID) return defaults
        val ranked = decision.options.mapNotNull { it.optionCode.snoozeMinutes() }.filter { it in defaults }
        (ranked + defaults).distinct().take(defaults.size)
    }.getOrDefault(defaults)

    private fun deadlineBucket(now: Instant, deadline: Instant): RecommendationDeadlineBucket {
        val seconds = java.time.Duration.between(now, deadline).seconds
        return when {
            seconds <= 0 -> RecommendationDeadlineBucket.OVERDUE
            seconds <= 15 * 60 -> RecommendationDeadlineBucket.UNDER_15_MIN
            seconds <= 60 * 60 -> RecommendationDeadlineBucket.UNDER_1_HOUR
            seconds <= 24 * 60 * 60 -> RecommendationDeadlineBucket.TODAY
            else -> RecommendationDeadlineBucket.LATER
        }
    }

    private fun RecommendationOptionCode.snoozeMinutes(): Int? = when (this) {
        RecommendationOptionCode.SNOOZE_5 -> 5
        RecommendationOptionCode.SNOOZE_10 -> 10
        RecommendationOptionCode.SNOOZE_15 -> 15
        RecommendationOptionCode.SNOOZE_30 -> 30
        RecommendationOptionCode.SNOOZE_60 -> 60
        else -> null
    }
}
