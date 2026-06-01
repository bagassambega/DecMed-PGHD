package com.hackastic.decmed.data.remote

import com.hackastic.decmed.domain.model.patient.PatientProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class IotaPatientGateway(private val rpcUrl: String) {
    suspend fun registerPatient(profile: PatientProfile) {
        if (rpcUrl.isBlank()) return
        val params = JSONArray()
            .put(profile.idHash)
            .put(profile.iotaAddress)
            .put(profile.prePublicKey)
            .put(profile.pghdPublicKey)
        call("decmed_registerPghdPatient", params)
    }

    suspend fun ensureRegistered(profile: PatientProfile) {
        if (rpcUrl.isBlank()) return
        val params = JSONArray().put(profile.iotaAddress)
        val result = call("decmed_isPghdPatientRegistered", params)
        if (result.optBoolean("result", true).not()) {
            error("Account not found on IOTA for ${profile.iotaAddress}")
        }
    }

    private suspend fun call(method: String, params: JSONArray): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", method)
            .put("params", params)

        val connection = URL(rpcUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
        val response = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("IOTA RPC failed: HTTP ${connection.responseCode} - $message")
        }
        JSONObject(response)
    }
}
