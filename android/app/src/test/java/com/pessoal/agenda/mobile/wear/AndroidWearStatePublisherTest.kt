package com.pessoal.agenda.mobile.wear

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.alert.AlertActionCommand
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.wear.contract.WearAlertStatus
import com.pessoal.agenda.wear.contract.WearContractCodec
import com.pessoal.agenda.wear.contract.WearDataPaths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidWearStatePublisherTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: AlertStore
    private lateinit var client: RecordingClient
    private val now = Instant.parse("2026-09-02T12:00:00Z")

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = AlertStore(database, Clock.fixed(now, ZoneOffset.UTC))
        store.ensureInstallationProfile()
        client = RecordingClient()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun publisherWritesClosedStateAtCanonicalPath() = runBlocking {
        store.materialize(alert())

        assertEquals(WearStatePublishResult.STORED, AndroidWearStatePublisher(store, client).publish(ALERT_ID))

        assertEquals(WearDataPaths.alertState(ALERT_ID), client.path)
        val state = WearContractCodec.decodeState(requireNotNull(client.payload))
        assertEquals(1, state.revision)
        assertEquals(WearAlertStatus.PENDING, state.status)
        assertEquals(listOf(10, 30, 60), state.snoozeOptionsMinutes)
        assertNull(state.acknowledgedOperationId)
    }

    @Test
    fun actionProducesMonotonicRevisionAndExplicitAcknowledgement() = runBlocking {
        store.materialize(alert())
        store.recordAction(
            AlertActionCommand(
                contractVersion = 1,
                operationId = OPERATION_ID,
                alertId = ALERT_ID,
                sourceDeviceId = WEAR_DEVICE_ID,
                action = AlertActionType.COMPLETE,
                occurredAt = now.toString(),
                snoozeUntil = null,
            ),
            store.ensureInstallationProfile().snoozePolicy,
        )

        AndroidWearStatePublisher(store, client).publish(ALERT_ID)

        val state = WearContractCodec.decodeState(requireNotNull(client.payload))
        assertEquals(2, state.revision)
        assertEquals(WearAlertStatus.COMPLETED, state.status)
        assertEquals(OPERATION_ID, state.acknowledgedOperationId)
    }

    @Test
    fun transportFailureIsNeutralAndClassified() = runBlocking {
        store.materialize(alert())
        client.failure = IllegalStateException("offline")

        assertEquals(
            WearStatePublishResult.UNAVAILABLE,
            AndroidWearStatePublisher(store, client).publish(ALERT_ID),
        )
    }

    private fun alert() = AlertDefinition(
        contractVersion = 1,
        alertId = ALERT_ID,
        origin = AlertOrigin.TASK,
        referenceId = REFERENCE_ID,
        text = "Teste pareado P2-05",
        reason = "Fixture sem dado pessoal",
        sourceDeviceId = PHONE_DEVICE_ID,
        scheduledAt = "2026-09-02T11:55:00Z",
        validUntil = "2026-09-02T18:00:00Z",
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = setOf(SensoryChannel.VISUAL),
        repeatPolicy = AlertRepeatPolicy(2, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )

    private class RecordingClient : WearStateDataClient {
        var path: String? = null
        var payload: ByteArray? = null
        var failure: Exception? = null
        override suspend fun put(path: String, payload: ByteArray) {
            failure?.let { throw it }
            this.path = path
            this.payload = payload
        }
    }

    private companion object {
        const val ALERT_ID = "81000000-0000-4000-8000-000000000001"
        const val REFERENCE_ID = "81000000-0000-4000-8000-000000000002"
        const val PHONE_DEVICE_ID = "81000000-0000-4000-8000-000000000003"
        const val WEAR_DEVICE_ID = "81000000-0000-4000-8000-000000000004"
        const val OPERATION_ID = "81000000-0000-4000-8000-000000000005"
    }
}
