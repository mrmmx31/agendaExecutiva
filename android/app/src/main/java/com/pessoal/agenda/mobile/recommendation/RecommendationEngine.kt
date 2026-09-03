package com.pessoal.agenda.mobile.recommendation

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class RecommendationContext(
    val purpose: RecommendationPurpose,
    val generatedAt: Instant,
    val activeContext: RecommendationActiveContext = RecommendationActiveContext.NONE,
    val capacityContext: RecommendationCapacityContext = RecommendationCapacityContext.STANDARD,
    val alertKind: RecommendationAlertKind? = null,
    val deadlineBucket: RecommendationDeadlineBucket? = null,
    val availableChannels: Set<RecommendationChannel> = setOf(RecommendationChannel.VISUAL),
    val quietHours: Boolean = false,
    val protocolAvailable: Boolean = false,
)

data class RecommendationObservation(
    val eventType: RecommendationEventType,
    val localHour: Int,
    val dayOfWeek: Int,
    val activeContext: RecommendationActiveContext,
    val capacityContext: RecommendationCapacityContext,
    val alertKind: RecommendationAlertKind?,
    val optionCode: RecommendationOptionCode?,
)

interface RecommendationEngine {
    fun recommend(
        context: RecommendationContext,
        settings: RecommendationSettings,
        observations: List<RecommendationObservation>,
    ): RecommendationDecision?
}

class DeterministicRecommendationEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : RecommendationEngine {
    override fun recommend(
        context: RecommendationContext,
        settings: RecommendationSettings,
        observations: List<RecommendationObservation>,
    ): RecommendationDecision? {
        require(settings.retentionDays in RecommendationStore.MIN_RETENTION_DAYS..RecommendationStore.MAX_RETENTION_DAYS)
        require(observations.all { it.localHour in 0..23 && it.dayOfWeek in 1..7 })
        val eligible = observations.filter { it.matches(context) }
        val fallback = !settings.personalizationEnabled || eligible.size < MINIMUM_SAMPLES
        val ranked = when (context.purpose) {
            RecommendationPurpose.SNOOZE_PRESET -> snoozeOptions(context, settings, eligible, fallback)
            RecommendationPurpose.ALERT_CHANNEL -> channelOptions(context, settings, eligible, fallback)
            RecommendationPurpose.PROTOCOL_SHORTCUT -> protocolOptions(context)
        }
        if (ranked.isEmpty()) return null
        val decision = RecommendationDecision(
            id = newId().also(UUID::fromString),
            generatedAt = context.generatedAt,
            purpose = context.purpose,
            sampleCount = eligible.size,
            minimumSamples = MINIMUM_SAMPLES,
            fallback = fallback,
            options = ranked.take(MAX_OPTIONS).mapIndexed { index, candidate ->
                RecommendationOption(candidate.code, index + 1, candidate.reason)
            },
        )
        validateOutput(decision, context)
        return decision
    }

    private fun snoozeOptions(
        context: RecommendationContext,
        settings: RecommendationSettings,
        eligible: List<RecommendationObservation>,
        fallback: Boolean,
    ): List<Candidate> {
        val baseline = when {
            context.deadlineBucket in URGENT_DEADLINES -> listOf(
                RecommendationOptionCode.SNOOZE_5,
                RecommendationOptionCode.SNOOZE_10,
                RecommendationOptionCode.SNOOZE_15,
            )
            context.capacityContext == RecommendationCapacityContext.REDUCED_EXPLICIT -> listOf(
                RecommendationOptionCode.SNOOZE_15,
                RecommendationOptionCode.SNOOZE_30,
                RecommendationOptionCode.SNOOZE_10,
            )
            else -> listOf(
                RecommendationOptionCode.SNOOZE_15,
                RecommendationOptionCode.SNOOZE_30,
                RecommendationOptionCode.SNOOZE_10,
            )
        }
        val manual = settings.preferredSnoozeMinutes?.let(::snoozeCode)
        if (manual != null) {
            return (listOf(Candidate(manual, RecommendationReason.MANUAL_PREFERENCE)) +
                baseline.filterNot { it == manual }.map { Candidate(it, RecommendationReason.CAUTIOUS_DEFAULT) })
        }
        if (!fallback) return rankedByHistory(eligible, SNOOZE_OPTIONS, baseline)
        return baseline.map { Candidate(it, RecommendationReason.CAUTIOUS_DEFAULT) }
    }

