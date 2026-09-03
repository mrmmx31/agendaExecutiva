package com.pessoal.agenda.mobile.alert.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.data.AlertDeliveryCandidate
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Instant
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertNotificationPublisherTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val publisher = AndroidAlertNotificationPublisher(context)

    @Before
    fun prepareChannel() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        manager.cancelAll()
        manager.deleteNotificationChannel(AndroidAlertNotificationPublisher.CHANNEL_ID)
    }

    @After
    fun cleanUp() {
        manager.cancelAll()
        manager.deleteNotificationChannel(AndroidAlertNotificationPublisher.CHANNEL_ID)
    }

    @Test
    fun deniedPermissionDoesNotCreateNotification() {
        val deniedPublisher = AndroidAlertNotificationPublisher(context) { false }

        val result = deniedPublisher.publish(candidate(), command())

        assertEquals(NotificationPublishResult.PERMISSION_DENIED, result)
        assertFalse(manager.activeNotifications.any { it.tag == ALERT_ID })
    }

    @Test
    fun grantedPermissionCreatesPrivateSilentNotificationWithTwoActions() {
        assertEquals(NotificationPublishResult.PUBLISHED, publisher.publish(candidate(), command()))

        val posted = waitForNotification(ALERT_ID)
        val channel = requireNotNull(manager.getNotificationChannel(AndroidAlertNotificationPublisher.CHANNEL_ID))
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.notification.visibility)
        assertEquals(0, posted.notification.flags and Notification.FLAG_LOCAL_ONLY)
        assertEquals(2, posted.notification.actions.size)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun completePendingIntentPersistsOfflineActionAndClosesNotification() {
        val alertId = UUID.randomUUID().toString()
        val store = AlertStore(MobileDatabase.get(context))
        kotlinx.coroutines.runBlocking { store.materialize(definition(alertId)) }
        val command = command()
        assertEquals(NotificationPublishResult.PUBLISHED, publisher.publish(candidate(alertId), command))
        val posted = waitForNotification(alertId)

        posted.notification.actions.first().actionIntent.send()

        val materialization = waitForMaterialization(alertId, "COMPLETED")
        assertEquals("COMPLETED", materialization.state)
        assertNotNull(kotlinx.coroutines.runBlocking {
            MobileDatabase.get(context).offline().alertAction(command.completeOperationId)
        })
        waitForNotificationRemoval(alertId)
    }

    @Test
    fun snoozePendingIntentPersistsOfflineActionAndClosesNotification() {
        val alertId = UUID.randomUUID().toString()
        val store = AlertStore(MobileDatabase.get(context))
        kotlinx.coroutines.runBlocking { store.materialize(definition(alertId)) }
        val command = command()
        assertEquals(NotificationPublishResult.PUBLISHED, publisher.publish(candidate(alertId), command))
        val posted = waitForNotification(alertId)

        posted.notification.actions[1].actionIntent.send()

        val materialization = waitForMaterialization(alertId, "SCHEDULED")
        assertEquals(command.snoozeUntil.toString(), materialization.nextEligibleAt)
        assertNotNull(kotlinx.coroutines.runBlocking {
            MobileDatabase.get(context).offline().alertAction(command.snoozeOperationId)
        })
        waitForNotificationRemoval(alertId)
    }

    private fun waitForNotification(alertId: String): android.service.notification.StatusBarNotification {
        repeat(50) {
            manager.activeNotifications.firstOrNull { it.tag == alertId }?.let { return it }
            Thread.sleep(100)
        }
        error("Notificação não foi publicada.")
    }

    private fun waitForNotificationRemoval(alertId: String) {
        repeat(50) {
            if (manager.activeNotifications.none { it.tag == alertId }) return
            Thread.sleep(100)
        }
        error("Notificação não foi removida.")
    }

    private fun waitForMaterialization(
        alertId: String,
        expectedState: String,
    ): com.pessoal.agenda.mobile.data.local.AlertMaterializationEntity {
        repeat(50) {
            val value = kotlinx.coroutines.runBlocking {
                MobileDatabase.get(context).offline().alertMaterialization(alertId)
            }
            if (value?.state == expectedState) return value
            Thread.sleep(100)
        }
        error("Ação da notificação não foi persistida.")
    }

    private fun candidate(alertId: String = ALERT_ID) = AlertDeliveryCandidate(
        alertId = alertId,
        text = "Compromisso fictício",
        reason = "Teste instrumental silencioso",
        channels = setOf(SensoryChannel.VISUAL),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
        snoozeMinutes = 10,
        audioRoute = AudioRoutePolicy.SYSTEM_DEFAULT,
    )

    private fun command() = AlertNotificationCommand(
        completeOperationId = UUID.randomUUID().toString(),
        snoozeOperationId = UUID.randomUUID().toString(),
        occurredAt = Instant.now(),
        snoozeUntil = Instant.now().plusSeconds(600),
    )

    private fun definition(alertId: String) = AlertDefinition(
        contractVersion = 1,
        alertId = alertId,
        origin = AlertOrigin.MANUAL,
        referenceId = null,
        text = "Compromisso fictício",
        reason = "Teste instrumental silencioso",
        sourceDeviceId = UUID.randomUUID().toString(),
        scheduledAt = Instant.now().minusSeconds(60).toString(),
        validUntil = Instant.now().plusSeconds(3_600).toString(),
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = setOf(SensoryChannel.VISUAL),
        repeatPolicy = AlertRepeatPolicy(1, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private companion object {
        const val ALERT_ID = "80000000-0000-4000-8000-000000000001"
    }
}
