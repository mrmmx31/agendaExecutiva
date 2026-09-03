package com.pessoal.agenda.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.data.WearAlertStore
import com.pessoal.agenda.wear.data.WearDatabase
import com.pessoal.agenda.wear.data.WearDeviceIdentity
import com.pessoal.agenda.wear.data.WearProtocolStore
import com.pessoal.agenda.wear.sync.WearInitialStateReader
import com.pessoal.agenda.wear.sync.WearOutboxScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearPairedGateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = WearDatabase.get(context)
    private val identity = WearDeviceIdentity(context)
    private val store = WearAlertStore(database, { identity.deviceId })
    private val protocolStore = WearProtocolStore(database, { identity.deviceId })

    @Before
    fun requireExplicitPairedGate() {
        assumeTrue(
            "Gate pareado executado somente pelo orquestrador de dois AVDs.",
            InstrumentationRegistry.getArguments().getString("pairedGate") == "true",
        )
    }

    @Test
    fun pairedNodeIsReachable() {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 20, TimeUnit.SECONDS)
        assertTrue("Nenhum telefone pareado alcançável.", nodes.isNotEmpty())
    }

    @Test
    fun snoozeReceivedFixtureAndAwaitAcknowledgement() = runBlocking {
        val alertId = SNOOZE_ALERT_ID
        awaitAlert(alertId)
        store.recordAction(alertId, WearActionType.SNOOZE, 10)
        WearOutboxScheduler(context).enqueue()
        awaitAcknowledgement(alertId)
    }

    @Test
    fun completeReceivedFixtureAndAwaitAcknowledgement() = runBlocking {
        val alertId = COMPLETE_ALERT_ID
        awaitAlert(alertId)
        store.recordAction(alertId, WearActionType.COMPLETE)
        WearOutboxScheduler(context).enqueue()
        awaitAcknowledgement(alertId)
    }

    @Test
    fun completeOfflineFixtureAndKeepDurableOutbox() = runBlocking {
        val alertId = OFFLINE_ALERT_ID
        awaitAlert(alertId)
        val action = store.recordAction(alertId, WearActionType.COMPLETE)
        WearOutboxScheduler(context).enqueue()
        repeat(100) {
            if (database.wear().action(action.operationId)?.state == "STORED") return@repeat
            delay(100)
        }
        delay(1_000)
        assertEquals("STORED", database.wear().action(action.operationId)?.state)
    }

    @Test
    fun completeProtocolStepAndAwaitAcknowledgement() = runBlocking {
        val runId = awaitProtocolRun()
        protocolStore.recordCompletion(runId)
        WearOutboxScheduler(context).enqueue()
        repeat(150) {
            WearInitialStateReader(context).refresh(store, protocolStore)
            if (protocolStore.actionsForSync().isEmpty()) {
                val current = protocolStore.observeCurrentStep().first()
                if (current?.stepPosition == 2) return@runBlocking
            }
            delay(200)
        }
        error("Etapa seguinte não chegou ao relógio em 30 segundos.")
    }

    private suspend fun awaitProtocolRun(): String {
        repeat(100) {
            WearInitialStateReader(context).refresh(store, protocolStore)
            protocolStore.observeCurrentStep().first()?.runId?.let { return it }
            delay(200)
        }
        error("Passo do protocolo não chegou ao relógio em 20 segundos.")
    }

    private suspend fun awaitAlert(alertId: String) {
        repeat(100) {
            WearInitialStateReader(context).refresh(store)
            if (database.wear().alert(alertId) != null) return
            delay(200)
        }
        error("Estado do telefone não chegou ao relógio em 20 segundos.")
    }

    private suspend fun awaitAcknowledgement(alertId: String) {
        repeat(150) {
            if (database.wear().actionsForSync().none { it.alertId == alertId }) return
            delay(200)
        }
        assertEquals(0, database.wear().actionsForSync().count { it.alertId == alertId })
    }

    private companion object {
        const val SNOOZE_ALERT_ID = "91000000-0000-4000-8000-000000000001"
        const val COMPLETE_ALERT_ID = "91000000-0000-4000-8000-000000000002"
        const val OFFLINE_ALERT_ID = "91000000-0000-4000-8000-000000000005"
    }
}
