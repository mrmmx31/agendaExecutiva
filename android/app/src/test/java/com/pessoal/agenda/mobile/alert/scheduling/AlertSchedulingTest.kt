package com.pessoal.agenda.mobile.alert.scheduling

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.data.AlertSchedule
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.AlertWorkEvaluation
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
class AlertSchedulingTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: AlertStore
    private lateinit var enqueuer: RecordingEnqueuer
    private lateinit var coordinator: AlertSchedulingCoordinator
    private val now = Instant.parse("2026-09-01T14:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = AlertStore(database, Clock.fixed(now, ZoneOffset.UTC))
        enqueuer = RecordingEnqueuer()
        coordinator = AlertSchedulingCoordinator(store, enqueuer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun scheduleAndReconciliationKeepOneAbsoluteTarget() = runBlocking {
        store.ensureInstallationProfile()
        store.materialize(alert())
        val target = Instant.parse("2026-09-01T15:00:00Z")

        assertTrue(coordinator.schedule(ALERT_ID, target))
        assertEquals(AlertSchedule(ALERT_ID, target), enqueuer.replaced.single())
        assertEquals(1, coordinator.reconcile())
        assertEquals(listOf(target, target), enqueuer.replaced.map(AlertSchedule::nextAt))
    }

    @Test
    fun cancellationChangesDurableStateAndCancelsUniqueWork() = runBlocking {
        store.ensureInstallationProfile()
        store.materialize(alert())
        coordinator.schedule(ALERT_ID, now.plusSeconds(60))

        assertTrue(coordinator.cancel(ALERT_ID))
        assertFalse(coordinator.cancel(ALERT_ID))

        assertEquals(listOf(ALERT_ID, ALERT_ID), enqueuer.cancelled)
        assertEquals("CANCELLED", database.offline().alertMaterialization(ALERT_ID)?.state)
    }

    @Test
    fun workerEvaluationReschedulesFutureAlertWithoutConsumingDelivery() = runBlocking {
        val initial = store.ensureInstallationProfile()
        store.saveProfile(initial.profile.copy(globalEnabled = true, quietHours = null), initial.snoozePolicy)
        store.materialize(alert(scheduledAt = "2026-09-01T15:00:00Z"))

        val result = store.evaluateForWork(ALERT_ID, now, ZoneOffset.UTC)

        assertEquals(
            AlertWorkEvaluation.Reschedule(
                Instant.parse("2026-09-01T15:00:00Z"),
                com.pessoal.agenda.mobile.alert.AlertSuppression.NOT_DUE,
            ),
            result,
        )
        val state = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals("SCHEDULED", state.state)
        assertEquals(0, state.deliveryCount)
    }

    @Test
    fun eligibleEvaluationStopsAtSimulatedDeliveryBoundary() = runBlocking {
        val initial = store.ensureInstallationProfile()
        store.saveProfile(initial.profile.copy(globalEnabled = true, quietHours = null), initial.snoozePolicy)
        store.materialize(alert())

        val result = store.evaluateForWork(ALERT_ID, now, ZoneOffset.UTC)

        assertEquals(AlertWorkEvaluation.Ready(setOf(SensoryChannel.VISUAL)), result)
        val state = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals("AWAITING_DELIVERY", state.state)
        assertEquals(0, state.deliveryCount)
        assertEquals(0, database.offline().alertDeliveryCount())
    }

    @Test
    fun expiredAndGloballyDisabledAlertsDoNotLoop() = runBlocking {
        store.ensureInstallationProfile()
        store.materialize(alert())

        assertEquals(AlertWorkEvaluation.Stop, store.evaluateForWork(ALERT_ID, now, ZoneOffset.UTC))
        assertEquals("SUPPRESSED", database.offline().alertMaterialization(ALERT_ID)?.state)

        val expiredId = "60000000-0000-4000-8000-000000000010"
        store.materialize(alert(id = expiredId, validUntil = "2026-09-01T13:59:00Z"))
        assertEquals(AlertWorkEvaluation.Stop, store.evaluateForWork(expiredId, now, ZoneOffset.UTC))
        assertEquals("EXPIRED", database.offline().alertMaterialization(expiredId)?.state)
    }

    private fun alert(
        id: String = ALERT_ID,
        scheduledAt: String = "2026-09-01T13:55:00Z",
        validUntil: String = "2026-09-01T18:00:00Z",
    ) = AlertDefinition(
        contractVersion = 1,
        alertId = id,
        origin = AlertOrigin.TASK,
        referenceId = REFERENCE_ID,
        text = "Revisar a tarefa atual",
        reason = "Horário planejado da tarefa",
        sourceDeviceId = DEVICE_ID,
        scheduledAt = scheduledAt,
        validUntil = validUntil,
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = setOf(SensoryChannel.VISUAL),
        repeatPolicy = AlertRepeatPolicy(2, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private class RecordingEnqueuer : AlertWorkEnqueuer {
        val replaced = mutableListOf<AlertSchedule>()
        val appended = mutableListOf<AlertSchedule>()
        val cancelled = mutableListOf<String>()

        override fun replace(schedule: AlertSchedule) { replaced += schedule }
        override fun append(schedule: AlertSchedule) { appended += schedule }
        override fun cancel(alertId: String) { cancelled += alertId }
    }

    private companion object {
        const val ALERT_ID = "60000000-0000-4000-8000-000000000001"
        const val REFERENCE_ID = "60000000-0000-4000-8000-000000000002"
        const val DEVICE_ID = "60000000-0000-4000-8000-000000000003"
    }
}
