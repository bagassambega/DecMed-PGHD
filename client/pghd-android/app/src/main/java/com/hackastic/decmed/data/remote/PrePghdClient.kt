package com.hackastic.decmed.data.remote

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope
import com.hackastic.decmed.domain.model.pghd.PghdSubmitResult
import com.hackastic.decmed.crypto.DecmedCryptoNative
import com.hackastic.decmed.iota.DecmedIotaNative
import com.hackastic.decmed.utils.DecmedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Base64

class PrePghdClient(private val baseUrl: String) {
    suspend fun pushRegistration(profile: PatientProfile) {
        if (baseUrl.isBlank()) return
        val payload = JSONObject()
            .put("patient_id_hash", profile.idHash)
            .put("patient_iota_address", profile.iotaAddress)
            .put("pghd_public_key", profile.pghdPublicKey)
            .put("pre_public_key", profile.pghdPrePublicKey)
            .put("pghd_pre_public_key", profile.pghdPrePublicKey)
            .put("medical_pre_public_key", profile.medicalPrePublicKey)
        postJson("/api/v1/pghd/patient", payload)
    }

    suspend fun submitPghd(envelope: PghdSecureEnvelope): PghdSubmitResult {
        if (baseUrl.isBlank()) {
            return PghdSubmitResult(
                batchId = envelope.batchId,
                accepted = false,
                message = "PRE_BASE_URL is empty; secure PGHD envelope was built but not submitted."
            )
        }

        val payload = JSONObject()
            .put("batch_id", envelope.batchId)
            .put("patient_id_hash", envelope.patientIdHash)
            .put("patient_iota_address", envelope.patientIotaAddress)
            .put("enc_pghd", envelope.encPghd)
            .put("h_cipher", envelope.hCipher)
            .put("enc_aes_key_nonce", envelope.encAesKeyNonce)
            .put("capsule", envelope.capsule)
            .put("pghd_outer_signature", envelope.pghdOuterSignature)

        postJson("/api/v1/pghd/submit", payload)
        return PghdSubmitResult(batchId = envelope.batchId, accepted = true)
    }

