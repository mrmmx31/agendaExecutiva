package com.pessoal.agenda.mobile.recommendation

import com.pessoal.agenda.mobile.data.local.RecommendationEventEntity
import java.time.Duration
import java.time.Instant

data class RecommendationStatistics(
    val totalEvents: Int = 0,
    val medianResponseLatencySeconds: Int? = null,
    val correctedEvents: Int = 0,
    val correctionRatePercent: Int = 0,
    val snoozeEvents: Int = 0,
    val repeatedSnoozeEstimate: Int = 0,
    val missedAlerts: Int = 0,
)

object RecommendationStatisticsCalculator {
    fun calculate(events: List<RecommendationEventEntity>): RecommendationStatistics {
        if (events.isEmpty()) return RecommendationStatistics()
        val latencies = events.mapNotNull { it.responseLatencySeconds }.sorted()
        val corrected = events.count {
            it.correctedAt != null || it.eventType == RecommendationEventType.RECOMMENDATION_CORRECTED.name
        }
        val ordered = events.sortedBy { it.occurredAt }
        return RecommendationStatistics(
            totalEvents = events.size,
            medianResponseLatencySeconds = median(latencies),
            correctedEvents = corrected,
            correctionRatePercent = (corrected * 100.0 / events.size).toInt(),
            snoozeEvents = events.count { it.eventType == RecommendationEventType.ALERT_SNOOZED.name },
            repeatedSnoozeEstimate = ordered.zipWithNext().count { (previous, current) ->
                previous.eventType == RecommendationEventType.ALERT_SNOOZED.name &&
                    current.eventType == RecommendationEventType.ALERT_SNOOZED.name &&
                    previous.activeContext == current.activeContext &&
                    previous.capacityContext == current.capacityContext &&
                    Duration.between(Instant.parse(previous.occurredAt), Instant.parse(current.occurredAt)).seconds in 0..3_600
            },
            missedAlerts = events.count { it.eventType == RecommendationEventType.ALERT_EXPIRED.name },
        )
    }

    private fun median(values: List<Int>): Int? = when {
        values.isEmpty() -> null
        values.size % 2 == 1 -> values[values.size / 2]
        else -> (values[values.size / 2 - 1].toLong() + values[values.size / 2])
            .div(2)
            .toInt()
    }
}
