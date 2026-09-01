package com.pessoal.agenda.mobile.pairing

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import java.net.URI
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class DeviceCredentialStore(context: Context) : PairingCredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override val deviceId: String
        get() = preferences.getString(DEVICE_ID, null)?.also(UUID::fromString)
            ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(DEVICE_ID, it).commit()
            }

    override val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .take(100)
            .ifBlank { "Android" }

    override fun publicKeyBase64Url(): String {
        ensureRsaKey()
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(requireNotNull(keyStore.getCertificate(RSA_ALIAS)).publicKey.encoded)
    }

    fun storeEncryptedCredential(
        encryptedCredential: String,
        desktopId: String,
        grantedRoles: Set<String>,
    ) {
        UUID.fromString(desktopId)
        require(grantedRoles.isNotEmpty() && ALLOWED_ROLES.containsAll(grantedRoles)) {
            "Papéis de pareamento inválidos."
        }
        ensureRsaKey()
        val privateKey = requireNotNull(keyStore.getKey(RSA_ALIAS, null))
        val rsa = Cipher.getInstance(RSA_TRANSFORMATION)
        rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        val credential = rsa.doFinal(Base64.getUrlDecoder().decode(encryptedCredential))
        require(credential.size == CREDENTIAL_BYTES) { "Credencial de pareamento inválida." }

        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, aesKey())
            val ciphertext = cipher.doFinal(credential)
            check(
                preferences.edit()
                    .putString(CREDENTIAL, Base64.getEncoder().encodeToString(ciphertext))
                    .putString(CREDENTIAL_IV, Base64.getEncoder().encodeToString(cipher.iv))
                    .putString(DESKTOP_ID, desktopId)
                    .putStringSet(GRANTED_ROLES, grantedRoles)
                    .commit(),
            ) { "Não foi possível persistir a credencial." }
        } finally {
            credential.fill(0)
        }
    }

    override fun completePairing(
        encryptedCredential: String,
        desktopId: String,
        grantedRoles: Set<String>,
        pairingEndpoint: String,
        fingerprint: String,
    ) {
        val syncBase = validatedSyncBase(pairingEndpoint, fingerprint)
        UUID.fromString(desktopId)
        require(grantedRoles.isNotEmpty() && ALLOWED_ROLES.containsAll(grantedRoles)) {
            "Papéis de pareamento inválidos."
        }
        ensureRsaKey()
        val privateKey = requireNotNull(keyStore.getKey(RSA_ALIAS, null))
        val rsa = Cipher.getInstance(RSA_TRANSFORMATION)
        rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        val credential = rsa.doFinal(Base64.getUrlDecoder().decode(encryptedCredential))
        require(credential.size == CREDENTIAL_BYTES) { "Credencial de pareamento inválida." }
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, aesKey())
            val ciphertext = cipher.doFinal(credential)
            check(
                preferences.edit()
                    .putString(CREDENTIAL, Base64.getEncoder().encodeToString(ciphertext))
                    .putString(CREDENTIAL_IV, Base64.getEncoder().encodeToString(cipher.iv))
                    .putString(DESKTOP_ID, desktopId)
                    .putStringSet(GRANTED_ROLES, grantedRoles)
                    .putString(SYNC_BASE, syncBase)
                    .putString(TLS_FINGERPRINT, fingerprint)
                    .commit(),
            ) { "Não foi possível persistir o pareamento." }
        } finally {
            credential.fill(0)
        }
    }

    fun storeServerConnection(pairingEndpoint: String, fingerprint: String) {
        val syncBase = validatedSyncBase(pairingEndpoint, fingerprint)
        check(preferences.edit()
            .putString(SYNC_BASE, syncBase)
            .putString(TLS_FINGERPRINT, fingerprint)
            .commit()) { "Não foi possível persistir a conexão." }
    }

    private fun validatedSyncBase(pairingEndpoint: String, fingerprint: String): String {
        val uri = URI.create(pairingEndpoint)
        require(uri.scheme == "https" && uri.host != null && uri.rawQuery == null && uri.fragment == null)
        require(uri.path == "/api/v1/pair/requests") { "Endpoint de pareamento inválido." }
        require(fingerprint.matches(Regex("[a-f0-9]{64}"))) { "Impressão digital inválida." }
        return URI("https", null, uri.host, uri.port, "/api/v1/sync", null, null).toString()
    }

    fun syncBaseUrl(): String? = if (hasReadableCredential()) {
        preferences.getString(SYNC_BASE, null)
    } else {
        null
    }

    fun tlsFingerprint(): String? = if (hasReadableCredential()) {
        preferences.getString(TLS_FINGERPRINT, null)
    } else {
        null
    }

    fun pairedDesktopId(): String? = if (hasReadableCredential()) {
        preferences.getString(DESKTOP_ID, null)
    } else {
        null
    }

    fun grantedRoles(): Set<String> = if (hasReadableCredential()) {
        preferences.getStringSet(GRANTED_ROLES, emptySet())?.toSet() ?: emptySet()
    } else {
        emptySet()
    }

    fun credential(): ByteArray {
        val ciphertext = Base64.getDecoder().decode(
            preferences.getString(CREDENTIAL, null) ?: error("Telefone não pareado."),
        )
        val iv = Base64.getDecoder().decode(
            preferences.getString(CREDENTIAL_IV, null) ?: error("Telefone não pareado."),
        )
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).also {
            require(it.size == CREDENTIAL_BYTES) { "Credencial de pareamento inválida." }
        }
    }

    fun clearPairing() {
        preferences.edit()
            .remove(CREDENTIAL)
            .remove(CREDENTIAL_IV)
            .remove(DESKTOP_ID)
            .remove(GRANTED_ROLES)
            .remove(SYNC_BASE)
            .remove(TLS_FINGERPRINT)
            .commit()
        keyStore.deleteEntry(AES_ALIAS)
        keyStore.deleteEntry(RSA_ALIAS)
    }

    private fun hasReadableCredential(): Boolean = runCatching {
        credential().also { it.fill(0) }
    }.isSuccess

    private fun ensureRsaKey() {
        if (keyStore.containsAlias(RSA_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(RSA_ALIAS, KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .build(),
        )
        generator.generateKeyPair()
    }

    private fun aesKey(): SecretKey {
        (keyStore.getKey(AES_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val RSA_ALIAS = "agenda_pairing_rsa_v1"
        const val AES_ALIAS = "agenda_pairing_credential_v1"
        const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val CREDENTIAL_BYTES = 32
        const val PREFERENCES = "pairing_private_v1"
        const val DEVICE_ID = "device_id"
        const val DESKTOP_ID = "desktop_id"
        const val CREDENTIAL = "credential"
        const val CREDENTIAL_IV = "credential_iv"
        const val GRANTED_ROLES = "granted_roles"
        const val SYNC_BASE = "sync_base"
        const val TLS_FINGERPRINT = "tls_fingerprint"
        val ALLOWED_ROLES = setOf("TASKS_READ", "CAPTURES_WRITE", "PROTOCOLS_EXECUTE")
        val OAEP_SPEC = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT,
        )
    }
}
