package com.hackastic.decmed.data.local.security

import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PatientSecureStorage {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrBlank()) return plainText
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(
            Base64.getEncoder().encodeToString(cipher.iv),
            Base64.getEncoder().encodeToString(cipherText)
        ).joinToString(".")
    }

    fun decrypt(storedValue: String?): String? {
        if (storedValue.isNullOrBlank()) return storedValue
        val parts = storedValue.split(".")
        if (parts.size != 2) return storedValue
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.getDecoder().decode(parts[0])
            val cipherText = Base64.getDecoder().decode(parts[1])
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        keyGenerator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "decmed_patient_secure_storage_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
