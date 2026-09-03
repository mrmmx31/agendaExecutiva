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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecommendationStoreTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: RecommendationStore
    private var nextId = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RecommendationStore(
            database = database,
            clock = CLOCK,
            zoneId = ZoneId.of("America/Manaus"),
            newId = { IDS[nextId++] },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun installationStartsDisabledAndCollectsNothing() = runBlocking {
        val settings = store.ensureSettings()

        assertFalse(settings.personalizationEnabled)
        assertEquals(90, settings.retentionDays)
        assertNull(store.recordEvent(event(Instant.parse("2026-09-02T15:00:00Z"))))
        assertFalse(store.recordDecision(decision(IDS[4], Instant.parse("2026-09-02T15:00:00Z"))))
        assertEquals(0, database.offline().recommendationEventCount())
        assertEquals(0, database.offline().recommendationDecisionCount())
        assertTrue(database.offline().operationsForSync().isEmpty())
    }

    @Test
    fun enabledCollectionStoresOnlyCategoricalAndDerivedValues() = runBlocking {
        enable()
        val id = requireNotNull(store.recordEvent(event(Instant.parse("2026-09-02T15:00:00Z"))))
        val row = requireNotNull(database.offline().recommendationEvent(id))

        assertEquals("ALERT_SNOOZED", row.eventType)
        assertEquals(11, row.localHour)
        assertEquals(3, row.dayOfWeek)
        assertEquals("PROTOCOL", row.activeContext)
        assertEquals("PARALLEL_EXPLICIT", row.capacityContext)
        assertEquals("SNOOZE_15", row.optionCode)
        assertNull(row.correctedAt)
        assertTrue(database.offline().operationsForSync().isEmpty())

        assertTrue(store.recordDecision(decision(IDS[4], Instant.parse("2026-09-02T15:00:00Z"))))
        assertTrue(store.decisions().single().optionsJson.contains("\"option_code\":\"SNOOZE_15\""))
        assertTrue(store.decisions().single().optionsJson.contains("\"reason_code\":\"CAUTIOUS_DEFAULT\""))
    }

    @Test
    fun correctionReplacesCategoricalValuesWithoutDuplicatingEvent() = runBlocking {
        enable()
        val id = requireNotNull(store.recordEvent(event(Instant.parse("2026-09-02T15:00:00Z"))))

        store.correctEvent(
            id,
            event(Instant.parse("2026-09-02T15:01:00Z")).copy(
                activeContext = RecommendationActiveContext.NONE,
                capacityContext = RecommendationCapacityContext.STANDARD,
                snoozeMinutes = 30,
                optionCode = RecommendationOptionCode.SNOOZE_30,
            ),
        )

        val corrected = requireNotNull(database.offline().recommendationEvent(id))
        assertEquals(1, database.offline().recommendationEventCount())
        assertEquals("NONE", corrected.activeContext)
        assertEquals(30, corrected.snoozeMinutes)
        assertEquals(CLOCK.instant().toString(), corrected.correctedAt)
    }

    @Test
    fun retentionAndExplicitClearCoverEventsAndDecisions() = runBlocking {
        enable()
        store.recordEvent(event(Instant.parse("2026-05-01T12:00:00Z")))
        store.recordEvent(event(Instant.parse("2026-09-01T12:00:00Z")))
        assertTrue(store.recordDecision(decision(IDS[4], Instant.parse("2026-05-01T12:00:00Z"))))
        assertTrue(store.recordDecision(decision(IDS[5], Instant.parse("2026-09-01T12:00:00Z"))))

        assertEquals(RetentionResult(1, 1), store.enforceRetention())
        assertEquals(1, database.offline().recommendationEventCount())
        assertEquals(1, database.offline().recommendationDecisionCount())
        assertEquals(RetentionResult(1, 1), store.clearHistory())
        assertEquals(0, database.offline().recommendationEventCount())
        assertEquals(0, database.offline().recommendationDecisionCount())
        assertTrue(database.offline().operationsForSync().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDecisionRanksAreRejected() = runBlocking {
        enable()
        store.recordDecision(
            decision(IDS[4], Instant.parse("2026-09-02T15:00:00Z")).copy(
                options = listOf(
                    RecommendationOption(
                        RecommendationOptionCode.SNOOZE_15,
                        rank = 2,
                        RecommendationReason.CAUTIOUS_DEFAULT,
                    ),
                ),
            ),
        )
        Unit
    }

    private suspend fun enable() {
        store.ensureSettings()
        store.saveSettings(
            RecommendationSettings(
                personalizationEnabled = true,
                retentionDays = 90,
                capacityContext = RecommendationCapacityContext.PARALLEL_EXPLICIT,
                preferredSnoozeMinutes = 15,
                preferredChannel = RecommendationChannel.VISUAL,
            ),
        )
    }

    private fun event(at: Instant) = RecommendationEventInput(
        eventType = RecommendationEventType.ALERT_SNOOZED,
        occurredAt = at,
        sourceDevice = RecommendationSourceDevice.PHONE,
        activeContext = RecommendationActiveContext.PROTOCOL,
        capacityContext = RecommendationCapacityContext.PARALLEL_EXPLICIT,
        alertKind = RecommendationAlertKind.TASK,
        deadlineBucket = RecommendationDeadlineBucket.TODAY,
        channel = RecommendationChannel.VISUAL,
        responseLatencySeconds = 42,
        snoozeMinutes = 15,
        recommendationId = IDS[3],
        optionCode = RecommendationOptionCode.SNOOZE_15,
    )

    private fun decision(id: String, at: Instant) = RecommendationDecision(
        id = id,
        generatedAt = at,
        purpose = RecommendationPurpose.SNOOZE_PRESET,
        sampleCount = 0,
        minimumSamples = 12,
        fallback = true,
        options = listOf(
            RecommendationOption(
                RecommendationOptionCode.SNOOZE_15,
                rank = 1,
                RecommendationReason.CAUTIOUS_DEFAULT,
            ),
        ),
    )

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-02T16:00:00Z"), ZoneOffset.UTC)
        val IDS = (1..8).map { "d0000000-0000-4000-8000-${it.toString().padStart(12, '0')}" }
    }
}
