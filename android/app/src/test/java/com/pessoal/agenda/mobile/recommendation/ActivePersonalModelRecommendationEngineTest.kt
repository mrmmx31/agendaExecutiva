package com.pessoal.agenda.mobile.recommendation

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ActivePersonalModelRecommendationEngineTest {
    private val primaryDecision = RecommendationDecision(
        id = "e3000000-0000-4000-8000-000000000001",
        generatedAt = NOW,
        purpose = RecommendationPurpose.SNOOZE_PRESET,
        sampleCount = 12,
        minimumSamples = 12,
        fallback = false,
        options = listOf(
            RecommendationOption(RecommendationOptionCode.SNOOZE_5, 1, RecommendationReason.ENOUGH_LOCAL_HISTORY),
            RecommendationOption(RecommendationOptionCode.SNOOZE_10, 2, RecommendationReason.ENOUGH_LOCAL_HISTORY),
            RecommendationOption(RecommendationOptionCode.SNOOZE_15, 3, RecommendationReason.ENOUGH_LOCAL_HISTORY),
        ),
    )
    private val settings = RecommendationSettings(true, 90, RecommendationCapacityContext.STANDARD, null, null)
    private val context = RecommendationContext(
        purpose = RecommendationPurpose.SNOOZE_PRESET,
        generatedAt = NOW,
        alertKind = RecommendationAlertKind.TASK,
        deadlineBucket = RecommendationDeadlineBucket.UNDER_15_MIN,
    )

    @Test
    fun activeModelOnlyReordersOptionsAlreadyAllowedByRules() {
        val engine = ActivePersonalModelRecommendationEngine(
            FixedEngine(primaryDecision),
            stored(PersonalModelStatus.ACTIVE),
            ZoneId.of("UTC"),
        )

        val decision = requireNotNull(engine.recommend(context, settings, history(12)))

        assertEquals(RecommendationOptionCode.SNOOZE_10, decision.options.first().optionCode)
        assertEquals(primaryDecision.options.map { it.optionCode }.toSet(), decision.options.map { it.optionCode }.toSet())
        assertEquals(ActivePersonalModelRecommendationEngine.MODEL_ENGINE_ID, decision.engineId)
        assertEquals("local-v1", decision.ruleVersion)
        assertEquals(RecommendationReason.PERSONAL_MODEL, decision.options.first().reasonCode)
    }

    @Test
    fun manualPreferenceAndOptOutAlwaysKeepPrimaryDecision() {
        val engine = ActivePersonalModelRecommendationEngine(FixedEngine(primaryDecision), stored(PersonalModelStatus.ACTIVE))

        assertSame(
            primaryDecision,
            engine.recommend(context, settings.copy(preferredSnoozeMinutes = 15), history(12)),
        )
        assertSame(primaryDecision, engine.recommend(context, settings.copy(personalizationEnabled = false), history(12)))
    }

    @Test
    fun insufficientContextOrNonActiveArtifactKeepsPrimaryDecision() {
        val active = ActivePersonalModelRecommendationEngine(FixedEngine(primaryDecision), stored(PersonalModelStatus.ACTIVE))
        val shadow = ActivePersonalModelRecommendationEngine(FixedEngine(primaryDecision), stored(PersonalModelStatus.SHADOW))

        assertSame(primaryDecision, active.recommend(context, settings, history(11)))
        assertSame(primaryDecision, shadow.recommend(context, settings, history(12)))
    }

    private fun stored(status: PersonalModelStatus): StoredPersonalModel = StoredPersonalModel(
        modelVersion = "local-v1",
        status = status,
        model = model(),
        evaluation = PersonalModelEvaluation(80, 30, 0.8, 0.6, true),
        artifactSha256 = "a".repeat(64),
        artifactSizeBytes = 1_024,
    )

    private fun model(): AuditableLinearModel {
        val features = listOf(AuditableLinearModel.INTERCEPT) + PersonalFeatureVocabulary.ALL
        val weights = AuditableLinearModel.SNOOZE_OPTIONS.associateWith { option ->
            features.associateWith { 0.0 }.toMutableMap().apply {
                this[AuditableLinearModel.INTERCEPT] = when (option) {
                    RecommendationOptionCode.SNOOZE_60 -> 100.0
                    RecommendationOptionCode.SNOOZE_10 -> 10.0
                    else -> 0.0
                }
            }.toMap()
        }
        return AuditableLinearModel(PersonalFeatureVocabulary.ALL, weights)
    }

    private fun history(count: Int) = List(count) { index ->
        RecommendationObservation(
            eventType = RecommendationEventType.ALERT_SNOOZED,
            localHour = 12,
            dayOfWeek = 4,
            activeContext = RecommendationActiveContext.NONE,
            capacityContext = RecommendationCapacityContext.STANDARD,
            alertKind = RecommendationAlertKind.TASK,
            optionCode = RecommendationOptionCode.SNOOZE_15,
            deadlineBucket = RecommendationDeadlineBucket.UNDER_15_MIN,
            occurredAt = NOW.minusSeconds(count.toLong() - index),
        )
    }

    private class FixedEngine(private val decision: RecommendationDecision) : RecommendationEngine {
        override fun recommend(
            context: RecommendationContext,
            settings: RecommendationSettings,
            observations: List<RecommendationObservation>,
        ) = decision
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-03T12:00:00Z")
    }
}
