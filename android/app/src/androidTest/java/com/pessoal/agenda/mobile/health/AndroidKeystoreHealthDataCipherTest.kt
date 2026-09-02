package com.pessoal.agenda.mobile.health

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreHealthDataCipherTest {
    @Test
    fun ciphertextRoundTripsAndIsBoundToRecordRevision() {
        val cipher = AndroidKeystoreHealthDataCipher()
        val plaintext = "Registro ficticio sem dado pessoal".encodeToByteArray()
        val aad = "health-v1:a3000000-0000-4000-8000-000000000001:1".encodeToByteArray()

        val encrypted = cipher.encrypt(plaintext, aad)

        assertFalse(encrypted.ciphertext.contains("Registro ficticio"))
        assertTrue(cipher.decrypt(encrypted, aad).contentEquals(plaintext))
        assertTrue(runCatching { cipher.decrypt(encrypted, "revision:2".encodeToByteArray()) }.isFailure)
    }
}
