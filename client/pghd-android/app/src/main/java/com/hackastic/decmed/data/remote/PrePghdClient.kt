package com.hackastic.decmed.data.remote

import com.hackastic.decmed.domain.model.patient.PatientProfile
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
