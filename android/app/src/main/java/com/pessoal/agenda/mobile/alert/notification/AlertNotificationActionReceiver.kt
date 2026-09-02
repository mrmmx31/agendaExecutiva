package com.pessoal.agenda.mobile.alert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.pessoal.agenda.mobile.wear.AndroidWearStatePublisher

class AlertNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = intent.payload() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext
                val store = AlertStore(MobileDatabase.get(application))
                AlertActionProcessor(
                    store = store,
                    scheduling = AlertSchedulingCoordinator(application, store),
                    publisher = AndroidAlertNotificationPublisher(application),
                    deviceIdProvider = { DeviceCredentialStore(application).deviceId },
                    wearPublisher = AndroidWearStatePublisher(application, store),
                ).process(payload)
            } catch (error: Exception) {
                Log.e(TAG, "Falha ao processar ação de alerta.", error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun Intent.payload(): AlertNotificationAction? = runCatching {
        val type = when (action) {
            AndroidAlertNotificationPublisher.ACTION_COMPLETE -> AlertActionType.COMPLETE
            AndroidAlertNotificationPublisher.ACTION_SNOOZE -> AlertActionType.SNOOZE
            else -> return null
        }
        val alertId = requireNotNull(getStringExtra(AndroidAlertNotificationPublisher.EXTRA_ALERT_ID))
            .also(UUID::fromString)
        val operationId = requireNotNull(getStringExtra(AndroidAlertNotificationPublisher.EXTRA_OPERATION_ID))
            .also(UUID::fromString)
        val occurredAt = requireNotNull(getStringExtra(AndroidAlertNotificationPublisher.EXTRA_OCCURRED_AT))
            .let(Instant::parse)
        val snoozeUntil = getStringExtra(AndroidAlertNotificationPublisher.EXTRA_SNOOZE_UNTIL)
            ?.let(Instant::parse)
        AlertNotificationAction(operationId, alertId, type, occurredAt, snoozeUntil)
    }.getOrNull()

    private companion object { const val TAG = "AgendaAlertAction" }
}
