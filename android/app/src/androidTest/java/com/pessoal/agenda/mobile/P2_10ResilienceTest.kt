package com.pessoal.agenda.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.alert.scheduling.WorkManagerAlertEnqueuer
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P2_10ResilienceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = MobileDatabase.get(context)

    @Test
    fun prepareDurableOfflineFixtureWithoutDelivery() = runBlocking {
        val ids = ArrayDeque(listOf(CAPTURE_ID, CAPTURE_OPERATION_ID))
        val repository = OfflineRepository(
            database = database,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            newId = { ids.removeFirst() },
            deviceIdProvider = { DEVICE_ID },
        )
        assertEquals(CAPTURE_ID, repository.createCapture("Captura fictícia P2-10 sem rede"))

        val store = AlertStore(database, Clock.fixed(NOW, ZoneOffset.UTC))
        val initial = store.ensureInstallationProfile()
        store.saveProfile(
            initial.profile.copy(
                globalEnabled = true,
                enabledChannels = setOf(SensoryChannel.VISUAL),
                quietHours = null,
            ),
            initial.snoozePolicy,
        )
        store.materialize(futureAlert())
        assertTrue(AlertSchedulingCoordinator(context, store).schedule(ALERT_ID, NOW.plusSeconds(14_400)))

        assertDurableState()
    }

    @Test
    fun assertDurableFixtureAfterProcessIdleOrReboot() = runBlocking {
        assertDurableState()
    }

    private suspend fun assertDurableState() {
        assertTrue(database.offline().observeCaptures().first().any { it.id == CAPTURE_ID })
        assertTrue(database.offline().operationsForSync().any { it.operationId == CAPTURE_OPERATION_ID })
        assertEquals("SCHEDULED", database.offline().alertMaterialization(ALERT_ID)?.state)
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerAlertEnqueuer.uniqueName(ALERT_ID))
            .get(10, TimeUnit.SECONDS)
        assertNotNull(work.firstOrNull { !it.state.isFinished })
    }

    private fun futureAlert() = AlertDefinition(
        contractVersion = AlertDefinition.CONTRACT_VERSION,
        alertId = ALERT_ID,
        origin = AlertOrigin.MANUAL,
        referenceId = null,
        text = "Alerta fictício futuro P2-10",
        reason = "Persistência virtual sem entrega sensorial",
        sourceDeviceId = DEVICE_ID,
        scheduledAt = NOW.plusSeconds(14_400).toString(),
        validUntil = NOW.plusSeconds(21_600).toString(),
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = setOf(SensoryChannel.VISUAL),
        repeatPolicy = AlertRepeatPolicy(1, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-03T12:00:00Z")
        const val CAPTURE_ID = "92000000-0000-4000-8000-000000000001"
        const val CAPTURE_OPERATION_ID = "92000000-0000-4000-8000-000000000002"
        const val ALERT_ID = "92000000-0000-4000-8000-000000000003"
        const val DEVICE_ID = "92000000-0000-4000-8000-000000000004"
    }
}

