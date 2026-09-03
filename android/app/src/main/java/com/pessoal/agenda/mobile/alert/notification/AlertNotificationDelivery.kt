package com.pessoal.agenda.mobile.alert.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.pessoal.agenda.mobile.MainActivity
import com.pessoal.agenda.mobile.R
import com.pessoal.agenda.mobile.alert.AlertActionCommand
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.output.AlertSensoryOutput
import com.pessoal.agenda.mobile.alert.output.SensoryOutputResult
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.alert.scheduling.AlertWorkEnqueuer
import com.pessoal.agenda.mobile.data.AlertDeliveryCandidate
import com.pessoal.agenda.mobile.data.AlertDeliveryOutcome
import com.pessoal.agenda.mobile.data.AlertDeliveryReason
import com.pessoal.agenda.mobile.data.AlertDeliveryRecord
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.recommendation.RecommendationSourceDevice
import com.pessoal.agenda.mobile.data.AlertWorkEvaluation
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID
import com.pessoal.agenda.mobile.wear.AlertWearPublisher
import com.pessoal.agenda.mobile.wear.NoOpAlertWearPublisher

class AlertDeliveryProcessor(
    private val store: AlertStore,
    private val enqueuer: AlertWorkEnqueuer,
    private val publisher: AlertNotificationPublisher,
    private val sensoryOutput: AlertSensoryOutput,
    private val deviceIdProvider: () -> String,
    private val wearPublisher: AlertWearPublisher = NoOpAlertWearPublisher,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun process(alertId: String, deliveryId: String) {
        UUID.fromString(alertId)
        UUID.fromString(deliveryId)
        when (val evaluation = store.evaluateForWork(alertId)) {
            is AlertWorkEvaluation.Reschedule -> enqueuer.append(
                com.pessoal.agenda.mobile.data.AlertSchedule(alertId, evaluation.nextAt),
            )
            is AlertWorkEvaluation.Ready -> deliver(evaluation, deliveryId)
            AlertWorkEvaluation.Stop -> Unit
        }
    }

    private suspend fun deliver(evaluation: AlertWorkEvaluation.Ready, deliveryId: String) {
        val occurredAt = Instant.now(clock)
        val command = AlertNotificationCommand(
            completeOperationId = operationId(deliveryId, AlertActionType.COMPLETE),
            snoozeOperationId = operationId(deliveryId, AlertActionType.SNOOZE),
            occurredAt = occurredAt,
            snoozeUntil = occurredAt.plusSeconds(evaluation.candidate.snoozeMinutes * 60L),
        )
        val visualResult = if (SensoryChannel.VISUAL in evaluation.candidate.channels) {
            publisher.publish(evaluation.candidate, command)
        } else {
            null
        }
        val sensoryResult = sensoryOutput.deliver(evaluation.candidate)
        val outcome = outcome(evaluation.candidate.channels, visualResult, sensoryResult)
        store.recordDelivery(
            AlertDeliveryRecord(
                deliveryId = deliveryId,
                alertId = evaluation.candidate.alertId,
                deviceId = deviceIdProvider(),
                channels = outcome.channels,
                outcome = outcome.outcome,
                technicalReason = outcome.reason,
                attemptedAt = occurredAt.toString(),
            ),
        )
        if (outcome.outcome == AlertDeliveryOutcome.DELIVERED) {
            evaluation.nextEvaluationAt
                ?.let { store.scheduleEvaluation(evaluation.candidate.alertId, it) }
                ?.let(enqueuer::append)
        }
        wearPublisher.publish(evaluation.candidate.alertId)
    }

    private fun outcome(
        requested: Set<SensoryChannel>,
        visualResult: NotificationPublishResult?,
        sensoryResult: SensoryOutputResult,
    ): DeliveryOutcome {
        val delivered = sensoryResult.deliveredChannels.toMutableSet()
        if (visualResult == NotificationPublishResult.PUBLISHED) delivered += SensoryChannel.VISUAL
        if (delivered.isNotEmpty()) {
            val incomplete = requested.any { it !in delivered }
            val reason = when {
                incomplete -> AlertDeliveryReason.PARTIAL_DELIVERY
                sensoryResult.reason == AlertDeliveryReason.AUDIO_FALLBACK -> AlertDeliveryReason.AUDIO_FALLBACK
                else -> null
            }
            return DeliveryOutcome(AlertDeliveryOutcome.DELIVERED, delivered, reason)
        }
        val reason = when {
            visualResult == NotificationPublishResult.PERMISSION_DENIED -> AlertDeliveryReason.PERMISSION_DENIED
            visualResult == NotificationPublishResult.SYSTEM_FAILURE -> AlertDeliveryReason.SYSTEM_FAILURE
            sensoryResult.reason != null -> sensoryResult.reason
            else -> AlertDeliveryReason.ROUTE_UNAVAILABLE
        }
        val outcome = if (reason == AlertDeliveryReason.PERMISSION_DENIED) {
            AlertDeliveryOutcome.SUPPRESSED
        } else {
            AlertDeliveryOutcome.FAILED
        }
        return DeliveryOutcome(outcome, emptySet(), reason)
    }

    private fun operationId(deliveryId: String, action: AlertActionType): String = UUID.nameUUIDFromBytes(
        "$deliveryId:${action.name}".toByteArray(StandardCharsets.UTF_8),
    ).toString()

    private data class DeliveryOutcome(
        val outcome: AlertDeliveryOutcome,
        val channels: Set<SensoryChannel>,
        val reason: AlertDeliveryReason?,
    )
}