    private fun channelOptions(
        context: RecommendationContext,
        settings: RecommendationSettings,
        eligible: List<RecommendationObservation>,
        fallback: Boolean,
    ): List<Candidate> {
        val available = buildList {
            if (RecommendationChannel.VISUAL in context.availableChannels) add(RecommendationOptionCode.CHANNEL_VISUAL)
            if (!context.quietHours && RecommendationChannel.PHONE_AUDIO in context.availableChannels) {
                add(RecommendationOptionCode.CHANNEL_AUDIO)
            }
            if (RecommendationChannel.WATCH in context.availableChannels) add(RecommendationOptionCode.CHANNEL_WATCH)
        }
        if (available.isEmpty()) return emptyList()
        val baseline = CHANNEL_BASELINE.filter { it in available }
        val manual = settings.preferredChannel?.let(::channelCode)
        if (manual != null && manual in available) {
            return (listOf(Candidate(manual, RecommendationReason.MANUAL_PREFERENCE)) +
                baseline.filterNot { it == manual }.map { Candidate(it, channelReason(it, context)) })
        }
        if (!fallback) return rankedByHistory(eligible, available.toSet(), baseline)
        val constrainedReason = if (manual != null && manual !in available) {
            RecommendationReason.DOMAIN_LIMIT_APPLIED
        } else null
        return baseline.mapIndexed { index, code ->
            Candidate(code, constrainedReason.takeIf { index == 0 } ?: channelReason(code, context))
        }
    }

    private fun protocolOptions(context: RecommendationContext): List<Candidate> {
        if (!context.protocolAvailable) return emptyList()
        val reason = if (context.activeContext == RecommendationActiveContext.PROTOCOL) {
            RecommendationReason.ACTIVE_PROTOCOL
        } else {
            RecommendationReason.CAUTIOUS_DEFAULT
        }
        return listOf(Candidate(RecommendationOptionCode.PROTOCOL_EXIT, reason))
    }

    private fun rankedByHistory(
        eligible: List<RecommendationObservation>,
        allowed: Set<RecommendationOptionCode>,
        baseline: List<RecommendationOptionCode>,
    ): List<Candidate> {
        val baselineRank = baseline.withIndex().associate { it.value to it.index }
        val counts = eligible.mapNotNull { it.optionCode }.filter { it in allowed }.groupingBy { it }.eachCount()
        return allowed.sortedWith(
            compareByDescending<RecommendationOptionCode> { counts[it] ?: 0 }
                .thenBy { baselineRank[it] ?: Int.MAX_VALUE }
                .thenBy { it.name },
        ).map { Candidate(it, RecommendationReason.ENOUGH_LOCAL_HISTORY) }
    }

    private fun RecommendationObservation.matches(context: RecommendationContext): Boolean {
        val option = optionCode ?: return false
        if (eventType !in PREFERENCE_EVENTS || option !in optionsFor(context.purpose)) return false
        val local = context.generatedAt.atZone(zoneId)
        return activeContext == context.activeContext &&
            capacityContext == context.capacityContext &&
            alertKind == context.alertKind &&
            dayGroup(dayOfWeek) == dayGroup(local.dayOfWeek.value) &&
            dayPart(localHour) == dayPart(local.hour)
    }

