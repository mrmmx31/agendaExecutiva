package com.pessoal.agenda.mobile.alert

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class AlertPolicy(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    fun evaluate(
        alert: AlertDefinition,
        profile: SensoryProfile,
        now: Instant,
        deliveryCount: Int = 0,
        lastAlertDeliveryAt: Instant? = null,
        lastSensoryDeliveryAt: Instant? = null,
        sensoryDeliveryActive: Boolean = false,
    ): AlertDecision {
        alert.validate()
        profile.validate()
        if (!now.isBefore(Instant.parse(alert.validUntil))) return AlertDecision.suppressed(AlertSuppression.EXPIRED)
        if (!profile.globalEnabled) return AlertDecision.suppressed(AlertSuppression.GLOBAL_DISABLED)
        if (now.isBefore(Instant.parse(alert.scheduledAt))) return AlertDecision.suppressed(AlertSuppression.NOT_DUE)
        if (deliveryCount >= alert.repeatPolicy.maxDeliveries) {
            return AlertDecision.suppressed(AlertSuppression.DELIVERY_LIMIT)
        }
        profile.pausedUntil?.let {
            if (now.isBefore(Instant.parse(it))) return AlertDecision.suppressed(AlertSuppression.PAUSED)
        }
        profile.quietHours?.let {
            if (it.contains(now.atZone(zoneId).toLocalTime())) {
                return AlertDecision.suppressed(AlertSuppression.QUIET_HOURS)
            }
        }
        lastAlertDeliveryAt?.let {
            if (Duration.between(it, now) < Duration.ofMinutes(alert.repeatPolicy.minimumIntervalMinutes.toLong())) {
                return AlertDecision.suppressed(AlertSuppression.REPEAT_INTERVAL)
            }
        }
        if (sensoryDeliveryActive) return AlertDecision.suppressed(AlertSuppression.SENSORY_OVERLAP)
        lastSensoryDeliveryAt?.let {
            if (Duration.between(it, now) < Duration.ofMinutes(profile.cooldownMinutes.toLong())) {
                return AlertDecision.suppressed(AlertSuppression.COOLDOWN)
            }
        }
        val channels = alert.allowedChannels intersect profile.enabledChannels
        if (channels.isEmpty()) return AlertDecision.suppressed(AlertSuppression.NO_ALLOWED_CHANNEL)
        return AlertDecision(true, channels, null)
    }
}

data class AlertDecision(
    val shouldDeliver: Boolean,
    val channels: Set<SensoryChannel>,
    val suppression: AlertSuppression?,
) {
    companion object {
        fun suppressed(reason: AlertSuppression) = AlertDecision(false, emptySet(), reason)
    }
}

enum class AlertSuppression {
    GLOBAL_DISABLED,
    NOT_DUE,
    EXPIRED,
    DELIVERY_LIMIT,
    PAUSED,
    QUIET_HOURS,
    REPEAT_INTERVAL,
    SENSORY_OVERLAP,
    COOLDOWN,
    NO_ALLOWED_CHANNEL,
}
