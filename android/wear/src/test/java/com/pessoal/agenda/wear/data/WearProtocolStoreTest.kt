package com.pessoal.agenda.wear.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.wear.contract.WearProtocolStatus
import com.pessoal.agenda.wear.contract.WearProtocolStepState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearProtocolStoreTest {
    private lateinit var database: WearDatabase
    private lateinit var store: WearProtocolStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), WearDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = WearProtocolStore(
            database,
            { WATCH_ID },
            { OPERATION_ID },
            Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After fun tearDown() = database.close()

    @Test
    fun completionIsDurableAndExactAckAdvancesStep() = runBlocking {
        assertEquals(WearIngestResult.INSERTED, store.ingest(state(1, FIRST_STEP, "Chaves", 1)))
        val action = store.recordCompletion(RUN_ID)
        assertEquals(OPERATION_ID, action.operationId)
        assertEquals(true, store.observeCurrentStep().first()?.feedback)
        assertEquals(1, store.actionsForSync().size)
        store.dismissFeedback(RUN_ID)
        assertEquals(true, store.observeCurrentStep().first()?.actionPending)
        assertEquals(true, runCatching { store.recordCompletion(RUN_ID) }.isFailure)

        assertEquals(
            WearIngestResult.UPDATED,
            store.ingest(state(2, SECOND_STEP, "Carteira", 2).copy(
                acknowledgedOperationId = OPERATION_ID,
            )),
        )
        assertEquals(0, store.actionsForSync().size)
        val visible = requireNotNull(store.observeCurrentStep().first())
        assertEquals("Carteira", visible.stepLabel)
        assertNotNull(visible)
    }

    @Test
    fun staleStateAndRemoteDeletionCannotLosePendingAction() = runBlocking {
        store.ingest(state(2, FIRST_STEP, "Chaves", 1))
        store.recordCompletion(RUN_ID)
        assertEquals(WearIngestResult.STALE, store.ingest(state(1, SECOND_STEP, "Antiga", 2)))
        assertFalse(store.removeRemoteState(RUN_ID))
        assertEquals(1, store.actionsForSync().size)
    }

    private fun state(revision: Long, stepId: String, label: String, position: Int) =
        WearProtocolStepState(
            contractVersion = 1,
            runId = RUN_ID,
            protocolId = PROTOCOL_ID,
            revision = revision,
            protocolTitle = "Saída de teste",
            stepId = stepId,
            stepLabel = label,
            stepPosition = position,
            stepCount = 2,
            updatedAt = "2026-09-02T11:55:00Z",
            status = WearProtocolStatus.ACTIVE,
            acknowledgedOperationId = null,
        )

    private companion object {
        const val RUN_ID = "90000000-0000-4000-8000-000000000001"
        const val PROTOCOL_ID = "90000000-0000-4000-8000-000000000002"
        const val WATCH_ID = "90000000-0000-4000-8000-000000000003"
        const val OPERATION_ID = "90000000-0000-4000-8000-000000000004"
        const val FIRST_STEP = "90000000-0000-4000-8000-000000000005"
        const val SECOND_STEP = "90000000-0000-4000-8000-000000000006"
    }
}
