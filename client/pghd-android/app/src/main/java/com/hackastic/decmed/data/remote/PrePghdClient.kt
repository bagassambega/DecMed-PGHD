package com.hackastic.decmed.data.remote

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope
import com.hackastic.decmed.domain.model.pghd.PghdSubmitResult
import com.hackastic.decmed.crypto.DecmedCryptoNative
import com.hackastic.decmed.iota.DecmedIotaNative
import com.hackastic.decmed.utils.DecmedLog
import com.hackastic.decmed.viewmodel.PatientGrantAccessKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
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
            .put("signature", envelope.signature)

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

    suspend fun revokeAccess(
        profile: PatientProfile,
        hospitalPersonnelIotaAddress: String,
        accessLogIndex: Long,
        purpose: String
    ): PghdAccessRevokeResult = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "PRE_BASE_URL must be configured before revoking access." }
        val patientIotaAddress = profile.iotaAddress.requireField("iota_address")
        val patientIotaKeyPair = profile.iotaKeyPair.requireField("iota_key_pair")
        val personnelAddress = hospitalPersonnelIotaAddress.trim()
        require(personnelAddress.isNotBlank()) { "Hospital personnel IOTA address is required." }

        val accessLogIndexes = resolveActiveAccessLogIndexes(
            patientIotaAddress = patientIotaAddress,
            hospitalPersonnelIotaAddress = personnelAddress,
            purpose = purpose
        )

        val nonce = getNonce(patientIotaAddress)
        val signature = DecmedIotaNative.signPersonalMessage(patientIotaKeyPair, nonce)
        val payload = JSONObject()
            .put("hospital_personnel_iota_address", personnelAddress)
            .put("patient_iota_address", patientIotaAddress)
            .put("purpose", purpose)
            .put("signature", signature)
        postJson("/api/v1/keys/revoke", payload)

        accessLogIndexes.forEach { index ->
            DecmedIotaNative.revokePghdAccess(
                hospitalPersonnelAddress = personnelAddress,
                accessLogIndex = index,
                senderAddress = patientIotaAddress,
                senderKeyPair = patientIotaKeyPair
            )
        }

        PghdAccessRevokeResult(
            hospitalPersonnelIotaAddress = personnelAddress,
            accessLogIndexes = accessLogIndexes
        )
    }

    suspend fun getActiveAccessGrants(profile: PatientProfile): List<PghdActiveAccessGrant> = withContext(Dispatchers.IO) {
        val patientIotaAddress = profile.iotaAddress.requireField("iota_address")
        val now = Instant.now()
        val activeLogs = readRecentAccessLogs(patientIotaAddress)
            .filter { log -> !log.isRevoked && log.expiresAt()?.isAfter(now) == true }

        buildList {
            activeLogs
                .filter { log ->
                    log.accessDataTypes.any { it.equals("Pghd", ignoreCase = true) } &&
                        log.accessType.equals("Read", ignoreCase = true)
                }
                .forEach { log ->
                    add(
                        log.toActiveAccessGrant(
                            accessKind = PatientGrantAccessKind.PGHD_READ,
                            accessLogIndexes = listOf(log.index)
                        )
                    )
                }

            activeLogs
                .filter { log -> log.accessDataTypes.any { it.equals("Medical", ignoreCase = true) } }
                .groupBy { log -> log.hospitalPersonnelAddress.lowercase() }
                .values
                .forEach { logs ->
                    val read = logs.firstOrNull { it.accessType.equals("Read", ignoreCase = true) }
                    val update = logs.firstOrNull { it.accessType.equals("Update", ignoreCase = true) }
                    val primary = read ?: update ?: return@forEach
                    add(
                        primary.toActiveAccessGrant(
                            accessKind = PatientGrantAccessKind.MEDICAL_RECORD_READ_UPDATE,
                            accessLogIndexes = listOfNotNull(read?.index, update?.index).distinct()
                        )
                    )
                }
        }.sortedByDescending { it.grantedAt }
    }

    private fun resolveActiveAccessLogIndexes(
        patientIotaAddress: String,
        hospitalPersonnelIotaAddress: String,
        purpose: String
    ): List<Long> {
        val logs = readRecentAccessLogs(patientIotaAddress)
            .filter { log ->
                !log.isRevoked && log.hospitalPersonnelAddress.equals(
                    hospitalPersonnelIotaAddress,
                    ignoreCase = true
                )
            }

        val resolved = if (purpose == "Update") {
            val medicalLogs = logs.filter { log ->
                log.accessDataTypes.any { it.equals("Medical", ignoreCase = true) }
            }
            listOfNotNull(
                medicalLogs.firstOrNull { it.accessType.equals("Read", ignoreCase = true) }?.index,
                medicalLogs.firstOrNull { it.accessType.equals("Update", ignoreCase = true) }?.index
            ).distinct()
        } else {
            logs.filter { log ->
                log.accessDataTypes.any { it.equals("Pghd", ignoreCase = true) } &&
                    log.accessType.equals("Read", ignoreCase = true)
            }.map { it.index }.take(1)
        }

        if (resolved.isNotEmpty()) return resolved

        throw IllegalStateException(
            "No active on-chain access log found for personnel $hospitalPersonnelIotaAddress and purpose $purpose. " +
                "The access may already be revoked, expired, or created before the local grant cache was refreshed."
        )
    }

    private fun readRecentAccessLogs(patientIotaAddress: String): List<com.hackastic.decmed.iota.IotaPatientAccessLog> {
        val logs = mutableListOf<com.hackastic.decmed.iota.IotaPatientAccessLog>()
        var cursor = 0L
        val pageSize = 10L
        repeat(20) {
            val page = DecmedIotaNative.getPatientAccessLogs(
                cursor = cursor,
                size = pageSize,
                senderAddress = patientIotaAddress
            )
            if (page.isEmpty()) return logs
            logs += page
            cursor += page.size
            if (page.size < pageSize) return logs
        }
        return logs
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
            val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val errorMessage = "PRE sync failed: HTTP $responseCode from $url"
            DecmedLog.e(TAG, "$errorMessage\nResponse body: $responseBody\nRequest body: ${body.toString(2)}")
            throw PreHttpException(
                statusCode = responseCode,
                url = url,
                responseBody = responseBody,
                detail = errorMessage
            )
        }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        DecmedLog.i(TAG, "PRE response HTTP $responseCode from $url\nResponse body: $response")
        if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun String?.requireField(name: String): String =
        require(!isNullOrBlank()) { "Missing patient $name." }.let { this!! }

    private fun com.hackastic.decmed.iota.IotaPatientAccessLog.expiresAt(): Instant? =
        runCatching { Instant.parse(date).plus(expDur, ChronoUnit.MINUTES) }.getOrNull()

    private fun com.hackastic.decmed.iota.IotaPatientAccessLog.toActiveAccessGrant(
        accessKind: PatientGrantAccessKind,
        accessLogIndexes: List<Long>
    ): PghdActiveAccessGrant {
        val personnelName = decodePersonnelName(hospitalPersonnelMetadata)
        return PghdActiveAccessGrant(
            id = "iota-${accessKind.name}-${accessLogIndexes.joinToString("-")}",
            hospitalPersonnelIotaAddress = hospitalPersonnelAddress,
            hospitalPersonnelName = personnelName,
            hospitalName = hospitalName.ifBlank { null },
            accessKind = accessKind,
            accessLogIndexes = accessLogIndexes,
            grantedAt = date,
            expiresAt = expiresAt()?.toString()
        )
    }

    private fun decodePersonnelName(raw: String): String? {
        if (raw.isBlank()) return null
        return decodePersonnelNameFromJson(raw)
            ?: runCatching {
                val decoded = String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
                decodePersonnelNameFromJson(decoded)
            }.getOrNull()
    }

    private fun decodePersonnelNameFromJson(raw: String): String? = runCatching {
        val json = JSONObject(raw)
        val objects = buildList {
            add(json)
            listOf("data", "payload", "public_metadata", "publicMetadata", "personnel").forEach { key ->
                json.optJSONObject(key)?.let(::add)
            }
        }
        objects.firstNotNullOfOrNull { item ->
            item.optString("name")
                .ifBlank { item.optString("full_name") }
                .ifBlank { item.optString("fullName") }
                .ifBlank { item.optString("nama") }
                .ifBlank { null }
        }
    }.getOrNull()
}

private const val TAG = "PrePghdClient"

data class PghdAccessGrantResult(
    val hospitalPersonnelIotaAddress: String,
    val date: String
)

data class PghdAccessRevokeResult(
    val hospitalPersonnelIotaAddress: String,
    val accessLogIndexes: List<Long>
)

data class PghdActiveAccessGrant(
    val id: String,
    val hospitalPersonnelIotaAddress: String,
    val hospitalPersonnelName: String?,
    val hospitalName: String?,
    val accessKind: PatientGrantAccessKind,
    val accessLogIndexes: List<Long>,
    val grantedAt: String,
    val expiresAt: String?
)

class PreHttpException(
    val statusCode: Int,
    val url: String,
    val responseBody: String,
    detail: String
) : IllegalStateException(detail)
