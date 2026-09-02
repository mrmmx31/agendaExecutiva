package com.pessoal.agenda.wear.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.contract.WearAlertState
import com.pessoal.agenda.wear.contract.WearAlertStatus
import com.pessoal.agenda.wear.contract.WearCriticality
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearAlertStoreTest {
    private lateinit var database: WearDatabase
    private lateinit var store: WearAlertStore
    private val now = Instant.parse("2026-09-02T12:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WearDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = WearAlertStore(
            database = database,
            deviceIdProvider = { WEAR_DEVICE_ID },
            operationIdProvider = { OPERATION_ID },
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun ingestExposesPendingAlertAndRejectsOldRevision() = runBlocking {
        assertEquals(WearIngestResult.INSERTED, store.ingest(state(2)))
        assertEquals(WearIngestResult.STALE, store.ingest(state(1).copy(text = "Versão antiga")))

        val visible = requireNotNull(store.observeVisibleAlert().first())
        assertEquals(ALERT_ID, visible.alertId)
        assertEquals("Separar documentos", visible.text)
        assertEquals(listOf(10, 30, 60), visible.snoozeOptionsMinutes)
    }

    @Test
    fun completePersistsOutboxBeforeLocalConfirmation() = runBlocking {
        store.ingest(state(1))

        val action = store.recordAction(ALERT_ID, WearActionType.COMPLETE)

        assertEquals(OPERATION_ID, action.operationId)
        assertEquals(1, database.wear().actionCount())
        val visible = requireNotNull(store.observeVisibleAlert().first())
        assertEquals(WearAlertStatus.COMPLETED, visible.status)
        assertEquals(WearActionType.COMPLETE, visible.feedback?.action)
    }

    @Test
    fun snoozeOnlyAcceptsOptionsReceivedFromPhone() = runBlocking {
        store.ingest(state(1))

        assertTrue(runCatching { store.recordAction(ALERT_ID, WearActionType.SNOOZE, 15) }.isFailure)
        assertEquals(0, database.wear().actionCount())

        val action = store.recordAction(ALERT_ID, WearActionType.SNOOZE, 30)
        assertEquals("2026-09-02T12:30:00Z", action.snoozeUntil)
        assertEquals(30, store.observeVisibleAlert().first()?.feedback?.snoozeMinutes)
    }

    @Test
    fun exactAcknowledgementClearsOutboxAndKeepsBriefFeedback() = runBlocking {
        store.ingest(state(1))
        store.recordAction(ALERT_ID, WearActionType.COMPLETE)

        assertEquals(
            WearIngestResult.UPDATED,
            store.ingest(state(2).copy(
                status = WearAlertStatus.COMPLETED,
                acknowledgedOperationId = OPERATION_ID,
            )),
        )

        assertEquals(0, database.wear().actionCount())
        assertNotNull(store.observeVisibleAlert().first()?.feedback)
        store.dismissFeedback(ALERT_ID)
        assertNull(store.observeVisibleAlert().first())
        assertEquals(0, database.wear().alertCount())
    }

    @Test
    fun staleAcknowledgementCannotConsumeNewerLocalAction() = runBlocking {
        store.ingest(state(2))
        store.recordAction(ALERT_ID, WearActionType.COMPLETE)

        assertEquals(
            WearIngestResult.STALE,
            store.ingest(state(1).copy(
                status = WearAlertStatus.COMPLETED,
                acknowledgedOperationId = OPERATION_ID,
            )),
        )

        assertEquals(1, database.wear().actionCount())
        assertEquals(OPERATION_ID, database.wear().actionsForSync().single().operationId)
    }

    @Test
    fun remoteDeletionCannotDiscardUnacknowledgedAction() = runBlocking {
        store.ingest(state(1))
        store.recordAction(ALERT_ID, WearActionType.COMPLETE)

        assertFalse(store.removeRemoteState(ALERT_ID))
        assertEquals(1, database.wear().alertCount())
        assertEquals(1, database.wear().actionCount())
    }

    private fun state(revision: Long) = WearAlertState(
        contractVersion = 1,
        alertId = ALERT_ID,
        revision = revision,
        text = "Separar documentos",
        reason = "Protocolo de saída",
        sourceDeviceId = PHONE_DEVICE_ID,
        scheduledAt = "2026-09-02T11:55:00Z",
        validUntil = "2026-09-02T18:00:00Z",
        updatedAt = "2026-09-02T11:55:00Z",
        criticality = WearCriticality.ROUTINE,
        actions = WearAlertState.REQUIRED_ACTIONS,
        snoozeOptionsMinutes = listOf(10, 30, 60),
        status = WearAlertStatus.PENDING,
        acknowledgedOperationId = null,
    )

    private companion object {
        const val ALERT_ID = "80000000-0000-4000-8000-000000000001"
        const val PHONE_DEVICE_ID = "80000000-0000-4000-8000-000000000002"
        const val WEAR_DEVICE_ID = "80000000-0000-4000-8000-000000000003"
        const val OPERATION_ID = "80000000-0000-4000-8000-000000000004"
    }
}
