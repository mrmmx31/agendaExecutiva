package com.pessoal.agenda.mobile.sync

import android.annotation.SuppressLint
import java.net.URI
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

internal object PinnedHttpsConnectionFactory {
    @SuppressLint("CustomX509TrustManager")
    fun open(endpoint: URI, expectedHex: String): HttpsURLConnection {
        require(expectedHex.matches(Regex("[0-9a-f]{64}"))) { "Impressão digital inválida." }
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
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return (endpoint.toURL().openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = context.socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            connectTimeout = 8_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
    }
}
