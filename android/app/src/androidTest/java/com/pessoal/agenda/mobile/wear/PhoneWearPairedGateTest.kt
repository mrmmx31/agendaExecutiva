package com.pessoal.agenda.mobile.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.wear.contract.WearAlertStatus
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneWearPairedGateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = MobileDatabase.get(context)
    private val store = AlertStore(database)

    @Test
    fun pairedNodeIsReachable() {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 20, TimeUnit.SECONDS)
        assertTrue("Nenhum relógio pareado alcançável.", nodes.isNotEmpty())
    }

    @Test
    fun publishSnoozeFixture() = runBlocking {
        publishFixture(SNOOZE_ALERT_ID, "Gate pareado: adiar")
    }

    @Test
    fun assertSnoozeConverged() = runBlocking {
        val action = awaitAction(SNOOZE_ALERT_ID)
        assertEquals("SNOOZE", action.action)
        assertEquals("SCHEDULED", database.offline().alertMaterialization(SNOOZE_ALERT_ID)?.state)
        assertEquals(WearAlertStatus.SNOOZED, store.wearState(SNOOZE_ALERT_ID)?.status)
    }

    @Test
    fun publishCompleteFixture() = runBlocking {
        publishFixture(COMPLETE_ALERT_ID, "Gate pareado: concluir")
    }

    @Test
    fun publishOfflineFixture() = runBlocking {
        publishFixture(OFFLINE_ALERT_ID, "Gate offline: concluir")
    }

    @Test
    fun assertCompleteConverged() = runBlocking {
        val action = awaitAction(COMPLETE_ALERT_ID)
        assertEquals("COMPLETE", action.action)
        assertEquals("COMPLETED", database.offline().alertMaterialization(COMPLETE_ALERT_ID)?.state)
    }

    @Test
    fun assertOfflineConvergedAfterRestart() = runBlocking {
        val action = awaitAction(OFFLINE_ALERT_ID)
        assertEquals("COMPLETE", action.action)
        assertEquals("COMPLETED", database.offline().alertMaterialization(OFFLINE_ALERT_ID)?.state)
    }

    private suspend fun publishFixture(alertId: String, title: String) {
        store.ensureInstallationProfile()
        if (database.offline().alertMaterialization(alertId) == null) {
            val now = Instant.now()
            store.materialize(AlertDefinition(
                contractVersion = 1,
                alertId = alertId,
                origin = AlertOrigin.TASK,
                referenceId = REFERENCE_ID,
                text = title,
                reason = "Validação automatizada sem estímulo",
                sourceDeviceId = PHONE_DEVICE_ID,
                scheduledAt = now.minusSeconds(60).toString(),
                validUntil = now.plusSeconds(3_600).toString(),
                criticality = FunctionalCriticality.ROUTINE,
                allowedChannels = setOf(SensoryChannel.VISUAL),
                repeatPolicy = AlertRepeatPolicy(1, 15),
                actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
            ))
        }
        assertEquals(WearStatePublishResult.STORED, AndroidWearStatePublisher(context, store).publish(alertId))
    }

    private suspend fun awaitAction(alertId: String) = repeatUntilNotNull {
        database.offline().latestAlertAction(alertId)
    }

    private suspend fun <T> repeatUntilNotNull(block: suspend () -> T?): T {
        repeat(100) {
            block()?.let { return it }
            delay(200)
        }
        error("Convergência Wear não ocorreu em 20 segundos.")
    }

    private companion object {
        const val SNOOZE_ALERT_ID = "91000000-0000-4000-8000-000000000001"
        const val COMPLETE_ALERT_ID = "91000000-0000-4000-8000-000000000002"
        const val OFFLINE_ALERT_ID = "91000000-0000-4000-8000-000000000005"
        const val REFERENCE_ID = "91000000-0000-4000-8000-000000000003"
        const val PHONE_DEVICE_ID = "91000000-0000-4000-8000-000000000004"
    }
}
