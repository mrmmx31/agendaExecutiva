package com.pessoal.agenda.mobile.health

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedHealthValue(val ciphertext: String, val iv: String)

interface HealthDataCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedHealthValue
    fun decrypt(value: EncryptedHealthValue, associatedData: ByteArray): ByteArray
}

class AndroidKeystoreHealthDataCipher : HealthDataCipher {
    private val keyStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedHealthValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        return EncryptedHealthValue(
            ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal(plaintext)),
            iv = Base64.getEncoder().encodeToString(cipher.iv),
        )
    }

    override fun decrypt(value: EncryptedHealthValue, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.getDecoder().decode(value.iv)),
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(Base64.getDecoder().decode(value.ciphertext))
    }

    private fun key(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "agenda_health_fields_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