class AlertActionProcessor(
    private val store: AlertStore,
    private val scheduling: AlertSchedulingCoordinator,
    private val publisher: AlertNotificationPublisher,
    private val deviceIdProvider: () -> String,
    private val wearPublisher: AlertWearPublisher = NoOpAlertWearPublisher,
) {
    suspend fun process(payload: AlertNotificationAction): Boolean {
        val profile = store.ensureInstallationProfile()
        val command = AlertActionCommand(
            contractVersion = AlertDefinition.CONTRACT_VERSION,
            operationId = payload.operationId,
            alertId = payload.alertId,
            sourceDeviceId = payload.sourceDeviceId ?: deviceIdProvider(),
            action = payload.action,
            occurredAt = payload.occurredAt.toString(),
            snoozeUntil = payload.snoozeUntil?.toString(),
        )
        val source = if (payload.sourceDeviceId == null) {
            RecommendationSourceDevice.PHONE
        } else {
            RecommendationSourceDevice.WATCH
        }
        val inserted = store.recordAction(command, profile.snoozePolicy, source)
        when (payload.action) {
            AlertActionType.COMPLETE -> scheduling.cancel(payload.alertId)
            AlertActionType.SNOOZE -> scheduling.schedule(payload.alertId, requireNotNull(payload.snoozeUntil))
        }
        publisher.cancel(payload.alertId)
        wearPublisher.publish(payload.alertId)
        return inserted
    }
}

interface AlertNotificationPublisher {
    fun publish(candidate: AlertDeliveryCandidate, command: AlertNotificationCommand): NotificationPublishResult
    fun cancel(alertId: String)
}

data class AlertNotificationCommand(
    val completeOperationId: String,
    val snoozeOperationId: String,
    val occurredAt: Instant,
    val snoozeUntil: Instant,
)

data class AlertNotificationAction(
    val operationId: String,
    val alertId: String,
    val action: AlertActionType,
    val occurredAt: Instant,
    val snoozeUntil: Instant?,
    val sourceDeviceId: String? = null,
)

enum class NotificationPublishResult { PUBLISHED, PERMISSION_DENIED, ROUTE_UNAVAILABLE, SYSTEM_FAILURE }

