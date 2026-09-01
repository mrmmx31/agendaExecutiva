package com.pessoal.agenda.mobile.sync

import android.annotation.SuppressLint
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HttpsSyncTransport(private val credentials: DeviceCredentialStore) : SyncTransport {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    override suspend fun push(batch: SyncBatch): SyncBatchResponse = withContext(Dispatchers.IO) {
        request("/batches", "POST", json.encodeToString(batch))
    }

    override suspend fun snapshot(pageToken: String?): SnapshotPage = withContext(Dispatchers.IO) {
        val suffix = pageToken?.let {
            "?page_token=" + URLEncoder.encode(it, "UTF-8")
        }.orEmpty()
        request("/snapshot$suffix", "GET", null)
    }

    private inline fun <reified T> request(path: String, method: String, body: String?): T {
        val base = credentials.syncBaseUrl() ?: throw SyncTransportException("Telefone não pareado.")
        val fingerprint = credentials.tlsFingerprint()
            ?: throw SyncTransportException("Certificado do desktop ausente.")
        val credential = credentials.credential()
        try {
            val connection = java.net.URI.create(base + path).toURL().openConnection()
                    as HttpsURLConnection
            connection.sslSocketFactory = pinnedContext(fingerprint).socketFactory
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Agenda-Device", credentials.deviceId)
            connection.setRequestProperty(
                "Authorization",
                "AgendaCredential " + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(credential),
            )
            if (body != null) {
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                require(bytes.size <= MAX_REQUEST_BYTES) { "Lote excede o limite local." }
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= MAX_RESPONSE_BYTES) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            if (bytes.size > MAX_RESPONSE_BYTES) throw SyncTransportException("Resposta excede o limite.")
            if (status !in 200..299) throw SyncTransportException(
                when (status) {
                    401, 403 -> "Pareamento recusado pelo desktop."
                    409 -> "Sessão de sincronização encerrada."
                    else -> "Desktop recusou a sincronização."
                },
            )
            return json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))
        } catch (error: SyncTransportException) {
            throw error
        } catch (error: Exception) {
            throw SyncTransportException("Não foi possível alcançar o desktop.", error)
        } finally {
            credential.fill(0)
        }
    }

    @SuppressLint("CustomX509TrustManager")
    private fun pinnedContext(expectedHex: String): SSLContext {
        val expected = expectedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("Certificado ausente")
                val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw CertificateException("Certificado não corresponde ao convite")
                }
            }
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 256 * 1024
        const val MAX_RESPONSE_BYTES = 1024 * 1024
    }
}

class SyncTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
