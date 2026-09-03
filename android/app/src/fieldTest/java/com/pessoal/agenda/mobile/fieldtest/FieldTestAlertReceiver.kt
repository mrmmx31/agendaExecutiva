package com.pessoal.agenda.mobile.fieldtest

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.notification.AlertNotificationActionReceiver
import com.pessoal.agenda.mobile.alert.notification.AndroidAlertNotificationPublisher
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.wear.AndroidWearStateCleaner
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FieldTestAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PUBLISH_VIBRATION_RELAY_FIXTURE) {
            publishVibrationRelayFixture(context.applicationContext)
            return
        }
        if (intent.action !in ASYNC_ACTIONS) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PUBLISH_FIXTURE_ALERT -> publishFixture(context.applicationContext, enableProfile = true)
                    ACTION_PUBLISH_DISABLED_FIXTURE -> publishFixture(
                        context.applicationContext,
                        enableProfile = false,
                    )
                    ACTION_DISABLE_ALERTS -> disableAlerts(context.applicationContext)
                    ACTION_COMPLETE_LATEST_FIXTURE -> dispatchFixtureAction(
                        context.applicationContext,
                        AlertActionType.COMPLETE,
                    )
                    ACTION_SNOOZE_LATEST_FIXTURE -> dispatchFixtureAction(
                        context.applicationContext,
                        AlertActionType.SNOOZE,
                    )
                }
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun publishFixture(context: Context, enableProfile: Boolean) {
        val now = Instant.now()
        val alertId = UUID.randomUUID().toString()
        val store = AlertStore(MobileDatabase.get(context))
        val settings = store.ensureInstallationProfile()
        if (enableProfile) {
            store.saveProfile(
                settings.profile.copy(
                    globalEnabled = true,
                    enabledChannels = setOf(SensoryChannel.VISUAL),
                    quietHours = null,
                    pausedUntil = null,
                    cooldownMinutes = 1,
                ),
                settings.snoozePolicy,
            )
        }
        store.materialize(
            AlertDefinition(
                contractVersion = AlertDefinition.CONTRACT_VERSION,
                alertId = alertId,
                origin = AlertOrigin.MANUAL,
                referenceId = null,
                text = "Alerta fictício da Agenda",
                reason = "Validação física de notificação e smartband",
                sourceDeviceId = UUID.randomUUID().toString(),
                scheduledAt = now.minusSeconds(1).toString(),
                validUntil = now.plusSeconds(3_600).toString(),
                criticality = FunctionalCriticality.ROUTINE,
                allowedChannels = setOf(SensoryChannel.VISUAL),
                repeatPolicy = AlertRepeatPolicy(maxDeliveries = 1, minimumIntervalMinutes = 15),
                actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
            ),
        )
        context.getSharedPreferences(FIXTURE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LATEST_ALERT_ID, alertId)
            .apply()
        AlertSchedulingCoordinator(context, store).schedule(alertId, now)
    }

    private suspend fun disableAlerts(context: Context) {
        val store = AlertStore(MobileDatabase.get(context))
        val settings = store.ensureInstallationProfile()
        store.saveProfile(settings.profile.copy(globalEnabled = false), settings.snoozePolicy)
        AlertSchedulingCoordinator(context, store).pause()
        AndroidAlertNotificationPublisher(context).cancelAllVisualAlerts()
        context.getSystemService(NotificationManager::class.java).run {
            cancel(LEGACY_VIBRATION_RELAY_NOTIFICATION_ID)
            cancel(VIBRATION_RELAY_NOTIFICATION_ID)
        }
        AndroidWearStateCleaner(context).clearAll()
    }

    private suspend fun dispatchFixtureAction(context: Context, action: AlertActionType) {
        val alertId = context.getSharedPreferences(FIXTURE_PREFERENCES, Context.MODE_PRIVATE)
            .getString(LATEST_ALERT_ID, null)
            ?: return
        val now = Instant.now()
        val target = Intent(context, AlertNotificationActionReceiver::class.java).apply {
            this.action = when (action) {
                AlertActionType.COMPLETE -> AndroidAlertNotificationPublisher.ACTION_COMPLETE
                AlertActionType.SNOOZE -> AndroidAlertNotificationPublisher.ACTION_SNOOZE
            }
            putExtra(AndroidAlertNotificationPublisher.EXTRA_ALERT_ID, alertId)
            putExtra(AndroidAlertNotificationPublisher.EXTRA_OPERATION_ID, UUID.randomUUID().toString())
            putExtra(AndroidAlertNotificationPublisher.EXTRA_OCCURRED_AT, now.toString())
            if (action == AlertActionType.SNOOZE) {
                val snoozeMinutes = AlertStore(MobileDatabase.get(context))
                    .ensureInstallationProfile()
                    .snoozePolicy
                    .presetMinutes
                    .first()
                putExtra(
                    AndroidAlertNotificationPublisher.EXTRA_SNOOZE_UNTIL,
                    now.plusSeconds(snoozeMinutes * 60L).toString(),
                )
            }
        }
        context.sendBroadcast(target)
    }

    @Suppress("DEPRECATION") // Legacy metadata is intentional: some companion apps ignore channel vibration.
    private fun publishVibrationRelayFixture(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(LEGACY_VIBRATION_RELAY_NOTIFICATION_ID)
        manager.deleteNotificationChannel(LEGACY_VIBRATION_RELAY_CHANNEL_ID)
        manager.deleteNotificationChannel(VIBRATION_RELAY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                VIBRATION_RELAY_CHANNEL_ID,
                "Teste de vibração da pulseira",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Canal temporário do APK de teste, sem áudio"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION_RELAY_PATTERN
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        manager.notify(
            VIBRATION_RELAY_NOTIFICATION_ID,
            Notification.Builder(context, VIBRATION_RELAY_CHANNEL_ID)
                .setSmallIcon(com.pessoal.agenda.mobile.R.drawable.ic_launcher_foreground)
                .setContentTitle("Teste fictício de vibração")
                .setContentText("Sem áudio; verificar telefone e smartband")
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText("Sem áudio; verificar telefone e smartband"),
                )
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setVibrate(VIBRATION_RELAY_PATTERN)
                .setOnlyAlertOnce(false)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val ACTION_PUBLISH_FIXTURE_ALERT =
            "com.pessoal.agenda.mobile.fieldtest.PUBLISH_FIXTURE_ALERT"
        const val ACTION_PUBLISH_VIBRATION_RELAY_FIXTURE =
            "com.pessoal.agenda.mobile.fieldtest.PUBLISH_VIBRATION_RELAY_FIXTURE"
        const val ACTION_COMPLETE_LATEST_FIXTURE =
            "com.pessoal.agenda.mobile.fieldtest.COMPLETE_LATEST_FIXTURE"
        const val ACTION_SNOOZE_LATEST_FIXTURE =
            "com.pessoal.agenda.mobile.fieldtest.SNOOZE_LATEST_FIXTURE"
        const val ACTION_DISABLE_ALERTS =
            "com.pessoal.agenda.mobile.fieldtest.DISABLE_ALERTS"
        const val ACTION_PUBLISH_DISABLED_FIXTURE =
            "com.pessoal.agenda.mobile.fieldtest.PUBLISH_DISABLED_FIXTURE"
        val ASYNC_ACTIONS = setOf(
            ACTION_PUBLISH_FIXTURE_ALERT,
            ACTION_COMPLETE_LATEST_FIXTURE,
            ACTION_SNOOZE_LATEST_FIXTURE,
            ACTION_DISABLE_ALERTS,
            ACTION_PUBLISH_DISABLED_FIXTURE,
        )
        const val FIXTURE_PREFERENCES = "fieldtest_alert_fixture"
        const val LATEST_ALERT_ID = "latest_alert_id"
        const val LEGACY_VIBRATION_RELAY_CHANNEL_ID = "agenda_fieldtest_wearable_vibration_v1"
        const val LEGACY_VIBRATION_RELAY_NOTIFICATION_ID = 82_001
        const val VIBRATION_RELAY_CHANNEL_ID = "agenda_fieldtest_wearable_vibration_v2"
        const val VIBRATION_RELAY_NOTIFICATION_ID = 82_002
        val VIBRATION_RELAY_PATTERN = longArrayOf(0, 350, 180, 350)
    }
}
