package com.pessoal.agenda.mobile.recommendation

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class RecommendationEngineTest {
    private val engine = DeterministicRecommendationEngine(
        zoneId = ZoneId.of("America/Manaus"),
        newId = { DECISION_ID },
    )

    @Test
    fun disabledPersonalizationUsesCautiousBaselineWithoutHistory() {
        val decision = requireNotNull(engine.recommend(context(), settings(enabled = false), emptyList()))

        assertTrue(decision.fallback)
        assertEquals(0, decision.sampleCount)
        assertEquals(
            listOf(
                RecommendationOptionCode.SNOOZE_15,
                RecommendationOptionCode.SNOOZE_30,
                RecommendationOptionCode.SNOOZE_10,
            ),
            decision.options.map { it.optionCode },
        )
        assertTrue(decision.options.all { it.reasonCode == RecommendationReason.CAUTIOUS_DEFAULT })
    }

    @Test
    fun disabledPersonalizationKeepsFallbackEvenWithEnoughHistory() {
        val decision = requireNotNull(engine.recommend(
            context(),
            settings(enabled = false),
            observations(12, RecommendationOptionCode.SNOOZE_30),
        ))

        assertTrue(decision.fallback)
        assertEquals(12, decision.sampleCount)
        assertEquals(RecommendationOptionCode.SNOOZE_15, decision.options.first().optionCode)
    }

    @Test
    fun urgentDeadlineUsesShorterCautiousBaseline() {
        val decision = requireNotNull(engine.recommend(
            context().copy(deadlineBucket = RecommendationDeadlineBucket.UNDER_15_MIN),
            settings(enabled = true),
            emptyList(),
        ))

        assertEquals(
            listOf(
                RecommendationOptionCode.SNOOZE_5,
                RecommendationOptionCode.SNOOZE_10,
                RecommendationOptionCode.SNOOZE_15,
            ),
            decision.options.map { it.optionCode },
        )
    }

    @Test
    fun manualPreferenceWinsWithoutPretendingHistoryIsEnough() {
        val decision = requireNotNull(engine.recommend(
            context(),
            settings(enabled = true).copy(preferredSnoozeMinutes = 60),
            observations(3, RecommendationOptionCode.SNOOZE_5),
        ))

        assertTrue(decision.fallback)
        assertEquals(RecommendationOptionCode.SNOOZE_60, decision.options.first().optionCode)
        assertEquals(RecommendationReason.MANUAL_PREFERENCE, decision.options.first().reasonCode)
    }

    @Test
    fun twelveMatchingSamplesEnableStableLocalRanking() {
        val history = observations(8, RecommendationOptionCode.SNOOZE_30) +
            observations(4, RecommendationOptionCode.SNOOZE_10)

        val decision = requireNotNull(engine.recommend(context(), settings(enabled = true), history))

        assertFalse(decision.fallback)
        assertEquals(12, decision.sampleCount)
        assertEquals(RecommendationOptionCode.SNOOZE_30, decision.options[0].optionCode)
        assertEquals(RecommendationOptionCode.SNOOZE_10, decision.options[1].optionCode)
        assertTrue(decision.options.all { it.reasonCode == RecommendationReason.ENOUGH_LOCAL_HISTORY })
    }

    @Test
    fun samplesFromAnotherExplicitContextDoNotUnlockPersonalization() {
        val otherContext = observations(12, RecommendationOptionCode.SNOOZE_30).map {
            it.copy(capacityContext = RecommendationCapacityContext.REDUCED_EXPLICIT)
        }

        val decision = requireNotNull(engine.recommend(context(), settings(enabled = true), otherContext))

        assertTrue(decision.fallback)
        assertEquals(0, decision.sampleCount)
        assertEquals(RecommendationOptionCode.SNOOZE_15, decision.options.first().optionCode)
    }

    @Test
    fun quietHoursAndAvailabilityConstrainChannelBeforeRanking() {
        val decision = requireNotNull(engine.recommend(
            context().copy(
                purpose = RecommendationPurpose.ALERT_CHANNEL,
                availableChannels = setOf(
                    RecommendationChannel.VISUAL,
                    RecommendationChannel.PHONE_AUDIO,
                    RecommendationChannel.WATCH,
                ),
                quietHours = true,
            ),
            settings(enabled = true).copy(preferredChannel = RecommendationChannel.PHONE_AUDIO),
            emptyList(),
        ))

        assertEquals(
            listOf(RecommendationOptionCode.CHANNEL_VISUAL, RecommendationOptionCode.CHANNEL_WATCH),
            decision.options.map { it.optionCode },
        )
        assertEquals(RecommendationReason.DOMAIN_LIMIT_APPLIED, decision.options.first().reasonCode)
        assertTrue(decision.options.none { it.optionCode == RecommendationOptionCode.CHANNEL_AUDIO })
    }

    @Test
    fun watchIsNeverSuggestedWhenUnavailable() {
        val decision = requireNotNull(engine.recommend(
            context().copy(
                purpose = RecommendationPurpose.ALERT_CHANNEL,
                availableChannels = setOf(RecommendationChannel.VISUAL, RecommendationChannel.PHONE_AUDIO),
            ),
            settings(enabled = false).copy(preferredChannel = RecommendationChannel.WATCH),
            emptyList(),
        ))

        assertTrue(decision.options.none { it.optionCode == RecommendationOptionCode.CHANNEL_WATCH })
        assertEquals(RecommendationReason.DOMAIN_LIMIT_APPLIED, decision.options.first().reasonCode)
    }

    @Test
    fun protocolShortcutRequiresAvailableProtocolAndNeverRunsIt() {
        val unavailable = engine.recommend(
            context().copy(purpose = RecommendationPurpose.PROTOCOL_SHORTCUT),
            settings(enabled = true),
            emptyList(),
        )
        val available = requireNotNull(engine.recommend(
            context().copy(
                purpose = RecommendationPurpose.PROTOCOL_SHORTCUT,
                activeContext = RecommendationActiveContext.PROTOCOL,
                protocolAvailable = true,
            ),
            settings(enabled = true),
            emptyList(),
        ))

        assertNull(unavailable)
        assertEquals(listOf(RecommendationOptionCode.PROTOCOL_EXIT), available.options.map { it.optionCode })
        assertEquals(RecommendationReason.ACTIVE_PROTOCOL, available.options.single().reasonCode)
    }

    @Test
    fun sameInputsAlwaysProduceSameRanking() {
        val history = observations(12, RecommendationOptionCode.SNOOZE_30)

        val first = requireNotNull(engine.recommend(context(), settings(enabled = true), history))
        val second = requireNotNull(engine.recommend(context(), settings(enabled = true), history))

        assertEquals(first.options, second.options)
        assertEquals(RecommendationDecision.ENGINE_ID, first.engineId)
        assertEquals(RecommendationDecision.RULE_VERSION, first.ruleVersion)
    }

    @Test
    fun tenThousandObservationsRemainWithinInteractiveBudget() {
        val history = observations(10_000, RecommendationOptionCode.SNOOZE_30)
        lateinit var decision: RecommendationDecision

        val elapsedMillis = measureTimeMillis {
            decision = requireNotNull(engine.recommend(context(), settings(enabled = true), history))
        }

        assertEquals(10_000, decision.sampleCount)
        assertEquals(RecommendationOptionCode.SNOOZE_30, decision.options.first().optionCode)
        assertTrue("Ranking levou ${elapsedMillis}ms", elapsedMillis < 1_000)
    }

    private fun context() = RecommendationContext(
        purpose = RecommendationPurpose.SNOOZE_PRESET,
        generatedAt = Instant.parse("2026-09-02T15:00:00Z"),
        activeContext = RecommendationActiveContext.PROTOCOL,
        capacityContext = RecommendationCapacityContext.PARALLEL_EXPLICIT,
        alertKind = RecommendationAlertKind.TASK,
        deadlineBucket = RecommendationDeadlineBucket.TODAY,
    )

    private fun settings(enabled: Boolean) = RecommendationSettings(
        personalizationEnabled = enabled,
        retentionDays = 90,
        capacityContext = RecommendationCapacityContext.PARALLEL_EXPLICIT,
        preferredSnoozeMinutes = null,
        preferredChannel = null,
    )

    private fun observations(count: Int, option: RecommendationOptionCode) = List(count) {
        RecommendationObservation(
            eventType = RecommendationEventType.RECOMMENDATION_ACCEPTED,
            localHour = 11,
            dayOfWeek = 3,
            activeContext = RecommendationActiveContext.PROTOCOL,
            capacityContext = RecommendationCapacityContext.PARALLEL_EXPLICIT,
            alertKind = RecommendationAlertKind.TASK,
            optionCode = option,
        )
    }

    private companion object {
        const val DECISION_ID = "e0000000-0000-4000-8000-000000000001"
    }
}
