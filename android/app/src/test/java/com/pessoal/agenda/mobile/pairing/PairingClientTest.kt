package com.pessoal.agenda.mobile.pairing

import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingClientTest {
    @Test
    fun approvedResponsePersistsCompleteConnection() = runBlocking {
        val store = FakeStore()
        val transport = FakeTransport(
            listOf(pending(), approved(store.deviceId)),
        )

        PairingClient(store, transport, fixedClock(), pause = {}).pair(invitation(), "123456")

        assertEquals(DESKTOP_ID, store.desktopId)
        assertEquals("encrypted", store.encryptedCredential)
        assertEquals(ENDPOINT, store.endpoint)
        assertEquals(setOf("TASKS_READ"), store.roles)
        assertEquals(1, transport.completed)
    }

    @Test
    fun malformedApprovedResponseIsNotPersisted() {
        val store = FakeStore()
        val transport = FakeTransport(
            listOf(pending(), approved("wrong-device")),
        )

        assertThrows(PairingException::class.java) {
            runBlocking { PairingClient(store, transport, fixedClock(), pause = {}).pair(invitation(), "123456") }
        }
        assertEquals(null, store.desktopId)
    }

    @Test
    fun expirationStopsBeforeAnotherPoll() {
        val store = FakeStore()
        val clock = MutableClock(Instant.parse("2026-09-01T12:00:00Z"))
        val transport = FakeTransport(listOf(pending()))

        val error = assertThrows(PairingException::class.java) {
            runBlocking {
                PairingClient(store, transport, clock) { clock.now = clock.now.plusSeconds(301) }
                    .pair(invitation(), "123456")
            }
        }

        assertTrue(error.message!!.contains("expirou"))
        assertEquals(0, transport.completed)
    }

    @Test
    fun cancelDisconnectsActiveTransport() {
        val transport = FakeTransport(emptyList())
        PairingClient(FakeStore(), transport).cancel()
        assertTrue(transport.cancelled)
    }

    private class FakeStore : PairingCredentialStore {
        override val deviceId = DEVICE_ID
        override val deviceName = "Android teste"
        var encryptedCredential: String? = null
        var desktopId: String? = null
        var roles: Set<String>? = null
        var endpoint: String? = null
        override fun publicKeyBase64Url(): String = "A".repeat(342)
        override fun completePairing(
            encryptedCredential: String,
            desktopId: String,
            grantedRoles: Set<String>,
            pairingEndpoint: String,
            fingerprint: String,
        ) {
            this.encryptedCredential = encryptedCredential
            this.desktopId = desktopId
            roles = grantedRoles
            endpoint = pairingEndpoint
        }
    }

    private class FakeTransport(private val responses: List<PairingResponse>) : PairingTransport {
        var completed = 0
        var cancelled = false
        override suspend fun submit(invitation: PairingInvitation, request: PairingRequest) = responses.first()
        override suspend fun complete(invitation: PairingInvitation, requestId: String, token: String): PairingResponse {
            completed++
            return responses[completed]
        }
        override fun cancel() { cancelled = true }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private fun pending() = PairingResponse(
        REQUEST_ID, "PENDING", 1, "B".repeat(43), null, null, null, null, emptySet(),
    )

    private fun approved(deviceId: String) = PairingResponse(
        REQUEST_ID, "APPROVED", null, null, deviceId, 1, 1, "encrypted", setOf("TASKS_READ"),
    )

    private fun invitation(): String = "agenda://pair?v=1" +
        "&session_id=$SESSION_ID" +
        "&desktop_id=$DESKTOP_ID" +
        "&endpoint=${java.net.URLEncoder.encode(ENDPOINT, "UTF-8")}" +
        "&expires_at=2026-09-01T12%3A05%3A00Z" +
        "&nonce=${"A".repeat(43)}" +
        "&fingerprint=${"a".repeat(64)}"

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneId.of("UTC"))

    private companion object {
        const val DEVICE_ID = "10000000-0000-4000-8000-000000000003"
        const val DESKTOP_ID = "10000000-0000-4000-8000-000000000002"
        const val SESSION_ID = "10000000-0000-4000-8000-000000000001"
        const val REQUEST_ID = "10000000-0000-4000-8000-000000000004"
        const val ENDPOINT = "https://192.0.2.10:45181/api/v1/pair/requests"
    }
}
