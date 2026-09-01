package com.pessoal.agenda.mobile.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {
    private lateinit var database: MobileDatabase
    private lateinit var offline: OfflineRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        val ids = ArrayDeque(
            (1..10).map { "10000000-0000-4000-8000-${it.toString().padStart(12, '0')}" },
        )
        offline = OfflineRepository(
            database,
            clock,
            ZoneId.of("America/Manaus"),
            newId = { ids.removeFirst() },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun appliedCaptureIsAcknowledgedAndSnapshotIsPersisted() = runBlocking {
        offline.createCapture("Captura para sincronizar")
        val transport = FakeTransport(
            result = { batch ->
                assertEquals(1, batch.contractVersion)
                assertTrue(Json.encodeToString(batch).contains("\"contract_version\":1"))
                response(batch, "APPLIED")
            },
            pages = ArrayDeque(listOf(snapshot(cursor = 7))),
        )

        val summary = SyncRepository(database, transport, clock).syncOnce()

        assertEquals(1, summary.sent)
        assertEquals(1, summary.applied)
        assertEquals("APPLIED", offline.operations.first().single().status)
        assertNotNull(offline.captures.first().single().acknowledgedAt)
        assertEquals("Tarefa sincronizada fictícia", offline.tasks.first().single().title)
        assertEquals(7, summary.serverCursor)
    }

    @Test
    fun transportFailureReturnsInFlightOperationToRetryable() = runBlocking {
        offline.createCapture("Captura com falha temporária")
        val transport = object : SyncTransport {
            override suspend fun push(batch: SyncBatch): SyncBatchResponse =
                throw SyncTransportException("Falha fictícia")
            override suspend fun snapshot(pageToken: String?): SnapshotPage = error("não deve chamar")
        }

        runCatching { SyncRepository(database, transport, clock).syncOnce() }

        val operation = offline.operations.first().single()
        assertEquals("RETRYABLE", operation.status)
        assertEquals("TEMPORARY_FAILURE", operation.errorCode)
        assertEquals(1, operation.attemptCount)
    }

    @Test
    fun conflictResultKeepsBothVersionsForReview() = runBlocking {
        offline.createCapture("Versão local fictícia")
        val operation = offline.operations.first().single()
        val conflictId = "10000000-0000-4000-8000-000000000099"
        val conflict = ConflictPayload(
            conflictId,
            operation.operationId,
            operation.entityType,
            operation.entityId,
            null,
            2,
            "TEXT_DIVERGED",
            buildJsonObject { put("text", "Versão local fictícia") },
            buildJsonObject { put("text", "Versão desktop fictícia") },
            "2026-09-01T12:00:00Z",
        )
        val transport = FakeTransport(
            result = { batch ->
                response(batch, "CONFLICT", conflictId = conflictId, conflicts = listOf(conflict))
            },
            pages = ArrayDeque(listOf(snapshot(cursor = 2))),
        )

        SyncRepository(database, transport, clock).syncOnce()

        assertEquals("CONFLICT", offline.operations.first().single().status)
        val stored = database.offline().openConflicts().single()
        assertEquals("TEXT_DIVERGED", stored.reason)
        assertTrue(stored.localValueJson.contains("Versão local fictícia"))
        assertEquals(2, stored.serverRevision)
    }

    private fun response(
        batch: SyncBatch,
        status: String,
        conflictId: String? = null,
        conflicts: List<ConflictPayload> = emptyList(),
    ) = SyncBatchResponse(
        1,
        batch.operations.maxOfOrNull { it.sequence } ?: 0,
        0,
        batch.operations.map {
            SyncResult(it.operationId, status, if (status == "CONFLICT") "STATE_CONFLICT" else null,
                if (status == "CONFLICT") 2 else 1, conflictId)
        },
        conflicts,
    )

    private fun snapshot(cursor: Long) = SnapshotPage(
        snapshotId = "10000000-0000-4000-8000-000000000080",
        serverCursor = cursor,
        page = 1,
        hasMore = false,
        nextPageToken = null,
        tasks = listOf(SnapshotTask(
            "10000000-0000-4000-8000-000000000081",
            "Tarefa sincronizada fictícia",
            "PENDING",
            1,
            "2026-09-01T12:00:00Z",
            false,
        )),
        protocols = emptyList(),
    )
}

private class FakeTransport(
    private val result: (SyncBatch) -> SyncBatchResponse,
    private val pages: ArrayDeque<SnapshotPage>,
) : SyncTransport {
    override suspend fun push(batch: SyncBatch): SyncBatchResponse = result(batch)
    override suspend fun snapshot(pageToken: String?): SnapshotPage = pages.removeFirst()
}
