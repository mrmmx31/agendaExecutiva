package com.pessoal.agenda.mobile.pairing

import com.pessoal.agenda.mobile.sync.PinnedHttpsConnectionFactory
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface PairingCredentialStore {
    val deviceId: String
    val deviceName: String
    fun publicKeyBase64Url(): String
    fun completePairing(
        encryptedCredential: String,
        desktopId: String,
        grantedRoles: Set<String>,
        pairingEndpoint: String,
        fingerprint: String,
    )
}

class PairingClient(
    private val credentials: PairingCredentialStore,
    private val transport: PairingTransport,
    private val clock: Clock = Clock.systemUTC(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun pair(rawInvitation: String, oneTimeCode: String) {
        require(oneTimeCode.matches(Regex("[0-9]{6}"))) { "Código deve conter seis dígitos." }
        val invitation = PairingInvitation.parse(rawInvitation, Instant.now(clock))
        val identity = withContext(Dispatchers.IO) {
            PairingIdentity(credentials.deviceId, credentials.deviceName, credentials.publicKeyBase64Url())
        }
        val request = PairingRequest(
            contractVersion = invitation.version,
            sessionId = invitation.sessionId.toString(),
            desktopId = invitation.desktopId.toString(),
            deviceId = identity.deviceId,
            deviceName = identity.deviceName,
            oneTimeCode = oneTimeCode,
            devicePublicKey = identity.publicKey,
            invitationNonce = invitation.nonce,
            requestedRoles = ALLOWED_ROLES,
        )
        var response = transport.submit(invitation, request)
        validatePending(response)
        val requestId = response.requestId
        val token = requireNotNull(response.completionToken)

        while (true) {
            if (!Instant.now(clock).isBefore(invitation.expiresAt)) {
                throw PairingException("O convite expirou. Gere outro no desktop.")
            }
            pause(requireNotNull(response.retryAfterSeconds).toLong() * 1_000)
            if (!Instant.now(clock).isBefore(invitation.expiresAt)) {
                throw PairingException("O convite expirou. Gere outro no desktop.")
            }
            response = transport.complete(invitation, requestId, token)
            when (response.status) {
                "PENDING" -> validatePending(response, requestId)
                "APPROVED" -> {
                    validateApproved(response, invitation, requestId)
                    withContext(Dispatchers.IO) {
                        credentials.completePairing(
                            requireNotNull(response.encryptedCredential),
                            invitation.desktopId.toString(),
                            response.grantedRoles,
                            invitation.endpoint.toString(),
                            invitation.certificateFingerprint,
                        )
                    }
                    return
                }
                "REJECTED" -> throw PairingException("Pareamento recusado pelo desktop.")
                "EXPIRED" -> throw PairingException("O convite expirou. Gere outro no desktop.")
                else -> throw PairingException("Resposta de pareamento inválida.")
            }
        }
    }

    fun cancel() = transport.cancel()

    private fun validatePending(response: PairingResponse, expectedRequestId: String? = null) {
        if (response.status != "PENDING" || response.retryAfterSeconds !in 1..10
            || response.completionToken?.matches(TOKEN_PATTERN) != true
            || response.deviceId != null || response.contractMin != null || response.contractMax != null
            || response.encryptedCredential != null || response.grantedRoles.isNotEmpty()
        ) throw PairingException("Resposta de pareamento inválida.")
        runCatching { UUID.fromString(response.requestId) }
            .getOrElse { throw PairingException("Resposta de pareamento inválida.") }
        if (expectedRequestId != null && response.requestId != expectedRequestId) {
            throw PairingException("Resposta de pareamento inválida.")
        }
    }

    private fun validateApproved(
        response: PairingResponse,
        invitation: PairingInvitation,
        expectedRequestId: String,
    ) {
        if (response.requestId != expectedRequestId || response.deviceId != credentials.deviceId
            || response.contractMin == null || response.contractMax == null
            || invitation.version !in response.contractMin..response.contractMax
            || response.retryAfterSeconds != null || response.completionToken != null
            || response.encryptedCredential.isNullOrBlank() || response.encryptedCredential.length > 2048
            || response.grantedRoles.isEmpty() || !ALLOWED_ROLES.containsAll(response.grantedRoles)
        ) throw PairingException("Resposta de pareamento inválida.")
    }

    private companion object {
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43,128}")
        val ALLOWED_ROLES = setOf("TASKS_READ", "CAPTURES_WRITE", "PROTOCOLS_EXECUTE")
    }

    private data class PairingIdentity(val deviceId: String, val deviceName: String, val publicKey: String)
}

interface PairingTransport {
    suspend fun submit(invitation: PairingInvitation, request: PairingRequest): PairingResponse
    suspend fun complete(invitation: PairingInvitation, requestId: String, token: String): PairingResponse
    fun cancel()
}

class HttpsPairingTransport : PairingTransport {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }
    private val active = AtomicReference<HttpsURLConnection?>()

    override suspend fun submit(
        invitation: PairingInvitation,
        request: PairingRequest,
    ): PairingResponse = request(invitation, invitation.endpoint, json.encodeToString(request), null)

    override suspend fun complete(
        invitation: PairingInvitation,
        requestId: String,
        token: String,
    ): PairingResponse = request(
        invitation,
        URI.create(invitation.endpoint.toString() + "/$requestId/complete"),
        "",
        "Pairing $token",
    )

    override fun cancel() {
        active.getAndSet(null)?.disconnect()
    }

    private suspend fun request(
        invitation: PairingInvitation,
        endpoint: URI,
        body: String,
        authorization: String?,
    ): PairingResponse = withContext(Dispatchers.IO) {
        val connection = PinnedHttpsConnectionFactory.open(
            endpoint,
            invitation.certificateFingerprint,
        )
        active.set(connection)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            authorization?.let { connection.setRequestProperty("Authorization", it) }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            require(bytes.size <= MAX_BODY_BYTES) { "Solicitação de pareamento excede o limite." }
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBytes = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= MAX_BODY_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            if (responseBytes.size > MAX_BODY_BYTES) throw PairingException("Resposta de pareamento inválida.")
            if (status !in 200..299) throw PairingException(
                if (status == 409) "Pareamento recusado ou expirado." else "Desktop recusou o pareamento.",
            )
            json.decodeFromString<PairingResponse>(responseBytes.toString(StandardCharsets.UTF_8))
        } catch (error: PairingException) {
            throw error
        } catch (error: Exception) {
            throw PairingException("Não foi possível alcançar o desktop.", error)
        } finally {
            active.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private companion object { const val MAX_BODY_BYTES = 32 * 1024 }
}

@Serializable
data class PairingRequest(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("session_id") val sessionId: String,
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("one_time_code") val oneTimeCode: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("invitation_nonce") val invitationNonce: String,
    @SerialName("requested_roles") val requestedRoles: Set<String>,
)

@Serializable
data class PairingResponse(
    @SerialName("request_id") val requestId: String,
    val status: String,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int?,
    @SerialName("completion_token") val completionToken: String?,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("contract_min") val contractMin: Int?,
    @SerialName("contract_max") val contractMax: Int?,
    @SerialName("encrypted_credential") val encryptedCredential: String?,
    @SerialName("granted_roles") val grantedRoles: Set<String>,
)

class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)
