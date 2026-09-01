package com.pessoal.agenda.mobile.alert.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.output.AlertSensoryOutput
import com.pessoal.agenda.mobile.alert.output.SensoryOutputResult
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.alert.scheduling.AlertWorkEnqueuer
import com.pessoal.agenda.mobile.data.AlertDeliveryCandidate
import com.pessoal.agenda.mobile.data.AlertDeliveryReason
import com.pessoal.agenda.mobile.data.AlertSchedule
import com.pessoal.agenda.mobile.data.AlertStore
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
class AlertNotificationDeliveryTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: AlertStore
    private lateinit var enqueuer: RecordingEnqueuer
    private lateinit var publisher: RecordingPublisher
    private val now = Instant.parse("2026-09-01T14:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = AlertStore(database, clock)
        val initial = store.ensureInstallationProfile()
        store.saveProfile(
            initial.profile.copy(globalEnabled = true, quietHours = null),
            initial.snoozePolicy,
        )
        enqueuer = RecordingEnqueuer()
        publisher = RecordingPublisher()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun publishedNotificationRecordsDeliveryAndSchedulesRepeat() = runBlocking {
        store.materialize(alert(ALERT_ID))
        val processor = processor()

        processor.process(ALERT_ID, DELIVERY_ID)

        assertEquals(ALERT_ID, publisher.published.single().first.alertId)
        assertEquals(1, database.offline().alertDeliveryCount())
        val materialization = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals(1, materialization.deliveryCount)
        assertEquals("SCHEDULED", materialization.state)
        assertEquals("2026-09-01T14:15:00Z", materialization.nextEligibleAt)
        assertEquals(Instant.parse("2026-09-01T14:15:00Z"), enqueuer.appended.single().nextAt)
    }

    @Test
    fun deniedPermissionIsRecordedWithoutConsumingDelivery() = runBlocking {
        store.materialize(alert(ALERT_ID))
        publisher.result = NotificationPublishResult.PERMISSION_DENIED

        processor().process(ALERT_ID, DELIVERY_ID)

        val delivery = requireNotNull(database.offline().alertDelivery(DELIVERY_ID))
        assertEquals("SUPPRESSED", delivery.state)
        assertEquals("PERMISSION_DENIED", delivery.technicalReason)
        val materialization = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals(0, materialization.deliveryCount)
        assertEquals("SUPPRESSED", materialization.state)
        assertTrue(enqueuer.appended.isEmpty())
    }

    @Test
    fun audioOnlyDeliveryDoesNotPublishVisualNotification() = runBlocking {
        enableChannels(setOf(SensoryChannel.AUDIO))
        store.materialize(alert(ALERT_ID, setOf(SensoryChannel.AUDIO)))
        val sensory = RecordingSensoryOutput(
            SensoryOutputResult(setOf(SensoryChannel.AUDIO), AlertDeliveryReason.AUDIO_FALLBACK),
        )

        processor(sensory).process(ALERT_ID, DELIVERY_ID)

        assertTrue(publisher.published.isEmpty())
        val delivery = requireNotNull(database.offline().alertDelivery(DELIVERY_ID))
        assertEquals("DELIVERED", delivery.state)
        assertEquals("AUDIO_FALLBACK", delivery.technicalReason)
        assertTrue(delivery.channelsJson.contains("AUDIO"))
    }

    @Test
    fun failedSensoryChannelRecordsPartialVisualDelivery() = runBlocking {
        val channels = setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION)
        enableChannels(channels)
        store.materialize(alert(ALERT_ID, channels))
        val sensory = RecordingSensoryOutput(
            SensoryOutputResult(emptySet(), AlertDeliveryReason.ROUTE_UNAVAILABLE),
        )

        processor(sensory).process(ALERT_ID, DELIVERY_ID)

        val delivery = requireNotNull(database.offline().alertDelivery(DELIVERY_ID))
        assertEquals("DELIVERED", delivery.state)
        assertEquals("PARTIAL_DELIVERY", delivery.technicalReason)
        assertTrue(delivery.channelsJson.contains("VISUAL"))
        assertFalse(delivery.channelsJson.contains("PHONE_VIBRATION"))
    }

    @Test
    fun completeActionIsIdempotentAndCancelsNotificationAndWork() = runBlocking {
        store.materialize(alert(ALERT_ID))
        val action = AlertNotificationAction(
            operationId = OPERATION_ID,
            alertId = ALERT_ID,
            action = AlertActionType.COMPLETE,
            occurredAt = now,
            snoozeUntil = null,
        )
        val processor = actionProcessor()

        assertTrue(processor.process(action))
        assertFalse(processor.process(action))

        assertEquals(1, database.offline().alertActionCount())
        assertEquals("COMPLETED", database.offline().alertMaterialization(ALERT_ID)?.state)
        assertEquals(listOf(ALERT_ID, ALERT_ID), enqueuer.cancelled)
        assertEquals(listOf(ALERT_ID, ALERT_ID), publisher.cancelled)
    }

    @Test
    fun snoozeActionIsIdempotentAndReplacesAbsoluteSchedule() = runBlocking {
        store.materialize(alert(ALERT_ID))
        val target = now.plusSeconds(10 * 60L)
        val action = AlertNotificationAction(
            operationId = OPERATION_ID,
            alertId = ALERT_ID,
            action = AlertActionType.SNOOZE,
            occurredAt = now,
            snoozeUntil = target,
        )
        val processor = actionProcessor()

        assertTrue(processor.process(action))
        assertFalse(processor.process(action))

        assertEquals(1, database.offline().alertActionCount())
        val materialization = requireNotNull(database.offline().alertMaterialization(ALERT_ID))
        assertEquals(1, materialization.snoozeCount)
        assertEquals("SCHEDULED", materialization.state)
        assertEquals(target.toString(), materialization.nextEligibleAt)
        assertEquals(listOf(target, target), enqueuer.replaced.map(AlertSchedule::nextAt))
        assertEquals(listOf(ALERT_ID, ALERT_ID), publisher.cancelled)
    }

    private fun processor(sensoryOutput: AlertSensoryOutput = NoSensoryOutput) = AlertDeliveryProcessor(
        store = store,
        enqueuer = enqueuer,
        publisher = publisher,
        sensoryOutput = sensoryOutput,
        deviceIdProvider = { DEVICE_ID },
        clock = clock,
    )

    private fun actionProcessor() = AlertActionProcessor(
        store = store,
        scheduling = AlertSchedulingCoordinator(store, enqueuer),
        publisher = publisher,
        deviceIdProvider = { DEVICE_ID },
    )

    private suspend fun enableChannels(channels: Set<SensoryChannel>) {
        val stored = store.ensureInstallationProfile()
        store.saveProfile(stored.profile.copy(enabledChannels = channels), stored.snoozePolicy)
    }

    private fun alert(
        id: String,
        channels: Set<SensoryChannel> = setOf(SensoryChannel.VISUAL),
    ) = AlertDefinition(
        contractVersion = 1,
        alertId = id,
        origin = AlertOrigin.TASK,
        referenceId = REFERENCE_ID,
        text = "Revisar compromisso",
        reason = "Horário planejado",
        sourceDeviceId = DEVICE_ID,
        scheduledAt = now.minusSeconds(60).toString(),
        validUntil = now.plusSeconds(3_600).toString(),
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = channels,
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
        override fun cancelAll() = Unit
    }

    private class RecordingPublisher : AlertNotificationPublisher {
        var result = NotificationPublishResult.PUBLISHED
        val published = mutableListOf<Pair<AlertDeliveryCandidate, AlertNotificationCommand>>()
        val cancelled = mutableListOf<String>()
        override fun publish(
            candidate: AlertDeliveryCandidate,
            command: AlertNotificationCommand,
        ): NotificationPublishResult {
            published += candidate to command
            return result
        }
        override fun cancel(alertId: String) { cancelled += alertId }
    }

    private data object NoSensoryOutput : AlertSensoryOutput {
        override suspend fun deliver(candidate: AlertDeliveryCandidate) = SensoryOutputResult(emptySet())
    }

    private class RecordingSensoryOutput(private val result: SensoryOutputResult) : AlertSensoryOutput {
        override suspend fun deliver(candidate: AlertDeliveryCandidate) = result
    }

    private companion object {
        const val ALERT_ID = "70000000-0000-4000-8000-000000000001"
        const val DELIVERY_ID = "70000000-0000-4000-8000-000000000002"
        const val OPERATION_ID = "70000000-0000-4000-8000-000000000003"
        const val REFERENCE_ID = "70000000-0000-4000-8000-000000000004"
        const val DEVICE_ID = "70000000-0000-4000-8000-000000000005"
    }
}
