package com.pessoal.agenda.mobile.recommendation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonalSnoozeOptionRankerTest {
    private lateinit var database: MobileDatabase
    private lateinit var recommendationStore: RecommendationStore
    private lateinit var artifactStore: PersonalModelArtifactStore
    private lateinit var ranker: PersonalSnoozeOptionRanker

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        recommendationStore = RecommendationStore(database, CLOCK, ZoneId.of("UTC"))
        artifactStore = PersonalModelArtifactStore(database, CLOCK)
        ranker = PersonalSnoozeOptionRanker(database, CLOCK, ZoneId.of("UTC"))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun activeModelReordersWearDefaultsWithoutAddingAnOption() = runBlocking {
        enableAndRecordContext()
        val training = List(60) { sample(RecommendationOptionCode.SNOOZE_30) }
        val model = AuditableLinearTrainer().train(training)
        artifactStore.stage(
            "local-v1",
            model,
            PersonalModelEvaluation(80, 30, 0.8, 0.6, true),
        )
        artifactStore.activate("local-v1")

        val ranked = ranker.rank(listOf(15, 30, 10), RecommendationAlertKind.TASK, NOW.plusSeconds(6 * 3_600))

        assertEquals(30, ranked.first())
        assertEquals(setOf(15, 30, 10), ranked.toSet())
    }

    @Test
    fun optOutKeepsConfiguredWearOrderEvenWithActiveArtifact() = runBlocking {
        enableAndRecordContext()
        val model = AuditableLinearTrainer().train(List(60) { sample(RecommendationOptionCode.SNOOZE_30) })
        artifactStore.stage("local-v1", model, PersonalModelEvaluation(80, 30, 0.8, 0.6, true))
        artifactStore.activate("local-v1")
        recommendationStore.saveSettings(settings(enabled = false))

        assertEquals(
            listOf(15, 30, 10),
            ranker.rank(listOf(15, 30, 10), RecommendationAlertKind.TASK, NOW.plusSeconds(6 * 3_600)),
        )
    }

    private suspend fun enableAndRecordContext() {
        recommendationStore.ensureSettings()
        recommendationStore.saveSettings(settings(enabled = true))
        repeat(12) { index ->
            recommendationStore.recordEvent(
                RecommendationEventInput(
                    eventType = RecommendationEventType.ALERT_SNOOZED,
                    occurredAt = NOW.minusSeconds(index.toLong()),
                    sourceDevice = RecommendationSourceDevice.PHONE,
                    activeContext = RecommendationActiveContext.NONE,
                    capacityContext = RecommendationCapacityContext.STANDARD,
                    alertKind = RecommendationAlertKind.TASK,
                    deadlineBucket = RecommendationDeadlineBucket.TODAY,
                    snoozeMinutes = 30,
                    optionCode = RecommendationOptionCode.SNOOZE_30,
                ),
            )
        }
    }

    private fun settings(enabled: Boolean) = RecommendationSettings(
        personalizationEnabled = enabled,
        retentionDays = 90,
        capacityContext = RecommendationCapacityContext.STANDARD,
        preferredSnoozeMinutes = null,
        preferredChannel = null,
    )

    private fun sample(option: RecommendationOptionCode) = PersonalRankingSample(
        dayPart = PersonalDayPart.AFTERNOON,
        dayGroup = PersonalDayGroup.WEEKDAY,
        sourceDevice = RecommendationSourceDevice.PHONE,
        activeContext = RecommendationActiveContext.NONE,
        capacityContext = RecommendationCapacityContext.STANDARD,
        alertKind = RecommendationAlertKind.TASK,
        deadlineBucket = RecommendationDeadlineBucket.TODAY,
        chosenOption = option,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-03T15:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
