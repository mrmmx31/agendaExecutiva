package com.pessoal.agenda.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.alert.AlertActionCommand
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SnoozePolicy
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
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
class AlertStoreTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: AlertStore
    private val now = Instant.parse("2026-09-01T14:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = AlertStore(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun installationProfileIsPersistedDisabledAndCanRoundTrip() = runBlocking {
        val initial = store.ensureInstallationProfile()
        val repeated = store.ensureInstallationProfile()

        assertEquals(initial, repeated)
        assertFalse(initial.profile.globalEnabled)
        assertEquals(listOf(10, 30, 60), initial.snoozePolicy.presetMinutes)

        val enabled = initial.profile.copy(
            globalEnabled = true,
            enabledChannels = setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION),
            audioRoute = AudioRoutePolicy.PREFER_PHONE,
        )
        store.saveProfile(enabled, initial.snoozePolicy)

        assertEquals(enabled, store.ensureInstallationProfile().profile)
    }

    @Test
    fun materializationIsAtomicIdempotentAndRejectsReusedId() = runBlocking {
        assertTrue(store.materialize(alert()))
        assertFalse(store.materialize(alert()))

        val persisted = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals("READY", persisted.state)
        assertEquals("2026-09-01T13:55:00Z", persisted.nextEligibleAt)
        assertEquals(1, database.offline().alertDefinitionCount())

        assertFails { store.materialize(alert().copy(text = "Outro conteúdo")) }
        assertEquals(1, database.offline().alertDefinitionCount())
    }

    @Test
    fun deliveriesAreIdempotentAndRespectDefinitionLimit() = runBlocking {
        store.materialize(alert())
        val first = delivery(DELIVERY_ONE, "2026-09-01T14:00:00Z")
        val second = delivery(DELIVERY_TWO, "2026-09-01T14:15:00Z")

        assertTrue(store.recordDelivery(first))
        assertFalse(store.recordDelivery(first))
        assertTrue(store.recordDelivery(second))
        assertFails { store.recordDelivery(delivery(DELIVERY_THREE, "2026-09-01T14:30:00Z")) }

        val materialized = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals(2, materialized.deliveryCount)
        assertEquals("2026-09-01T14:15:00Z", materialized.lastDeliveryAt)
        assertEquals(2, database.offline().alertDeliveryCount())
    }

    @Test
    fun snoozeAndCompleteActionsPersistOnceAndUpdateMaterialization() = runBlocking {
        store.materialize(alert())
        val policy = SnoozePolicy(listOf(10), 5, 60, 1)
        val snooze = action(ACTION_ONE, AlertActionType.SNOOZE, "2026-09-01T14:10:00Z")

        assertTrue(store.recordAction(snooze, policy))
        assertFalse(store.recordAction(snooze, policy))
        assertFails {
            store.recordAction(
                action(ACTION_TWO, AlertActionType.SNOOZE, "2026-09-01T14:20:00Z"),
                policy,
            )
        }
        assertTrue(store.recordAction(action(ACTION_THREE, AlertActionType.COMPLETE, null), policy))

        val materialized = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals("COMPLETED", materialized.state)
        assertEquals(1, materialized.snoozeCount)
        assertEquals(now.toString(), materialized.completedAt)
        assertEquals(2, database.offline().alertActionCount())
    }

    @Test
    fun suppressedDeliveryStoresOnlyTechnicalReason() = runBlocking {
        store.materialize(alert())
        val record = AlertDeliveryRecord(
            deliveryId = DELIVERY_ONE,
            alertId = ALERT_ID,
            deviceId = DEVICE_ID,
            channels = emptySet(),
            outcome = AlertDeliveryOutcome.SUPPRESSED,
            technicalReason = AlertDeliveryReason.GLOBAL_DISABLED,
            attemptedAt = now.toString(),
        )

        assertTrue(store.recordDelivery(record))

        val persisted = requireNotNull(database.offline().alertDelivery(DELIVERY_ONE))
        assertEquals("GLOBAL_DISABLED", persisted.technicalReason)
        assertNull(database.offline().alertMaterialization(ALERT_ID)?.lastDeliveryAt)
    }

    private fun alert() = AlertDefinition(
        contractVersion = 1,
        alertId = ALERT_ID,
        origin = AlertOrigin.TASK,
        referenceId = REFERENCE_ID,
        text = "Revisar a tarefa atual",
        reason = "Horário planejado da tarefa",
        sourceDeviceId = DEVICE_ID,
        scheduledAt = "2026-09-01T13:55:00Z",
        validUntil = "2026-09-01T18:00:00Z",
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION),
        repeatPolicy = AlertRepeatPolicy(2, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private fun delivery(id: String, attemptedAt: String) = AlertDeliveryRecord(
        deliveryId = id,
        alertId = ALERT_ID,
        deviceId = DEVICE_ID,
        channels = setOf(SensoryChannel.VISUAL),
        outcome = AlertDeliveryOutcome.DELIVERED,
        technicalReason = null,
        attemptedAt = attemptedAt,
    )

    private fun action(id: String, type: AlertActionType, snoozeUntil: String?) = AlertActionCommand(
        contractVersion = 1,
        operationId = id,
        alertId = ALERT_ID,
        sourceDeviceId = DEVICE_ID,
        action = type,
        occurredAt = now.toString(),
        snoozeUntil = snoozeUntil,
    )

    private suspend fun assertFails(action: suspend () -> Unit) {
        assertTrue(runCatching { action() }.isFailure)
    }

    private companion object {
        const val ALERT_ID = "50000000-0000-4000-8000-000000000001"
        const val REFERENCE_ID = "50000000-0000-4000-8000-000000000002"
        const val DEVICE_ID = "50000000-0000-4000-8000-000000000003"
        const val DELIVERY_ONE = "50000000-0000-4000-8000-000000000004"
        const val DELIVERY_TWO = "50000000-0000-4000-8000-000000000005"
        const val DELIVERY_THREE = "50000000-0000-4000-8000-000000000006"
        const val ACTION_ONE = "50000000-0000-4000-8000-000000000007"
        const val ACTION_TWO = "50000000-0000-4000-8000-000000000008"
        const val ACTION_THREE = "50000000-0000-4000-8000-000000000009"
    }
}
