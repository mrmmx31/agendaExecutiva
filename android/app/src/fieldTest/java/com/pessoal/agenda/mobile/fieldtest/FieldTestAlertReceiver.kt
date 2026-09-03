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
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
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
        if (intent.action != ACTION_PUBLISH_FIXTURE_ALERT) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                publishFixture(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun publishFixture(context: Context) {
        val now = Instant.now()
        val alertId = UUID.randomUUID().toString()
        val store = AlertStore(MobileDatabase.get(context))
        val settings = store.ensureInstallationProfile()
        store.saveProfile(
            settings.profile.copy(
                globalEnabled = true,
                enabledChannels = setOf(SensoryChannel.VISUAL),
                quietHours = null,
                pausedUntil = null,
            ),
            settings.snoozePolicy,
        )
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
        AlertSchedulingCoordinator(context, store).schedule(alertId, now)
    }

    private fun publishVibrationRelayFixture(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
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
                vibrationPattern = longArrayOf(0, 350, 180, 350)
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
        const val VIBRATION_RELAY_CHANNEL_ID = "agenda_fieldtest_wearable_vibration_v1"
        const val VIBRATION_RELAY_NOTIFICATION_ID = 82_001
    }
}
