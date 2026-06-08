package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdInnerPlaintext
import com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope
import com.hackastic.decmed.crypto.DecmedCryptoNative
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PghdSecureEnvelopeBuilder {
    fun build(payload: PghdBatchPayload, patientProfile: PatientProfile): PghdSecureEnvelope {
        val secretKey = patientProfile.pghdSecretKey.requireField("pghd_secret_key")
        val pghdPrePublicKey = patientProfile.pghdPrePublicKey.requireField("pghd_pre_public_key")
        val privateKey = decodeEcPrivateKey(secretKey)

        val pghdPlaintext = PghdPayloadSerializer.toJson(payload)
        val hPlain = sha256(pghdPlaintext.toByteArray(Charsets.UTF_8))
        val innerSignature = signPrehashed(hPlain, privateKey)
        val innerPlaintext = PghdInnerPlaintext(
            pghdData = pghdPlaintext,
            innerSignature = Base64.getEncoder().encodeToString(innerSignature)
        )
        val innerPlaintextJson = PghdPayloadSerializer.innerPlaintextToJson(innerPlaintext)

        val aesKey = randomBytes(AES_KEY_BYTES)
        val aesNonce = randomBytes(GCM_NONCE_BYTES)
        val encPghdBytes = aesGcmEncrypt(
            key = aesKey,
            nonce = aesNonce,
            plaintext = innerPlaintextJson.toByteArray(Charsets.UTF_8),
            aad = payload.batchId.toByteArray(Charsets.UTF_8)
        )

        val hCipher = sha256(encPghdBytes)
        val outerSignature = signPrehashed(hCipher, privateKey)
        val wrappedKeyNonce = DecmedCryptoNative.encryptForPublicKey(
            pghdPrePublicKey,
            aesKey + aesNonce
        )

        return PghdSecureEnvelope(
            batchId = payload.batchId,
            patientIdHash = patientProfile.idHash,
            patientIotaAddress = patientProfile.iotaAddress,
            encPghd = Base64.getEncoder().encodeToString(encPghdBytes),
            hCipher = hCipher.toHex(),
            encAesKeyNonce = wrappedKeyNonce.ciphertext,
            capsule = wrappedKeyNonce.capsule,
            pghdOuterSignature = Base64.getEncoder().encodeToString(outerSignature)
        )
    }

    private fun aesGcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun signPrehashed(hash: ByteArray, privateKey: PrivateKey): ByteArray {
        return Signature.getInstance("NONEwithECDSA").run {
            initSign(privateKey)
            update(hash)
            sign()
        }
    }

    private fun decodeEcPrivateKey(base64: String): PrivateKey =
        KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun String?.requireField(name: String): String =
        require(!isNullOrBlank()) { "Missing patient $name." }.let { this!! }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private const val AES_KEY_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
}
