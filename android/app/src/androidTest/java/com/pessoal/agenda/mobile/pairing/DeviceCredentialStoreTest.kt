package com.pessoal.agenda.mobile.pairing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyFactory
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceCredentialStoreTest {
    private lateinit var store: DeviceCredentialStore

    @Before
    fun setUp() {
        store = DeviceCredentialStore(ApplicationProvider.getApplicationContext())
        store.clearPairing()
    }

    @After
    fun tearDown() = store.clearPairing()

    @Test
    fun credentialRoundTripsThroughNonExportableKeystoreKeys() {
        val credential = ByteArray(32) { (it + 1).toByte() }
        val encrypted = encryptForStore(credential)
        val desktopId = "10000000-0000-4000-8000-000000000002"

        store.storeEncryptedCredential(encrypted, desktopId, setOf("TASKS_READ", "CAPTURES_WRITE"))
        store.storeServerConnection(
            "https://192.0.2.10:45182/api/v1/pair/requests",
            "ab".repeat(32),
        )
        val restored = store.credential()

        assertArrayEquals(credential, restored)
        assertEquals(desktopId, store.pairedDesktopId())
        assertEquals(setOf("TASKS_READ", "CAPTURES_WRITE"), store.grantedRoles())
        assertEquals("https://192.0.2.10:45182/api/v1/sync", store.syncBaseUrl())
        assertEquals("ab".repeat(32), store.tlsFingerprint())
        assertNotEquals(Base64.getEncoder().encodeToString(credential), encrypted)
        restored.fill(0)
    }

    @Test
    fun clearingPairingRemovesCredentialButKeepsStableDeviceId() {
        val deviceId = store.deviceId
        store.storeEncryptedCredential(
            encryptForStore(ByteArray(32) { 7 }),
            "10000000-0000-4000-8000-000000000002",
            setOf("TASKS_READ"),
        )

        store.clearPairing()

        assertNull(store.pairedDesktopId())
        assertEquals(emptySet<String>(), store.grantedRoles())
        assertNull(store.syncBaseUrl())
        assertNull(store.tlsFingerprint())
        assertEquals(deviceId, store.deviceId)
    }

    @Test
    fun keysAreNonExportableAndPreferencesDoNotContainPlainCredential() {
        val credential = ByteArray(32) { (it + 31).toByte() }
        store.storeEncryptedCredential(
            encryptForStore(credential),
            "10000000-0000-4000-8000-000000000002",
            setOf("TASKS_READ"),
        )

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(keyStore.getKey("agenda_pairing_rsa_v1", null).encoded)
        assertNull(keyStore.getKey("agenda_pairing_credential_v1", null).encoded)

        val plain = Base64.getEncoder().encodeToString(credential)
        val preferences = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("pairing_private_v1", android.content.Context.MODE_PRIVATE)
        assertFalse(preferences.all.values.any { it.toString().contains(plain) })
        assertTrue(keyStore.containsAlias("agenda_pairing_rsa_v1"))
        assertTrue(keyStore.containsAlias("agenda_pairing_credential_v1"))
        credential.fill(0)
    }

    private fun encryptForStore(credential: ByteArray): String {
        val encoded = Base64.getUrlDecoder().decode(store.publicKeyBase64Url())
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
