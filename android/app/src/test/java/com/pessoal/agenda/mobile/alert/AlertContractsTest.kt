package com.pessoal.agenda.mobile.alert

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertContractsTest {
    private val now = Instant.parse("2026-09-01T14:00:00Z")
    private val policy = AlertPolicy(ZoneId.of("America/Manaus"))

    @Test
    fun installationStartsWithoutSensoryOutput() {
        val profile = SensoryProfile.installationDefault()

        profile.validate()

        assertFalse(profile.globalEnabled)
        assertEquals(setOf(SensoryChannel.VISUAL), profile.enabledChannels)
        assertEquals(AlertSuppression.GLOBAL_DISABLED, policy.evaluate(alert(), profile, now).suppression)
    }

    @Test
    fun eligibleAlertUsesOnlyIntersectionOfConsentedChannels() {
        val profile = enabledProfile(
            channels = setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION),
        )

        val result = policy.evaluate(alert(), profile, now)

        assertTrue(result.shouldDeliver)
        assertEquals(setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION), result.channels)
    }

    @Test
    fun quietHoursAcrossMidnightSuppressAllChannels() {
        val late = Instant.parse("2026-09-02T03:00:00Z") // 23:00 em Manaus

        val result = policy.evaluate(
            alert(
                scheduledAt = "2026-09-02T02:00:00Z",
                validUntil = "2026-09-02T06:00:00Z",
            ),
            enabledProfile(),
            late,
        )

        assertEquals(AlertSuppression.QUIET_HOURS, result.suppression)
    }

    @Test
    fun pauseCooldownOverlapAndDeliveryLimitAreIndependentBarriers() {
        assertEquals(
            AlertSuppression.PAUSED,
            policy.evaluate(alert(), enabledProfile(pausedUntil = "2026-09-01T14:30:00Z"), now).suppression,
        )
        assertEquals(
            AlertSuppression.REPEAT_INTERVAL,
            policy.evaluate(alert(), enabledProfile(), now, lastAlertDeliveryAt = now.minusSeconds(10 * 60)).suppression,
        )
        assertEquals(
            AlertSuppression.COOLDOWN,
            policy.evaluate(alert(), enabledProfile(), now, lastSensoryDeliveryAt = now.minusSeconds(60)).suppression,
        )
        assertEquals(
            AlertSuppression.SENSORY_OVERLAP,
            policy.evaluate(alert(), enabledProfile(), now, sensoryDeliveryActive = true).suppression,
        )
        assertEquals(
            AlertSuppression.DELIVERY_LIMIT,
            policy.evaluate(alert(), enabledProfile(), now, deliveryCount = 2).suppression,
        )
    }

    @Test
    fun completeAndSnoozeCommandsHaveStrictTemporalShapes() {
        val snoozePolicy = SnoozePolicy.cautiousDefault().also(SnoozePolicy::validate)
        action(AlertActionType.COMPLETE, null).validate(snoozePolicy)
        action(AlertActionType.SNOOZE, "2026-09-01T14:30:00Z").validate(snoozePolicy)

        assertFails { action(AlertActionType.COMPLETE, "2026-09-01T14:30:00Z").validate(snoozePolicy) }
        assertFails { action(AlertActionType.SNOOZE, "2026-09-01T14:01:00Z").validate(snoozePolicy) }
        assertFails { action(AlertActionType.SNOOZE, null).validate(snoozePolicy) }
    }

    @Test
    fun malformedDefinitionsAndAggressivePoliciesAreRejected() {
        assertFails { alert(text = " ").validate() }
        assertFails { alert(validUntil = "2026-09-10T14:00:00Z").validate() }
        assertFails { AlertRepeatPolicy(6, 1).validate() }
        assertFails { SnoozePolicy(listOf(1, 2, 3), 1, 10_000, 20).validate() }
    }

    private fun alert(
        text: String = "Revisar a tarefa atual",
        scheduledAt: String = "2026-09-01T13:55:00Z",
        validUntil: String = "2026-09-01T18:00:00Z",
    ) = AlertDefinition(
        contractVersion = 1,
        alertId = "10000000-0000-4000-8000-000000000001",
        origin = AlertOrigin.TASK,
        referenceId = "10000000-0000-4000-8000-000000000002",
        text = text,
        reason = "Horário planejado da tarefa",
        sourceDeviceId = "10000000-0000-4000-8000-000000000003",
        scheduledAt = scheduledAt,
        validUntil = validUntil,
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = SensoryChannel.entries.toSet(),
        repeatPolicy = AlertRepeatPolicy(2, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private fun enabledProfile(
        channels: Set<SensoryChannel> = SensoryChannel.entries.toSet(),
        pausedUntil: String? = null,
    ) = SensoryProfile(
        contractVersion = 1,
        globalEnabled = true,
        enabledChannels = channels,
        quietHours = QuietHours("22:30", "07:00"),
        pausedUntil = pausedUntil,
        cooldownMinutes = 5,
        audioRoute = AudioRoutePolicy.SYSTEM_DEFAULT,
    )

    private fun action(type: AlertActionType, snoozeUntil: String?) = AlertActionCommand(
        contractVersion = 1,
        operationId = "20000000-0000-4000-8000-000000000001",
        alertId = "10000000-0000-4000-8000-000000000001",
        sourceDeviceId = "10000000-0000-4000-8000-000000000003",
        action = type,
        occurredAt = now.toString(),
        snoozeUntil = snoozeUntil,
    )

    private fun assertFails(action: () -> Unit) {
        assertTrue(runCatching(action).isFailure)
    }
}
