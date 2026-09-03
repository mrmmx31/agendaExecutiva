package com.pessoal.agenda.mobile.recommendation

import com.pessoal.agenda.mobile.data.local.RecommendationEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommendationStatisticsTest {
    private var id = 0

    @Test
    fun emptyHistoryProducesNeutralStatistics() {
        val statistics = RecommendationStatisticsCalculator.calculate(emptyList())

        assertEquals(0, statistics.totalEvents)
        assertNull(statistics.medianResponseLatencySeconds)
        assertEquals(0, statistics.correctionRatePercent)
    }

    @Test
    fun statisticsUseOnlyExplicitEventsAndBoundedValues() {
        val events = listOf(
            event("ALERT_SNOOZED", "2026-09-02T12:00:00Z", latency = 10),
            event("ALERT_SNOOZED", "2026-09-02T12:20:00Z", latency = 30, corrected = true),
            event("ALERT_COMPLETED", "2026-09-02T12:30:00Z", latency = 20),
            event("ALERT_EXPIRED", "2026-09-02T13:00:00Z"),
        )

        val statistics = RecommendationStatisticsCalculator.calculate(events)

        assertEquals(4, statistics.totalEvents)
        assertEquals(20, statistics.medianResponseLatencySeconds)
        assertEquals(1, statistics.correctedEvents)
        assertEquals(25, statistics.correctionRatePercent)
        assertEquals(2, statistics.snoozeEvents)
        assertEquals(1, statistics.repeatedSnoozeEstimate)
        assertEquals(1, statistics.missedAlerts)
    }

    @Test
    fun snoozesInDifferentExplicitContextsAreNotGrouped() {
        val first = event("ALERT_SNOOZED", "2026-09-02T12:00:00Z")
        val second = event("ALERT_SNOOZED", "2026-09-02T12:10:00Z").copy(activeContext = "PROTOCOL")

        assertEquals(
            0,
            RecommendationStatisticsCalculator.calculate(listOf(first, second)).repeatedSnoozeEstimate,
        )
    }

    private fun event(type: String, at: String, latency: Int? = null, corrected: Boolean = false) =
        RecommendationEventEntity(
            id = "f0000000-0000-4000-8000-${(++id).toString().padStart(12, '0')}",
            contractVersion = 1,
            eventType = type,
            occurredAt = at,
            localHour = 8,
            dayOfWeek = 3,
            sourceDevice = "PHONE",
            activeContext = "NONE",
            capacityContext = "STANDARD",
            alertKind = "TASK",
            deadlineBucket = "TODAY",
            channel = "VISUAL",
            responseLatencySeconds = latency,
            snoozeMinutes = null,
            recommendationId = null,
            optionCode = null,
            correctedAt = if (corrected) "2026-09-02T14:00:00Z" else null,
        )
}
