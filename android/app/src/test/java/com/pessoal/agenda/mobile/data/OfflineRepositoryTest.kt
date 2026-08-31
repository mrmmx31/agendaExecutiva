package com.pessoal.agenda.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineRepositoryTest {
    private lateinit var database: MobileDatabase
    private lateinit var ids: ArrayDeque<String>
    private lateinit var repository: OfflineRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        ids = ArrayDeque(
            (1..20).map { "10000000-0000-4000-8000-${it.toString().padStart(12, '0')}" },
        )
        repository = OfflineRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC),
            zoneId = ZoneId.of("America/Manaus"),
            newId = { ids.removeFirst() },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun captureAndOperationArePersistedWithCanonicalHash() = runBlocking {
        repository.createCapture("  Lembrar de revisar o exemplo  ")

        val capture = repository.captures.first().single()
        val operation = repository.operations.first().single()
        assertEquals("Lembrar de revisar o exemplo", capture.text)
        assertEquals(capture.id, operation.entityId)
        assertEquals("CAPTURE_CREATED", operation.commandType)
        assertEquals(1, operation.sequence)
        assertEquals(OfflineRepository.sha256(operation.payloadJson), operation.payloadHash)
        assertEquals("America/Manaus", operation.timeZone)
        assertTrue(operation.payloadJson.contains(capture.id))
    }

    @Test
    fun fixturesAreDeterministicAndDoNotGenerateOperations() = runBlocking {
        repository.initializeFictitiousData()
        repository.initializeFictitiousData()

        assertEquals(2, repository.tasks.first().size)
        assertEquals(1, repository.protocols.first().size)
        assertTrue(repository.operations.first().isEmpty())
    }

    @Test
    fun completingProtocolStepIsIdempotentAndFinishesRun() = runBlocking {
        repository.initializeFictitiousData()
        val runId = repository.startProtocol(OfflineRepository.FIXTURE_PROTOCOL)
        val steps = repository.activeRunSteps.first { it.isNotEmpty() }

        assertTrue(repository.completeProtocolStep(runId, steps[0].stepId))
        assertFalse(repository.completeProtocolStep(runId, steps[0].stepId))
        steps.drop(1).forEach { assertTrue(repository.completeProtocolStep(runId, it.stepId)) }

        assertTrue(repository.activeRunSteps.first().isEmpty())
        val operations = repository.operations.first()
        assertEquals(5, operations.size)
        assertEquals((1L..5L).toList(), operations.sortedBy { it.sequence }.map { it.sequence })
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankCaptureIsRejectedBeforePersistence() {
        runBlocking { repository.createCapture("   ") }
    }
}