class AndroidAlertNotificationPublisher(
    private val context: Context,
    private val permissionOverride: (() -> Boolean)? = null,
) : AlertNotificationPublisher {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun publish(
        candidate: AlertDeliveryCandidate,
        command: AlertNotificationCommand,
    ): NotificationPublishResult {
        if (SensoryChannel.VISUAL !in candidate.channels) return NotificationPublishResult.ROUTE_UNAVAILABLE
        if (!notificationsAllowed()) return NotificationPublishResult.PERMISSION_DENIED
        return runCatching {
            ensureSilentChannel()
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return NotificationPublishResult.PERMISSION_DENIED
            }
            manager.notify(candidate.alertId, NOTIFICATION_ID, notification(candidate, command))
            NotificationPublishResult.PUBLISHED
        }.getOrElse {
            if (it is SecurityException) NotificationPublishResult.PERMISSION_DENIED
            else NotificationPublishResult.SYSTEM_FAILURE
        }
    }

    override fun cancel(alertId: String) {
        UUID.fromString(alertId)
        manager.cancel(alertId, NOTIFICATION_ID)
    }

    fun cancelAllVisualAlerts() {
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun notificationsAllowed(): Boolean = permissionOverride?.invoke()
        ?: (manager.areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED))

    private fun ensureSilentChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_visual_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alert_channel_visual_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(
        candidate: AlertDeliveryCandidate,
        command: AlertNotificationCommand,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(candidate.text)
            .setContentText(candidate.reason)
            .setStyle(Notification.BigTextStyle().bigText(candidate.reason))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(openAgendaIntent(candidate.alertId))
            .setPublicVersion(publicNotification())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setAllowSystemGeneratedContextualActions(false)
        }
        if (AlertActionType.COMPLETE in candidate.actions) {
            builder.addAction(action(candidate, command, AlertActionType.COMPLETE))
        }
        if (AlertActionType.SNOOZE in candidate.actions) {
            builder.addAction(action(candidate, command, AlertActionType.SNOOZE))
        }
        return builder.build()
    }

    private fun publicNotification(): Notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(context.getString(R.string.alert_public_title))
        .setContentText(context.getString(R.string.alert_public_text))
        .build()

    private fun action(
        candidate: AlertDeliveryCandidate,
        command: AlertNotificationCommand,
        type: AlertActionType,
    ): Notification.Action {
        val isComplete = type == AlertActionType.COMPLETE
        val intent = Intent(context, AlertNotificationActionReceiver::class.java).apply {
            action = if (isComplete) ACTION_COMPLETE else ACTION_SNOOZE
            setPackage(context.packageName)
            putExtra(EXTRA_ALERT_ID, candidate.alertId)
            putExtra(
                EXTRA_OPERATION_ID,
                if (isComplete) command.completeOperationId else command.snoozeOperationId,
            )
            putExtra(EXTRA_OCCURRED_AT, command.occurredAt.toString())
            if (!isComplete) putExtra(EXTRA_SNOOZE_UNTIL, command.snoozeUntil.toString())
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(candidate.alertId, type),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(
            context,
            if (isComplete) android.R.drawable.checkbox_on_background else android.R.drawable.ic_lock_idle_alarm,
        )
        return Notification.Action.Builder(
            icon,
            context.getString(if (isComplete) R.string.alert_action_complete else R.string.alert_action_snooze),
            pending,
        ).build()
    }

    private fun openAgendaIntent(alertId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            putExtra(EXTRA_ALERT_ID, alertId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(alertId: String, action: AlertActionType): Int = 31 * alertId.hashCode() + action.ordinal

    companion object {
        const val CHANNEL_ID = "agenda_visual_alerts_v1"
        const val ACTION_COMPLETE = "com.pessoal.agenda.mobile.alert.COMPLETE"
        const val ACTION_SNOOZE = "com.pessoal.agenda.mobile.alert.SNOOZE"
        const val ACTION_OPEN = "com.pessoal.agenda.mobile.alert.OPEN"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_OPERATION_ID = "operation_id"
        const val EXTRA_OCCURRED_AT = "occurred_at"
        const val EXTRA_SNOOZE_UNTIL = "snooze_until"
        private const val NOTIFICATION_ID = 0
    }
}
