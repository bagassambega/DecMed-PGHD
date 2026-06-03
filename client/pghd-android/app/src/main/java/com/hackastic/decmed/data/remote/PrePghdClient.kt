package com.hackastic.decmed.data.remote

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope
import com.hackastic.decmed.domain.model.pghd.PghdSubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class PrePghdClient(private val baseUrl: String) {
    suspend fun pushRegistration(profile: PatientProfile) {
        if (baseUrl.isBlank()) return
        val payload = JSONObject()
            .put("patient_id_hash", profile.idHash)
            .put("patient_iota_address", profile.iotaAddress)
            .put("pghd_public_key", profile.pghdPublicKey)
            .put("pre_public_key", profile.prePublicKey)
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

    private suspend fun postJson(path: String, body: JSONObject) = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
        if (connection.responseCode !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
            error("PRE sync failed: HTTP ${connection.responseCode}${message?.let { " - $it" }.orEmpty()}")
        }
    }
}
