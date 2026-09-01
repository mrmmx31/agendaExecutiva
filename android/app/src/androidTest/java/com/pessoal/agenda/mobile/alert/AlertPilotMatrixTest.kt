package com.pessoal.agenda.mobile.alert

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pessoal.agenda.mobile.alert.output.AndroidSensoryOutput
import com.pessoal.agenda.mobile.alert.output.SensoryOutputGate
import com.pessoal.agenda.mobile.data.AlertDeliveryOutcome
import com.pessoal.agenda.mobile.data.AlertDeliveryReason
import com.pessoal.agenda.mobile.data.AlertDeliveryRecord
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.AlertWorkEvaluation
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertPilotMatrixTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun quietHoursRescheduleWithoutDeliveringAnyChannel() = withStore(QUIET_NOW) { database, store ->
        val initial = store.ensureInstallationProfile()
        store.saveProfile(
            initial.profile.copy(
                globalEnabled = true,
                enabledChannels = SensoryChannel.entries.toSet(),
                quietHours = QuietHours("22:30", "07:00"),
            ),
            initial.snoozePolicy,
        )
        val alert = alert(
            scheduledAt = QUIET_NOW.minusSeconds(60),
            validUntil = QUIET_NOW.plusSeconds(12 * 60 * 60),
            channels = SensoryChannel.entries.toSet(),
        )
        store.materialize(alert)

        val result = store.evaluateForWork(alert.alertId, QUIET_NOW, ZoneId.of("America/Manaus"))

        assertEquals(
            AlertWorkEvaluation.Reschedule(
                Instant.parse("2026-09-02T11:00:00Z"),
                AlertSuppression.QUIET_HOURS,
            ),
            result,
        )
        assertEquals(0, database.offline().alertDeliveryCount())
    }

    @Test
    fun sensoryCooldownUsesLastDeliveredChannelAcrossAlerts() = withStore(NOW) { database, store ->
        val initial = store.ensureInstallationProfile()
        store.saveProfile(
            initial.profile.copy(
                globalEnabled = true,
                enabledChannels = setOf(SensoryChannel.PHONE_VIBRATION),
                quietHours = null,
                cooldownMinutes = 5,
            ),
            initial.snoozePolicy,
        )
        val first = alert(scheduledAt = NOW.minusSeconds(120), validUntil = NOW.plusSeconds(3_600))
        val second = alert(scheduledAt = NOW.minusSeconds(60), validUntil = NOW.plusSeconds(3_600))
        store.materialize(first)
        store.materialize(second)
        store.recordDelivery(
            AlertDeliveryRecord(
                deliveryId = UUID.randomUUID().toString(),
                alertId = first.alertId,
                deviceId = DEVICE_ID,
                channels = setOf(SensoryChannel.PHONE_VIBRATION),
                outcome = AlertDeliveryOutcome.DELIVERED,
                technicalReason = null,
                attemptedAt = NOW.minusSeconds(60).toString(),
            ),
        )

        val result = store.evaluateForWork(second.alertId, NOW, ZoneOffset.UTC)

        assertEquals(
            AlertWorkEvaluation.Reschedule(NOW.plusSeconds(4 * 60), AlertSuppression.COOLDOWN),
            result,
        )
        assertEquals(1, database.offline().alertDeliveryCount())
    }

    @Test
    fun occupiedSensoryGateRejectsAudioTestWithoutPlaying() = runBlocking {
        val gate = SensoryOutputGate()
        assertTrue(gate.tryAcquire())
        try {
            val result = AndroidSensoryOutput(context, gate).testTone(AudioRoutePolicy.SYSTEM_DEFAULT)

            assertEquals(emptySet<SensoryChannel>(), result.deliveredChannels)
            assertEquals(AlertDeliveryReason.SENSORY_OVERLAP, result.reason)
        } finally {
            gate.release()
        }
    }

    private fun withStore(
        now: Instant,
        block: suspend (MobileDatabase, AlertStore) -> Unit,
    ) = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, MobileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            block(database, AlertStore(database, Clock.fixed(now, ZoneOffset.UTC)))
        } finally {
            database.close()
        }
    }

    private fun alert(
        scheduledAt: Instant,
        validUntil: Instant,
        channels: Set<SensoryChannel> = setOf(SensoryChannel.PHONE_VIBRATION),
    ) = AlertDefinition(
        contractVersion = AlertDefinition.CONTRACT_VERSION,
        alertId = UUID.randomUUID().toString(),
        origin = AlertOrigin.MANUAL,
        referenceId = null,
        text = "Alerta fictício da matriz",
        reason = "Validação automatizada no AVD",
        sourceDeviceId = DEVICE_ID,
        scheduledAt = scheduledAt.toString(),
        validUntil = validUntil.toString(),
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = channels,
        repeatPolicy = AlertRepeatPolicy(1, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T14:00:00Z")
        val QUIET_NOW: Instant = Instant.parse("2026-09-02T03:00:00Z")
        const val DEVICE_ID = "90000000-0000-4000-8000-000000000001"
    }
}