    private fun validateOutput(decision: RecommendationDecision, context: RecommendationContext) {
        require(decision.options.isNotEmpty() && decision.options.size <= MAX_OPTIONS)
        require(decision.options.map { it.rank } == (1..decision.options.size).toList())
        require(decision.options.map { it.optionCode }.distinct().size == decision.options.size)
        require(decision.options.all { it.optionCode in optionsFor(context.purpose) })
        if (context.purpose == RecommendationPurpose.ALERT_CHANNEL) {
            require(decision.options.none {
                it.optionCode == RecommendationOptionCode.CHANNEL_AUDIO && context.quietHours
            })
        }
    }

    private fun optionsFor(purpose: RecommendationPurpose): Set<RecommendationOptionCode> = when (purpose) {
        RecommendationPurpose.SNOOZE_PRESET -> SNOOZE_OPTIONS
        RecommendationPurpose.ALERT_CHANNEL -> CHANNEL_OPTIONS
        RecommendationPurpose.PROTOCOL_SHORTCUT -> setOf(RecommendationOptionCode.PROTOCOL_EXIT)
    }

    private fun snoozeCode(minutes: Int): RecommendationOptionCode? = when (minutes) {
        5 -> RecommendationOptionCode.SNOOZE_5
        10 -> RecommendationOptionCode.SNOOZE_10
        15 -> RecommendationOptionCode.SNOOZE_15
        30 -> RecommendationOptionCode.SNOOZE_30
        60 -> RecommendationOptionCode.SNOOZE_60
        else -> null
    }

    private fun channelCode(channel: RecommendationChannel): RecommendationOptionCode? = when (channel) {
        RecommendationChannel.VISUAL -> RecommendationOptionCode.CHANNEL_VISUAL
        RecommendationChannel.PHONE_AUDIO -> RecommendationOptionCode.CHANNEL_AUDIO
        RecommendationChannel.WATCH -> RecommendationOptionCode.CHANNEL_WATCH
        RecommendationChannel.PHONE_VIBRATION -> null
    }

    private fun channelReason(code: RecommendationOptionCode, context: RecommendationContext): RecommendationReason =
        when {
            context.quietHours -> RecommendationReason.QUIET_HOURS_GUARD
            code == RecommendationOptionCode.CHANNEL_WATCH -> RecommendationReason.DEVICE_AVAILABLE
            else -> RecommendationReason.CAUTIOUS_DEFAULT
        }

    private fun dayGroup(day: Int) = if (day in 1..5) 0 else 1
    private fun dayPart(hour: Int) = when (hour) {
        in 5..11 -> 0
        in 12..17 -> 1
        in 18..22 -> 2
        else -> 3
    }

    private data class Candidate(val code: RecommendationOptionCode, val reason: RecommendationReason)

    companion object {
        const val MINIMUM_SAMPLES = 12
        const val MAX_OPTIONS = 3
        private val PREFERENCE_EVENTS = setOf(
            RecommendationEventType.ALERT_SNOOZED,
            RecommendationEventType.RECOMMENDATION_ACCEPTED,
            RecommendationEventType.RECOMMENDATION_CORRECTED,
        )
        private val URGENT_DEADLINES = setOf(
            RecommendationDeadlineBucket.OVERDUE,
            RecommendationDeadlineBucket.UNDER_15_MIN,
        )
        private val SNOOZE_OPTIONS = setOf(
            RecommendationOptionCode.SNOOZE_5,
            RecommendationOptionCode.SNOOZE_10,
            RecommendationOptionCode.SNOOZE_15,
            RecommendationOptionCode.SNOOZE_30,
            RecommendationOptionCode.SNOOZE_60,
        )
        private val CHANNEL_OPTIONS = setOf(
            RecommendationOptionCode.CHANNEL_VISUAL,
            RecommendationOptionCode.CHANNEL_AUDIO,
            RecommendationOptionCode.CHANNEL_WATCH,
        )
        private val CHANNEL_BASELINE = listOf(
            RecommendationOptionCode.CHANNEL_VISUAL,
            RecommendationOptionCode.CHANNEL_WATCH,
            RecommendationOptionCode.CHANNEL_AUDIO,
        )
    }
}
