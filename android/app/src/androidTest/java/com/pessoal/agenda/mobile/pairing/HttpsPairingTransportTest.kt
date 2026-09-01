package com.pessoal.agenda.mobile.pairing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpsPairingTransportTest {
    private lateinit var store: DeviceCredentialStore

    @Before
    fun setUp() {
        store = DeviceCredentialStore(ApplicationProvider.getApplicationContext())
        store.clearPairing()
    }

    @After
    fun tearDown() = store.clearPairing()

    @Test
    fun pinnedHttpsCompletesAndReconnectsWithNewSession() = runBlocking {
        val firstCredential = ByteArray(32) { 11 }
        val first = PairingServer(DESKTOP_ONE, store.deviceId, firstCredential)
        first.use {
            PairingClient(store, HttpsPairingTransport(), fixedClock(), pause = {})
                .pair(it.invitation(), "123456")
        }
        assertEquals(DESKTOP_ONE, store.pairedDesktopId())
        val firstRestored = store.credential()
        assertArrayEquals(firstCredential, firstRestored)
        firstRestored.fill(0)

        val secondCredential = ByteArray(32) { 22 }
        val second = PairingServer(DESKTOP_TWO, store.deviceId, secondCredential)
        second.use {
            PairingClient(store, HttpsPairingTransport(), fixedClock(), pause = {})
                .pair(it.invitation(), "654321")
        }
        assertEquals(DESKTOP_TWO, store.pairedDesktopId())
        val restored = store.credential()
        assertArrayEquals(secondCredential, restored)
        restored.fill(0)
    }

    @Test
    fun incorrectCertificateNeverStoresCredential() {
        PairingServer(DESKTOP_ONE, store.deviceId, ByteArray(32) { 7 }).use { server ->
            val error = assertThrows(PairingException::class.java) {
                runBlocking {
                    PairingClient(store, HttpsPairingTransport(), fixedClock(), pause = {})
                        .pair(server.invitation(fingerprint = "00".repeat(32)), "123456")
                }
            }
            assertEquals("Não foi possível alcançar o desktop.", error.message)
            assertEquals(null, store.pairedDesktopId())
        }
    }

    private class PairingServer(
        private val desktopId: String,
        private val deviceId: String,
        private val credential: ByteArray,
    ) : AutoCloseable {
        private val json = Json { explicitNulls = true }
        private val certificate = HeldCertificate.Builder()
            .commonName("Agenda pairing test")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        private val server = MockWebServer()
        private val requestId = UUID.randomUUID().toString()
        private lateinit var publicKey: String

        init {
            val certificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
            server.useHttps(certificates.sslSocketFactory(), false)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when {
                        request.path == "/api/v1/pair/requests" -> pending(request)
                        request.path == "/api/v1/pair/requests/$requestId/complete" -> approved(request)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()
        }

        fun invitation(fingerprint: String = fingerprint()): String {
            val endpoint = "https://127.0.0.1:${server.port}/api/v1/pair/requests"
            return "agenda://pair?v=1" +
                "&session_id=$SESSION_ID" +
                "&desktop_id=$desktopId" +
                "&endpoint=${URLEncoder.encode(endpoint, "UTF-8")}" +
                "&expires_at=2026-09-01T12%3A05%3A00Z" +
                "&nonce=${"A".repeat(43)}" +
                "&fingerprint=$fingerprint"
        }

        private fun pending(request: RecordedRequest): MockResponse {
            val received = json.decodeFromString<PairingRequest>(request.body.readUtf8())
            require(received.deviceId == deviceId)
            publicKey = received.devicePublicKey
            val response = PairingResponse(
                requestId, "PENDING", 1, "B".repeat(43), null, null, null, null, emptySet(),
            )
            return json(response, 202)
        }

        private fun approved(request: RecordedRequest): MockResponse {
            require(request.getHeader("Authorization") == "Pairing ${"B".repeat(43)}")
            val response = PairingResponse(
                requestId, "APPROVED", null, null, deviceId, 1, 1,
                encrypt(publicKey, credential), setOf("TASKS_READ", "CAPTURES_WRITE", "PROTOCOLS_EXECUTE"),
            )
            return json(response, 200)
        }

        private fun fingerprint(): String = certificate.certificate.encoded
            .let { MessageDigest.getInstance("SHA-256").digest(it) }
            .joinToString("") { "%02x".format(it) }

        private fun json(response: PairingResponse, status: Int) = MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(json.encodeToString(response))

        override fun close() = server.shutdown()
    }

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneId.of("UTC"))

    private companion object {
        const val SESSION_ID = "10000000-0000-4000-8000-000000000001"
        const val DESKTOP_ONE = "10000000-0000-4000-8000-000000000002"
        const val DESKTOP_TWO = "20000000-0000-4000-8000-000000000002"

        fun encrypt(encodedPublicKey: String, credential: ByteArray): String {
            val encoded = Base64.getUrlDecoder().decode(encodedPublicKey)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT),
            )
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(credential))
        }
    }
}