    suspend fun grantPghdReadAccess(
        profile: PatientProfile,
        hospitalPersonnelIotaAddress: String,
        hospitalPersonnelPrePublicKey: String
    ): PghdAccessGrantResult = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "PRE_BASE_URL must be configured before granting PGHD access." }
        val patientIotaAddress = profile.iotaAddress.requireField("iota_address")
        val patientIotaKeyPair = profile.iotaKeyPair.requireField("iota_key_pair")
        val patientPghdPrePublicKey = profile.pghdPrePublicKey.requireField("pghd_pre_public_key")
        val patientPghdPreSecretKey = profile.pghdPreSecretKey.requireField("pghd_pre_secret_key")
        val personnelAddress = hospitalPersonnelIotaAddress.trim()
        val personnelPrePublicKey = hospitalPersonnelPrePublicKey.trim()
        require(personnelAddress.isNotBlank()) { "Hospital personnel IOTA address is required." }
        require(personnelPrePublicKey.isNotBlank()) { "Hospital personnel PRE public key is required." }

        val nonce = getNonce(patientIotaAddress)
        val dataPreKeyPair = DecmedCryptoNative.generatePreKeypair()
        val encryptedDataPreSecretSeed = DecmedCryptoNative.encryptForPublicKey(
            personnelPrePublicKey,
            Base64.getDecoder().decode(dataPreKeyPair.secretSeed)
        )
        val kfrag = DecmedCryptoNative.generateKfrag(
            delegatingSecretSeed = patientPghdPreSecretKey,
            receivingPublicKey = dataPreKeyPair.publicKey
        )
        val signature = DecmedIotaNative.signPersonalMessage(patientIotaKeyPair, nonce)
        val keysPayload = JSONObject()
            .put("enc_data_pre_secret_key_seed", encryptedDataPreSecretSeed.ciphertext)
            .put("hospital_personnel_iota_address", personnelAddress)
            .put("k_frag", kfrag.kFrag)
            .put("data_pre_public_key", dataPreKeyPair.publicKey)
            .put("data_pre_secret_key_seed_capsule", encryptedDataPreSecretSeed.capsule)
            .put("patient_iota_address", patientIotaAddress)
            .put("patient_pre_public_key", patientPghdPrePublicKey)
            .put("purpose", "ReadPghd")
            .put("signature", signature)
            .put("signer_pre_public_key", kfrag.signerPrePublicKey)
        val keyResponse = postJson("/api/v1/keys", keysPayload)
        val accessToken = keyResponse
            .getJSONObject("data")
            .optString("access_token_read_pghd")
            .ifBlank { keyResponse.getJSONObject("data").getString("access_token_read") }

        val accessData = JSONObject()
            .put("access_token", accessToken)
            .put("patient_iota_address", patientIotaAddress)
            .put("patient_name", profile.name ?: profile.id)
            .put("patient_pre_public_key", patientPghdPrePublicKey)
        val encryptedAccessData = DecmedCryptoNative.encryptForPublicKey(
            personnelPrePublicKey,
            accessData.toString().toByteArray(Charsets.UTF_8)
        )
        val moveMetadata = JSONObject()
            .put("capsule", encryptedAccessData.capsule)
            .put("enc_data", encryptedAccessData.ciphertext)
        val metadataBase64 = Base64.getEncoder()
            .encodeToString(moveMetadata.toString().toByteArray(Charsets.UTF_8))
        val date = Instant.now().toString()

        DecmedIotaNative.createPghdAccess(
            date = date,
            hospitalPersonnelAddress = personnelAddress,
            metadata = metadataBase64,
            senderAddress = patientIotaAddress,
            senderKeyPair = patientIotaKeyPair
        )

        PghdAccessGrantResult(
            hospitalPersonnelIotaAddress = personnelAddress,
            date = date
        )
    }

    suspend fun grantMedicalRecordReadUpdateAccess(
        profile: PatientProfile,
        hospitalPersonnelIotaAddress: String,
        hospitalPersonnelPrePublicKey: String
    ): PghdAccessGrantResult = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "PRE_BASE_URL must be configured before granting medical record access." }
        val patientIotaAddress = profile.iotaAddress.requireField("iota_address")
        val patientIotaKeyPair = profile.iotaKeyPair.requireField("iota_key_pair")
        val patientMedicalPrePublicKey = (profile.medicalPrePublicKey ?: profile.prePublicKey)
            .requireField("medical_pre_public_key")
        val patientMedicalPreSecretKey = (profile.medicalPreSecretKey ?: profile.preSecretKey)
            .requireField("medical_pre_secret_key")
        val personnelAddress = hospitalPersonnelIotaAddress.trim()
        val personnelPrePublicKey = hospitalPersonnelPrePublicKey.trim()
        require(personnelAddress.isNotBlank()) { "Hospital personnel IOTA address is required." }
        require(personnelPrePublicKey.isNotBlank()) { "Hospital personnel PRE public key is required." }

        val nonce = getNonce(patientIotaAddress)
        val dataPreKeyPair = DecmedCryptoNative.generatePreKeypair()
        val encryptedDataPreSecretSeed = DecmedCryptoNative.encryptForPublicKey(
            personnelPrePublicKey,
            Base64.getDecoder().decode(dataPreKeyPair.secretSeed)
        )
        val kfrag = DecmedCryptoNative.generateKfrag(
            delegatingSecretSeed = patientMedicalPreSecretKey,
            receivingPublicKey = dataPreKeyPair.publicKey
        )
        val signature = DecmedIotaNative.signPersonalMessage(patientIotaKeyPair, nonce)
        val keysPayload = JSONObject()
            .put("enc_data_pre_secret_key_seed", encryptedDataPreSecretSeed.ciphertext)
            .put("hospital_personnel_iota_address", personnelAddress)
            .put("k_frag", kfrag.kFrag)
            .put("data_pre_public_key", dataPreKeyPair.publicKey)
            .put("data_pre_secret_key_seed_capsule", encryptedDataPreSecretSeed.capsule)
            .put("patient_iota_address", patientIotaAddress)
            .put("patient_pre_public_key", patientMedicalPrePublicKey)
            .put("purpose", "Update")
            .put("signature", signature)
            .put("signer_pre_public_key", kfrag.signerPrePublicKey)
        val keyResponse = postJson("/api/v1/keys", keysPayload).getJSONObject("data")
        val accessTokenRead = keyResponse.getString("access_token_read")
        val accessTokenUpdate = keyResponse.optString("access_token_update").ifBlank {
            error("PRE did not return update access token for medical personnel.")
        }

        val patientName = profile.name ?: profile.id
        val readAccessData = JSONObject()
            .put("access_token", accessTokenRead)
            .put("patient_iota_address", patientIotaAddress)
            .put("patient_name", patientName)
        val updateAccessData = JSONObject()
            .put("access_token", accessTokenUpdate)
            .put("patient_iota_address", patientIotaAddress)
            .put("patient_name", patientName)
            .put("patient_pre_public_key", patientMedicalPrePublicKey)
        val metadata = JSONArray()
            .put(encryptAccessMetadata(personnelPrePublicKey, readAccessData))
            .put(encryptAccessMetadata(personnelPrePublicKey, updateAccessData))
            .toString()
        val date = Instant.now().toString()

        DecmedIotaNative.createPghdAccess(
            date = date,
            hospitalPersonnelAddress = personnelAddress,
            metadata = metadata,
            senderAddress = patientIotaAddress,
            senderKeyPair = patientIotaKeyPair
        )

        PghdAccessGrantResult(
            hospitalPersonnelIotaAddress = personnelAddress,
            date = date
        )
    }

    private fun encryptAccessMetadata(personnelPrePublicKey: String, accessData: JSONObject): String {
        val encryptedAccessData = DecmedCryptoNative.encryptForPublicKey(
            personnelPrePublicKey,
            accessData.toString().toByteArray(Charsets.UTF_8)
        )
        val moveMetadata = JSONObject()
            .put("capsule", encryptedAccessData.capsule)
            .put("enc_data", encryptedAccessData.ciphertext)
        return Base64.getEncoder().encodeToString(moveMetadata.toString().toByteArray(Charsets.UTF_8))
    }

    private suspend fun getNonce(patientIotaAddress: String): String {
        val response = postJson(
            "/api/v1/nonce",
            JSONObject().put("iota_address", patientIotaAddress)
        )
        return response.getString("data")
    }

    private suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        DecmedLog.i(TAG, "PRE request POST $url\nRequest body: ${body.toString(2)}")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
        val responseCode = connection.responseCode
        if (connection.responseCode !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
            val errorMessage = "PRE sync failed: HTTP $responseCode from $url${message?.let { " - $it" }.orEmpty()}"
            DecmedLog.e(TAG, "$errorMessage\nRequest body: ${body.toString(2)}")
            error(errorMessage)
        }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        DecmedLog.i(TAG, "PRE response HTTP $responseCode from $url\nResponse body: $response")
        if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun String?.requireField(name: String): String =
        require(!isNullOrBlank()) { "Missing patient $name." }.let { this!! }
}

private const val TAG = "PrePghdClient"

data class PghdAccessGrantResult(
    val hospitalPersonnelIotaAddress: String,
    val date: String
)
