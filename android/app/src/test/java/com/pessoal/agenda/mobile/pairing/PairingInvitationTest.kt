package com.pessoal.agenda.mobile.pairing

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingInvitationTest {
    private val now = Instant.parse("2026-08-31T20:00:00Z")

    @Test
    fun parsesStrictValidInvitation() {
        val invitation = PairingInvitation.parse(validInvitation(), now)

        assertEquals(1, invitation.version)
        assertEquals("192.0.2.10", invitation.endpoint.host)
        assertEquals(45181, invitation.endpoint.port)
        assertEquals("2026-08-31T20:04:00Z", invitation.expiresAt.toString())
    }

    @Test
    fun rejectsExpiredOrExcessivelyLongInvitation() {
        assertInvalid(validInvitation(expiresAt = "2026-08-31T20:00:00Z"))
        assertInvalid(validInvitation(expiresAt = "2026-08-31T20:05:01Z"))
    }

    @Test
    fun rejectsUnknownDuplicateAndMissingFields() {
        assertInvalid(validInvitation() + "&extra=1")
        assertInvalid(validInvitation() + "&nonce=${"B".repeat(43)}")
        assertInvalid(validInvitation().replace(Regex("&fingerprint=[^&]+"), ""))
    }

    @Test
    fun rejectsEndpointDowngradeAndEndpointSmuggling() {
        assertInvalid(validInvitation(endpoint = "http://192.0.2.10:45181/api/v1/pair/requests"))
        assertInvalid(validInvitation(endpoint = "https://user@192.0.2.10:45181/api/v1/pair/requests"))
        assertInvalid(validInvitation(endpoint = "https://192.0.2.10:45181/api/v1/pair/requests?next=evil"))
    }

    @Test
    fun exposesOnlyGenericValidationFailure() {
        val error = assertThrows(PairingInvitationException::class.java) {
            PairingInvitation.parse("agenda://pair?v=broken", now)
        }
        assertEquals("Convite de pareamento inválido ou expirado.", error.message)
    }

    private fun assertInvalid(raw: String) {
        assertThrows(PairingInvitationException::class.java) { PairingInvitation.parse(raw, now) }
    }

    private fun validInvitation(
        endpoint: String = "https://192.0.2.10:45181/api/v1/pair/requests",
        expiresAt: String = "2026-08-31T20:04:00Z",
    ): String = "agenda://pair?v=1" +
        "&session_id=10000000-0000-4000-8000-000000000001" +
        "&desktop_id=10000000-0000-4000-8000-000000000002" +
        "&endpoint=${encode(endpoint)}" +
        "&expires_at=${encode(expiresAt)}" +
        "&nonce=${"A".repeat(43)}" +
        "&fingerprint=${"a".repeat(64)}"

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
