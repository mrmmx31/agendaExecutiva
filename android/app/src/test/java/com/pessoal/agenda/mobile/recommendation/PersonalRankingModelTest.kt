package com.pessoal.agenda.mobile.recommendation

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class PersonalRankingModelTest {
    @Test
    fun sharedSyntheticFixtureDecodesWithOnlyClosedCategoricalFeatures() {
        val dataset = fixture()

        assertEquals(1, dataset.contractVersion)
        assertEquals(PersonalDatasetSource.SYNTHETIC_FIXTURE, dataset.source)
        assertEquals(RecommendationPurpose.SNOOZE_PRESET, dataset.purpose)
        assertEquals(12, dataset.validatedSamples().size)
        assertTrue(dataset.samples.all { it.featureNames().size == 7 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun datasetRejectsUnsupportedContractVersion() {
        fixture().copy(contractVersion = 2).validatedSamples()
    }

    @Test(expected = IllegalArgumentException::class)
    fun trainerRejectsHistoryBelowMinimum() {
        AuditableLinearTrainer().train(fixture().samples)
    }

    @Test
    fun trainingAndRankingAreDeterministicAndExplainable() {
        val samples = repeatedFixture(8)
        val trainer = AuditableLinearTrainer()

        val first = trainer.train(samples)
        val second = trainer.train(samples)
        val sample = samples.first()

        assertEquals(first, second)
        assertEquals(first.rank(sample), second.rank(sample))
        assertEquals(8, first.contributions(sample, first.rank(sample).first()).size)
        assertTrue(first.weights.values.all { it.keys == first.weights.values.first().keys })
    }

    @Test
    fun offlineEvaluationUsesNewestPartitionAndReportsBaselineSeparately() {
        val evaluation = OfflinePersonalModelEvaluator().evaluate(repeatedFixture(15))

        assertEquals(144, evaluation.trainingSampleCount)
        assertEquals(36, evaluation.evaluationSampleCount)
        assertTrue(evaluation.modelTop1Accuracy in 0.0..1.0)
        assertTrue(evaluation.baselineTop1Accuracy in 0.0..1.0)
        assertEquals(
            evaluation.modelTop1Accuracy - evaluation.baselineTop1Accuracy >=
                OfflinePersonalModelEvaluator.MINIMUM_ABSOLUTE_GAIN,
            evaluation.eligibleForPromotion,
        )
    }

    @Test
    fun insufficientEvaluationCanNeverPromote() {
        val evaluation = OfflinePersonalModelEvaluator().evaluate(repeatedFixture(7))

        assertEquals(17, evaluation.evaluationSampleCount)
        assertFalse(evaluation.eligibleForPromotion)
    }

    @Test
    fun rulesBaselineMatchesExistingCautiousFirstOption() {
        val engine = DeterministicRecommendationEngine(newId = { DECISION_ID })
        val settings = RecommendationSettings(false, 90, RecommendationCapacityContext.STANDARD, null, null)
        fixture().samples.forEach { sample ->
            val context = RecommendationContext(
                purpose = RecommendationPurpose.SNOOZE_PRESET,
                generatedAt = java.time.Instant.parse("2026-09-02T15:00:00Z"),
                capacityContext = sample.capacityContext,
                deadlineBucket = sample.deadlineBucket,
            )
            val decision = requireNotNull(engine.recommend(context, settings, emptyList()))
            assertEquals(RulesV1SnoozeBaseline.predict(sample), decision.options.first().optionCode)
        }
    }

    @Test
    fun modelArtifactTrainingAndInferenceStayWithinLocalBudget() {
        val samples = List(167) { fixture().samples }.flatten()
            .take(ShadowingRecommendationEngine.MAXIMUM_TRAINING_SAMPLES)
        lateinit var model: AuditableLinearModel
        val trainingMillis = measureTimeMillis { model = AuditableLinearTrainer().train(samples) }
        val artifactBytes = Json.encodeToString(model.toArtifactPayload()).toByteArray().size
        val inferenceMillis = measureTimeMillis {
            repeat(10_000) { model.rank(samples.first()) }
        }

        assertEquals(2_000, samples.size)
        println(
            "P2_09_BENCHMARK training_2000_ms=$trainingMillis " +
                "inference_10000_ms=$inferenceMillis artifact_bytes=$artifactBytes",
        )
        assertTrue("Treino levou ${trainingMillis}ms", trainingMillis < 2_000)
        assertTrue("Inferência levou ${inferenceMillis}ms", inferenceMillis < 1_000)
        assertTrue("Artefato possui $artifactBytes bytes", artifactBytes < 64 * 1_024)
    }

    private fun repeatedFixture(times: Int) = List(times) { fixture().samples }.flatten()

    private fun fixture(): PersonalRankingDataset {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/v1/personal-ranking-dataset.valid.json",
        ))
        return stream.bufferedReader().use {
            Json.decodeFromString<PersonalRankingDataset>(it.readText())
        }
    }

    private companion object {
        const val DECISION_ID = "e1000000-0000-4000-8000-000000000001"
    }
}
