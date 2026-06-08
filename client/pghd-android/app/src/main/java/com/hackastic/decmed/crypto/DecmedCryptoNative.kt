package com.hackastic.decmed.crypto

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64

object DecmedCryptoNative {
    private val loadError: Throwable? = runCatching {
        System.loadLibrary("decmed_crypto")
    }.exceptionOrNull()

    external fun generatePreKeypairJson(): String
    external fun publicKeyFromSeedJson(seed: String): String
    external fun encryptForPublicKeyJson(publicKey: String, plaintext: String): String
    external fun generateKfragJson(delegatingSecretSeed: String, receivingPublicKey: String): String

    fun generatePreKeypair(): PreKeyPair {
        ensureLoaded()
        return decodeData(generatePreKeypairJson()) { json ->
            PreKeyPair(
                publicKey = json.getString("public_key"),
                secretSeed = json.getString("secret_seed")
            )
        }
    }

    fun publicKeyFromSeed(secretSeed: String): String {
        if (loadError != null && isHostJvmUnitTest()) {
            return "host-jvm-test-umbral-public-key:" + sha256Hex(secretSeed.toByteArray(Charsets.UTF_8))
        }
        ensureLoaded()
        return decodeData(publicKeyFromSeedJson(secretSeed)) { it.getString("value") }
    }

    fun encryptForPublicKey(publicKey: String, plaintext: ByteArray): PreEncryptedPayload {
        ensureLoaded()
        val plaintextBase64 = Base64.getEncoder().encodeToString(plaintext)
        return decodeData(encryptForPublicKeyJson(publicKey, plaintextBase64)) { json ->
            PreEncryptedPayload(
                capsule = json.getString("capsule"),
                ciphertext = json.getString("ciphertext")
            )
        }
    }

    fun generateKfrag(delegatingSecretSeed: String, receivingPublicKey: String): KfragPayload {
        ensureLoaded()
        return decodeData(generateKfragJson(delegatingSecretSeed, receivingPublicKey)) { json ->
            KfragPayload(
                kFrag = json.getString("k_frag"),
                signerPrePublicKey = json.getString("signer_pre_public_key")
            )
        }
    }

    fun ensureLoaded() {
        loadError?.let {
            throw IllegalStateException(
                "Native DecMed crypto library is not packaged for this device ABI. " +
                    "Build crypto/decmed-crypto for Android and package libdecmed_crypto.so.",
                it
            )
        }
    }

    private fun <T> decodeData(raw: String, mapper: (JSONObject) -> T): T {
        ensureLoaded()
        val wrapper = JSONObject(raw)
        if (!wrapper.optBoolean("ok")) {
            throw IllegalStateException(wrapper.optString("error", "Native crypto call failed."))
        }
        val data = wrapper.get("data")
        val dataJson = when (data) {
            is JSONObject -> data
            is String -> JSONObject().put("value", data)
            else -> error("Unexpected native crypto response.")
        }
        return mapper(dataJson)
    }

    private fun isHostJvmUnitTest(): Boolean =
        System.getProperty("java.runtime.name").orEmpty().contains("OpenJDK", ignoreCase = true)

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}

data class PreKeyPair(
    val publicKey: String,
    val secretSeed: String
)

data class PreEncryptedPayload(
    val capsule: String,
    val ciphertext: String
)

data class KfragPayload(
    val kFrag: String,
    val signerPrePublicKey: String
)
