package com.hackastic.decmed.data.remote

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.iota.DecmedIotaNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64

class IotaPatientGateway {
    suspend fun registerPatient(profile: PatientProfile) = withContext(Dispatchers.IO) {
        val idHash = profile.idHash.requireField("id_hash")
        val iotaAddress = profile.iotaAddress.requireField("iota_address")
        val iotaKeyPair = profile.iotaKeyPair.requireField("iota_key_pair")
        val pghdPublicKey = profile.pghdPublicKey.requireField("pghd_public_key")
        profile.pghdPrePublicKey.requireField("pghd_pre_public_key")
        DecmedIotaNative.signupAndPublishPghdKey(
            patientIdHash = idHash,
            privateMetadata = defaultPrivateMetadata(profile),
            pghdPublicKey = pghdPublicKey,
            senderAddress = iotaAddress,
            senderKeyPair = iotaKeyPair
        )
    }

    suspend fun ensureRegistered(profile: PatientProfile) = withContext(Dispatchers.IO) {
        val iotaAddress = profile.iotaAddress.requireField("iota_address")
        DecmedIotaNative.ensureRegistered(iotaAddress)
    }

    suspend fun getPghdPublicKey(patientAddress: String, senderAddress: String): String =
        withContext(Dispatchers.IO) {
            DecmedIotaNative.getPghdPublicKey(patientAddress, senderAddress)
        }

    private fun defaultPrivateMetadata(profile: PatientProfile): String {
        val metadata = JSONObject()
            .put("source", "pghd_android")
            .put("patient_id_hash", profile.idHash)
        return Base64.getEncoder().encodeToString(metadata.toString().toByteArray(Charsets.UTF_8))
    }

    private fun String?.requireField(name: String): String =
        require(!isNullOrBlank()) { "Missing patient $name." }.let { this!! }
}
