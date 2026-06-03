package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdInnerPlaintext
import com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PghdSecureEnvelopeBuilder {
    fun build(payload: PghdBatchPayload, patientProfile: PatientProfile): PghdSecureEnvelope {
        val secretKey = patientProfile.pghdSecretKey.requireField("pghd_secret_key")
        val prePublicKey = patientProfile.prePublicKey.requireField("pre_public_key")
        val privateKey = decodeEcPrivateKey(secretKey)
        val publicKey = decodeEcPublicKey(prePublicKey)

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
        val wrappedKeyNonce = wrapAesKeyNonce(
            aesKeyNonce = aesKey + aesNonce,
            recipientPublicKey = publicKey,
            aad = payload.batchId.toByteArray(Charsets.UTF_8)
        )

        return PghdSecureEnvelope(
            batchId = payload.batchId,
            patientIdHash = patientProfile.idHash,
            patientIotaAddress = patientProfile.iotaAddress,
            encPghd = Base64.getEncoder().encodeToString(encPghdBytes),
            hCipher = hCipher.toHex(),
            encAesKeyNonce = wrappedKeyNonce.encAesKeyNonce,
            capsule = wrappedKeyNonce.capsule,
            pghdOuterSignature = Base64.getEncoder().encodeToString(outerSignature)
        )
    }

    private fun wrapAesKeyNonce(
        aesKeyNonce: ByteArray,
        recipientPublicKey: PublicKey,
        aad: ByteArray
    ): WrappedKeyNonce {
        val ephemeralKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(ephemeralKeyPair.private)
            doPhase(recipientPublicKey, true)
            generateSecret()
        }
        val wrappingKey = sha256(sharedSecret + "decmed-pghd-pre-wrap-v1".toByteArray(Charsets.UTF_8))
        val wrapNonce = randomBytes(GCM_NONCE_BYTES)
        val encryptedKeyNonce = aesGcmEncrypt(
            key = wrappingKey,
            nonce = wrapNonce,
            plaintext = aesKeyNonce,
            aad = aad
        )

        val capsule = JSONObject()
            .put("algorithm", "ECDH-P256-AES256GCM-PRE-ADAPTER-V1")
            .put(
                "ephemeral_public_key",
                Base64.getEncoder().encodeToString(ephemeralKeyPair.public.encoded)
            )
            .put("wrap_nonce", Base64.getEncoder().encodeToString(wrapNonce))
            .toString()

        return WrappedKeyNonce(
            encAesKeyNonce = Base64.getEncoder().encodeToString(encryptedKeyNonce),
            capsule = Base64.getEncoder().encodeToString(capsule.toByteArray(Charsets.UTF_8))
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

    private fun decodeEcPublicKey(base64: String): PublicKey =
        KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun String?.requireField(name: String): String =
        require(!isNullOrBlank()) { "Missing patient $name." }.let { this!! }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private data class WrappedKeyNonce(
        val encAesKeyNonce: String,
        val capsule: String
    )

    private const val AES_KEY_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
}
