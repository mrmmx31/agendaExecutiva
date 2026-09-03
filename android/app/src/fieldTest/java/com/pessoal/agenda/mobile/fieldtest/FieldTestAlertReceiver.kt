package com.pessoal.agenda.mobile.fieldtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    private companion object {
        const val ACTION_PUBLISH_FIXTURE_ALERT =
            "com.pessoal.agenda.mobile.fieldtest.PUBLISH_FIXTURE_ALERT"
    }
}
