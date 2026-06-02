package com.hackastic.decmed.data.patient

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft
import com.hackastic.decmed.domain.repository.PatientCryptoBridge
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class DeterministicPatientCryptoBridge : PatientCryptoBridge {
    override suspend fun generateMnemonic(): String {
        return DEFAULT_TEST_VECTOR_MNEMONIC
    }

    override suspend fun deriveRegistrationProfile(draft: PatientRegistrationDraft): PatientProfile {
        val seed = bip39Seed(draft.seedWords, draft.nik)
        val idHash = sha256Hex(draft.nik.toByteArray(Charsets.UTF_8))
        val pghdKeyPair = deriveP256KeyPair(seed, "decmed-pghd-ecdsa-v1")
        val iotaKeySeed = sha256(seed + "decmed-iota-ed25519-v1".toByteArray(Charsets.UTF_8))
        val iotaAddress = iotaAddressFromSeed(iotaKeySeed)

        return PatientProfile(
            id = draft.nik,
            idHash = idHash,
            iotaAddress = iotaAddress,
            prePublicKey = pghdKeyPair.publicKeyBase64,
            pghdPublicKey = pghdKeyPair.publicKeyBase64,
            pghdSecretKey = pghdKeyPair.secretKeyBase64
        )
    }

    private fun bip39Seed(words: String, passphrase: String): ByteArray {
        val spec = PBEKeySpec(
            words.trim().lowercase().toCharArray(),
            "mnemonic$passphrase".toByteArray(Charsets.UTF_8),
            2048,
            512
        )
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
    }

    private fun deriveP256KeyPair(seed: ByteArray, label: String): DerivedKeyPair {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)
        val privateScalar = deriveScalar(seed, label, P256_N)
        val publicPoint = multiply(P256_G, privateScalar)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(ECPrivateKeySpec(privateScalar, ecSpec))
        val publicKey = keyFactory.generatePublic(ECPublicKeySpec(publicPoint, ecSpec))
        return DerivedKeyPair(
            publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded),
            secretKeyBase64 = Base64.getEncoder().encodeToString(privateKey.encoded)
        )
    }

    private fun deriveScalar(seed: ByteArray, label: String, order: BigInteger): BigInteger {
        val material = sha256(seed + label.toByteArray(Charsets.UTF_8))
        return BigInteger(1, material).mod(order.subtract(BigInteger.ONE)).add(BigInteger.ONE)
    }

    private fun iotaAddressFromSeed(seed: ByteArray): String {
        return "0x" + sha256(seed).take(32).joinToString("") { "%02x".format(it) }
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha256Hex(data: ByteArray): String = sha256(data).joinToString("") { "%02x".format(it) }

    private fun multiply(point: ECPoint, scalar: BigInteger): ECPoint {
        var result = ECPoint.POINT_INFINITY
        var addend = point
        var k = scalar
        while (k.signum() > 0) {
            if (k.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            k = k.shiftRight(1)
        }
        return result
    }

    private fun add(a: ECPoint, b: ECPoint): ECPoint {
        if (a == ECPoint.POINT_INFINITY) return b
        if (b == ECPoint.POINT_INFINITY) return a

        val x1 = a.affineX
        val y1 = a.affineY
        val x2 = b.affineX
        val y2 = b.affineY

        if (x1 == x2 && y1.add(y2).mod(P256_P) == BigInteger.ZERO) return ECPoint.POINT_INFINITY

        val lambda = if (x1 == x2 && y1 == y2) {
            x1.pow(2).multiply(BigInteger.valueOf(3)).add(P256_A)
                .multiply(y1.multiply(BigInteger.valueOf(2L)).modInverse(P256_P))
        } else {
            y2.subtract(y1).multiply(x2.subtract(x1).mod(P256_P).modInverse(P256_P))
        }.mod(P256_P)

        val x3 = lambda.pow(2).subtract(x1).subtract(x2).mod(P256_P)
        val y3 = lambda.multiply(x1.subtract(x3)).subtract(y1).mod(P256_P)
        return ECPoint(x3, y3)
    }

    private data class DerivedKeyPair(
        val publicKeyBase64: String,
        val secretKeyBase64: String
    )

    companion object {
        private val P256_P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
        private val P256_A = BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16)
        private val P256_N = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
        private val P256_G = ECPoint(
            BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
            BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)
        )

        const val DEFAULT_TEST_VECTOR_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
