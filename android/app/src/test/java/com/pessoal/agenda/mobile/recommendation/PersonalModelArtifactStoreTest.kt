package com.pessoal.agenda.mobile.recommendation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonalModelArtifactStoreTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: PersonalModelArtifactStore
    private lateinit var model: AuditableLinearModel

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = PersonalModelArtifactStore(database, CLOCK)
        model = AuditableLinearTrainer().train(trainingSamples())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun stagingPersistsCanonicalArtifactAndVerifiedHash() = runBlocking {
        val staged = store.stage("local-v1", model, eligibleEvaluation())
        val row = store.versions().single()

        assertEquals(PersonalModelStatus.SHADOW, staged.status)
        assertEquals(PersonalModelArtifactStore.sha256(row.artifactJson), row.artifactSha256)
        assertEquals(model.rank(trainingSamples().first()), staged.model.rank(trainingSamples().first()))
        assertTrue(database.offline().operationsForSync().isEmpty())
    }

    @Test
    fun activationAtomicallyRollsBackPreviousVersion() = runBlocking {
        store.stage("local-v1", model, eligibleEvaluation())
        store.activate("local-v1")
        store.stage("local-v2", model, eligibleEvaluation())

        val active = store.activate("local-v2")
        val versions = store.versions().associateBy { it.modelVersion }

        assertEquals("local-v2", active.modelVersion)
        assertEquals("ROLLED_BACK", versions.getValue("local-v1").status)
        assertEquals("ACTIVE", versions.getValue("local-v2").status)
    }

    @Test
    fun ineligibleCandidateCannotReplaceActiveModel() = runBlocking {
        store.stage("good", model, eligibleEvaluation())
        store.activate("good")
        store.stage("weak", model, eligibleEvaluation().copy(modelTop1Accuracy = 0.51))

        val failure = runCatching { store.activate("weak") }

        assertTrue(failure.isFailure)
        assertEquals("good", store.active()?.modelVersion)
    }

    @Test
    fun corruptedActiveArtifactRollsBackToRules() = runBlocking {
        store.stage("local-v1", model, eligibleEvaluation())
        store.activate("local-v1")
        database.openHelper.writableDatabase.execSQL(
            "UPDATE personal_model_artifacts SET artifactJson=artifactJson || ' ' WHERE modelVersion='local-v1'",
        )

        assertNull(store.active())
        assertEquals("ROLLED_BACK", store.versions().single().status)
    }

    @Test
    fun explicitRollbackLeavesNoActiveArtifact() = runBlocking {
        store.stage("local-v1", model, eligibleEvaluation())
        store.activate("local-v1")

        assertTrue(store.rollbackToRules())
        assertNull(store.active())
        assertFalse(store.rollbackToRules())
    }

    @Test
    fun shadowMetricsAreAggregatedWithoutContextOrHistory() = runBlocking {
        store.recordShadow(comparison(agrees = true))
        store.recordShadow(comparison(agrees = false))

        assertEquals(ShadowMetrics(2, 1), store.shadowMetrics())
        val row = requireNotNull(database.offline().personalModelShadowMetrics(PersonalModelArtifactStore.MODEL_ID))
        assertEquals("SNOOZE_15", row.lastRuleOption)
        assertEquals("SNOOZE_30", row.lastModelOption)
    }

    @Test
    fun clearingRecommendationHistoryInvalidatesDerivedState() = runBlocking {
        val recommendationStore = RecommendationStore(database, CLOCK)
        recommendationStore.ensureSettings()
        store.stage("local-v1", model, eligibleEvaluation())
        store.recordShadow(comparison(agrees = true))

        recommendationStore.clearHistory()

        assertTrue(store.versions().isEmpty())
        assertEquals(ShadowMetrics(0, 0), store.shadowMetrics())
    }

    private fun eligibleEvaluation() = PersonalModelEvaluation(
        trainingSampleCount = 80,
        evaluationSampleCount = 30,
        modelTop1Accuracy = 0.8,
        baselineTop1Accuracy = 0.6,
        eligibleForPromotion = true,
    )

    private fun comparison(agrees: Boolean) = ShadowComparison(
        trainingSampleCount = 80,
        ruleTopOption = RecommendationOptionCode.SNOOZE_15,
        modelTopOption = if (agrees) RecommendationOptionCode.SNOOZE_15 else RecommendationOptionCode.SNOOZE_30,
        agreesWithRule = agrees,
    )

    private fun trainingSamples() = List(60) { index ->
        PersonalRankingSample(
            dayPart = PersonalDayPart.MORNING,
            dayGroup = PersonalDayGroup.WEEKDAY,
            sourceDevice = RecommendationSourceDevice.PHONE,
            activeContext = RecommendationActiveContext.NONE,
            capacityContext = RecommendationCapacityContext.STANDARD,
            alertKind = RecommendationAlertKind.TASK,
            deadlineBucket = RecommendationDeadlineBucket.TODAY,
            chosenOption = if (index % 3 == 0) {
                RecommendationOptionCode.SNOOZE_30
            } else {
                RecommendationOptionCode.SNOOZE_15
            },
        )
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC)
    }
}
