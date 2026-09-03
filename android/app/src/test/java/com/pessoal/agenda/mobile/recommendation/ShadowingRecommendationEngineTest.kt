package com.pessoal.agenda.mobile.recommendation

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShadowingRecommendationEngineTest {
    private val settings = RecommendationSettings(
        personalizationEnabled = true,
        retentionDays = 90,
        capacityContext = RecommendationCapacityContext.STANDARD,
        preferredSnoozeMinutes = null,
        preferredChannel = null,
    )
    private val context = RecommendationContext(
        purpose = RecommendationPurpose.SNOOZE_PRESET,
        generatedAt = Instant.parse("2026-09-03T12:00:00Z"),
        activeContext = RecommendationActiveContext.NONE,
        capacityContext = RecommendationCapacityContext.STANDARD,
        alertKind = RecommendationAlertKind.TASK,
        deadlineBucket = RecommendationDeadlineBucket.TODAY,
    )

    @Test
    fun shadowReturnsTheExactPrimaryDecisionAndOnlyRecordsComparison() {
        val primaryDecision = decision(RecommendationOptionCode.SNOOZE_15)
        val accumulator = ShadowMetricsAccumulator()
        val engine = ShadowingRecommendationEngine(
            primary = FixedEngine(primaryDecision),
            zoneId = ZoneId.of("UTC"),
            onComparison = accumulator::record,
        )

        val returned = engine.recommend(context, settings, history(60))

        assertSame(primaryDecision, returned)
        assertEquals(1, accumulator.snapshot().evaluatedCount)
    }

    @Test
    fun insufficientHistoryDoesNotPretendShadowWasEvaluated() {
        val accumulator = ShadowMetricsAccumulator()
        val engine = ShadowingRecommendationEngine(
            primary = FixedEngine(decision(RecommendationOptionCode.SNOOZE_15)),
            onComparison = accumulator::record,
        )

        engine.recommend(context, settings, history(59))

        assertEquals(ShadowMetrics(0, 0), accumulator.snapshot())
    }

    @Test
    fun optOutAndUnsupportedPurposeNeverRunShadow() {
        val accumulator = ShadowMetricsAccumulator()
        val engine = ShadowingRecommendationEngine(
            primary = FixedEngine(decision(RecommendationOptionCode.SNOOZE_15)),
            onComparison = accumulator::record,
        )

        engine.recommend(context, settings.copy(personalizationEnabled = false), history(60))
        engine.recommend(context.copy(purpose = RecommendationPurpose.ALERT_CHANNEL), settings, history(60))

        assertEquals(0, accumulator.snapshot().evaluatedCount)
    }

    @Test
    fun incompleteAndNonSnoozeEventsAreExcludedFromTraining() {
        val accumulator = ShadowMetricsAccumulator()
        val engine = ShadowingRecommendationEngine(
            primary = FixedEngine(decision(RecommendationOptionCode.SNOOZE_15)),
            onComparison = accumulator::record,
        )
        val invalid = history(60).mapIndexed { index, observation ->
            when {
                index < 20 -> observation.copy(alertKind = null)
                index < 40 -> observation.copy(deadlineBucket = null)
                else -> observation.copy(eventType = RecommendationEventType.ALERT_COMPLETED)
            }
        }

        engine.recommend(context, settings, invalid)

        assertEquals(0, accumulator.snapshot().evaluatedCount)
    }

    @Test
    fun trainerOrObserverFailureNeverChangesPrimaryOutput() {
        val primaryDecision = decision(RecommendationOptionCode.SNOOZE_15)
        val trainerFailure = ShadowingRecommendationEngine(
            primary = FixedEngine(primaryDecision),
            trainer = AuditableLinearTrainer(epochs = 0),
        )
        val observerFailure = ShadowingRecommendationEngine(
            primary = FixedEngine(primaryDecision),
            onComparison = { error("observer failure") },
        )

        assertSame(primaryDecision, trainerFailure.recommend(context, settings, history(60)))
        assertSame(primaryDecision, observerFailure.recommend(context, settings, history(60)))
    }

    @Test
    fun shadowCapsTrainingToMostRecentTwoThousandSamples() {
        var comparison: ShadowComparison? = null
        val engine = ShadowingRecommendationEngine(
            primary = FixedEngine(decision(RecommendationOptionCode.SNOOZE_15)),
            zoneId = ZoneId.of("UTC"),
            onComparison = { comparison = it },
        )

        engine.recommend(context, settings, history(2_001))

        assertEquals(ShadowingRecommendationEngine.MAXIMUM_TRAINING_SAMPLES, comparison?.trainingSampleCount)
    }

    private fun history(count: Int) = List(count) { index ->
        RecommendationObservation(
            eventType = RecommendationEventType.ALERT_SNOOZED,
            localHour = 12,
            dayOfWeek = 3,
            activeContext = RecommendationActiveContext.NONE,
            capacityContext = RecommendationCapacityContext.STANDARD,
            alertKind = RecommendationAlertKind.TASK,
            optionCode = if (index % 3 == 0) {
                RecommendationOptionCode.SNOOZE_30
            } else {
                RecommendationOptionCode.SNOOZE_15
            },
            sourceDevice = RecommendationSourceDevice.PHONE,
            deadlineBucket = RecommendationDeadlineBucket.TODAY,
            occurredAt = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(index.toLong()),
        )
    }

    private fun decision(top: RecommendationOptionCode) = RecommendationDecision(
        id = "e2000000-0000-4000-8000-000000000001",
        generatedAt = context.generatedAt,
        purpose = RecommendationPurpose.SNOOZE_PRESET,
        sampleCount = 60,
        minimumSamples = 12,
        fallback = false,
        options = listOf(RecommendationOption(top, 1, RecommendationReason.ENOUGH_LOCAL_HISTORY)),
    )

    private class FixedEngine(private val decision: RecommendationDecision) : RecommendationEngine {
        override fun recommend(
            context: RecommendationContext,
            settings: RecommendationSettings,
            observations: List<RecommendationObservation>,
        ) = decision
    }
}
