package com.pessoal.agenda.mobile.pairing

import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PairingInvitation(
    val version: Int,
    val sessionId: UUID,
    val desktopId: UUID,
    val endpoint: URI,
    val expiresAt: Instant,
    val nonce: String,
    val certificateFingerprint: String,
) {
    companion object {
        private const val CONTRACT_VERSION = 2
        private val expectedFields = setOf(
            "v", "session_id", "desktop_id", "endpoint",
            "expires_at", "nonce", "fingerprint",
        )
        private val noncePattern = Regex("[A-Za-z0-9_-]{43,128}")
        private val fingerprintPattern = Regex("[0-9a-f]{64}")

        fun parse(raw: String, now: Instant = Instant.now()): PairingInvitation {
            val invitation = runCatching { URI(raw.trim()) }.getOrElse { invalid() }
            if (invitation.scheme != "agenda" || invitation.host != "pair" || invitation.fragment != null) invalid()
            val fields = parseQuery(invitation.rawQuery ?: invalid())
            if (fields.keys != expectedFields) invalid()

            val version = fields.getValue("v").toIntOrNull() ?: invalid()
            if (version !in 1..CONTRACT_VERSION) invalid()
            val expiresAt = runCatching { Instant.parse(fields.getValue("expires_at")) }.getOrElse { invalid() }
            val remaining = Duration.between(now, expiresAt)
            if (remaining.isZero || remaining.isNegative || remaining > Duration.ofMinutes(5)) invalid()

            val endpoint = parseEndpoint(fields.getValue("endpoint"))
            val nonce = fields.getValue("nonce")
            val fingerprint = fields.getValue("fingerprint")
            if (!noncePattern.matches(nonce) || !fingerprintPattern.matches(fingerprint)) invalid()

            return PairingInvitation(
                version,
                parseUuid(fields.getValue("session_id")),
                parseUuid(fields.getValue("desktop_id")),
                endpoint,
                expiresAt,
                nonce,
                fingerprint,
            )
        }

        private fun parseQuery(rawQuery: String): Map<String, String> {
            val result = linkedMapOf<String, String>()
            rawQuery.split('&').forEach { item ->
                val parts = item.split('=', limit = 2)
                if (parts.size != 2) invalid()
                val key = decode(parts[0])
                val value = decode(parts[1])
                if (key.isBlank() || value.isBlank() || result.put(key, value) != null) invalid()
            }
            return result
        }

        private fun parseEndpoint(raw: String): URI {
            val endpoint = runCatching { URI(raw) }.getOrElse { invalid() }
            if (endpoint.scheme != "https" || endpoint.host.isNullOrBlank()) invalid()
            if (endpoint.userInfo != null || endpoint.query != null || endpoint.fragment != null) invalid()
            if (endpoint.path != "/api/v1/pair/requests" || endpoint.port !in 1..65535) invalid()
            return endpoint
        }

        private fun parseUuid(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrElse { invalid() }

        private fun decode(raw: String): String = runCatching {
            @Suppress("DEPRECATION")
            URLDecoder.decode(raw, "UTF-8")
        }.getOrElse { invalid() }

        private fun invalid(): Nothing = throw PairingInvitationException("Convite de pareamento inválido ou expirado.")
    }
}

class PairingInvitationException(message: String) : IllegalArgumentException(message)
