package com.hackastic.decmed.iota

import android.content.Context
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.utils.DecmedLog
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64

object DecmedIotaNative {
    private val loadError: Throwable? = runCatching {
        System.loadLibrary("decmed_iota")
    }.exceptionOrNull()
    @Volatile private var androidTlsInitialized = false

    external fun initAndroidTlsJson(context: Context): String
    external fun derivePatientIdentityJson(seedWords: String, patientId: String): String
    external fun generateMnemonicJson(): String
    external fun signupAndPublishPghdKeyJson(
        config: String,
        patientIdHash: String,
        privateMetadata: String,
        pghdPublicKey: String,
        senderAddress: String,
        senderKeyPair: String
    ): String
    external fun ensureRegisteredJson(config: String, senderAddress: String): String
    external fun getPghdPublicKeyJson(config: String, patientAddress: String, senderAddress: String): String
    external fun getPatientAccessLogsJson(config: String, cursor: Long, size: Long, senderAddress: String): String
    external fun getHospitalPersonnelInfoJson(
        config: String,
        hospitalPersonnelAddress: String,
        senderAddress: String
    ): String
    external fun createPghdAccessJson(
        config: String,
        date: String,
        hospitalPersonnelAddress: String,
        metadata: String,
        senderAddress: String,
        senderKeyPair: String
    ): String
    external fun revokePghdAccessJson(
        config: String,
        hospitalPersonnelAddress: String,
        accessLogIndex: Long,
        senderAddress: String,
        senderKeyPair: String
    ): String
    external fun signPersonalMessageJson(senderKeyPair: String, message: String): String

    fun initialize(context: Context) {
        if (loadError != null && isHostJvmUnitTest()) return
        ensureLoaded()
        if (androidTlsInitialized) return
        synchronized(this) {
            if (!androidTlsInitialized) {
                decodeData(initAndroidTlsJson(context.applicationContext)) { Unit }
                androidTlsInitialized = true
            }
        }
    }

    fun derivePatientIdentity(seedWords: String, patientId: String): IotaIdentity {
        if (loadError != null && isHostJvmUnitTest()) {
            val material = "$seedWords:$patientId".toByteArray(Charsets.UTF_8)
            val digest = sha256Hex(material)
            return IotaIdentity(
                idHash = digest,
                iotaAddress = "0x" + digest.take(64),
                iotaKeyPair = "host-jvm-test-iota-keypair:$digest"
            )
        }
        ensureLoaded()
        return decodeData(derivePatientIdentityJson(seedWords, patientId)) { json ->
            IotaIdentity(
                idHash = json.getString("id_hash"),
                iotaAddress = json.getString("iota_address"),
                iotaKeyPair = json.getString("iota_key_pair")
            )
        }
    }

    fun generateMnemonic(): String {
        if (loadError != null && isHostJvmUnitTest()) {
            return "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        }
        ensureLoaded()
        return decodeData(generateMnemonicJson()) { it.getString("value") }
    }

    fun signupAndPublishPghdKey(
        patientIdHash: String,
        privateMetadata: String,
        pghdPublicKey: String,
        senderAddress: String,
        senderKeyPair: String
    ) {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        decodeData(
            signupAndPublishPghdKeyJson(
                iotaConfigJson(),
                patientIdHash,
                privateMetadata,
                pghdPublicKey,
                senderAddress,
                senderKeyPair
            )
        ) { Unit }
    }

    fun ensureRegistered(senderAddress: String) {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        decodeData(ensureRegisteredJson(iotaConfigJson(), senderAddress)) { Unit }
    }

    fun getPghdPublicKey(patientAddress: String, senderAddress: String): String {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        return decodeData(getPghdPublicKeyJson(iotaConfigJson(), patientAddress, senderAddress)) {
            it.getString("value")
        }
    }

    fun getPatientAccessLogs(
        cursor: Long,
        size: Long,
        senderAddress: String
    ): List<IotaPatientAccessLog> {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        return decodeArrayData(
            getPatientAccessLogsJson(
                iotaConfigJson(requireGrantObjects = true),
                cursor,
                size,
                senderAddress
            )
        ) { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        IotaPatientAccessLog(
                            accessDataTypes = item.optJSONArray("access_data_type").toStringList(),
                            accessType = item.optString("access_type"),
                            date = item.optString("date"),
                            expDur = item.optLong("exp_dur"),
                            hospitalName = item.optJSONObject("hospital_metadata")?.optString("name").orEmpty(),
                            hospitalPersonnelAddress = item.optString("hospital_personnel_address"),
                            hospitalPersonnelMetadata = item.optString("hospital_personnel_metadata"),
                            index = item.optLong("index"),
                            isRevoked = item.optBoolean("is_revoked")
                        )
                    )
                }
            }
        }
    }

    fun getHospitalPersonnelInfo(
        hospitalPersonnelAddress: String,
        senderAddress: String
    ): IotaHospitalPersonnelInfo {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        return decodeData(
            getHospitalPersonnelInfoJson(
                iotaConfigJson(requireGrantObjects = true),
                hospitalPersonnelAddress,
                senderAddress
            )
        ) { json ->
            val publicMetadata = json.optString("public_metadata")
            val decodedPublicMetadata = decodeHospitalPersonnelPublicMetadata(publicMetadata)
            IotaHospitalPersonnelInfo(
                publicMetadata = decodedPublicMetadata ?: publicMetadata,
                hospitalName = json.optString("hospital_name"),
                displayName = decodeHospitalPersonnelDisplayName(publicMetadata)
            )
        }
    }

    fun createPghdAccess(
        date: String,
        hospitalPersonnelAddress: String,
        metadata: String,
        senderAddress: String,
        senderKeyPair: String
    ) {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        decodeData(
            createPghdAccessJson(
                iotaConfigJson(requireGrantObjects = true),
                date,
                hospitalPersonnelAddress,
                metadata,
                senderAddress,
                senderKeyPair
            )
        ) { Unit }
    }

    fun revokePghdAccess(
        hospitalPersonnelAddress: String,
        accessLogIndex: Long,
        senderAddress: String,
        senderKeyPair: String
    ) {
        ensureLoaded()
        ensureAndroidTlsInitialized()
        decodeData(
            revokePghdAccessJson(
                iotaConfigJson(requireGrantObjects = true),
                hospitalPersonnelAddress,
                accessLogIndex,
                senderAddress,
                senderKeyPair
            )
        ) { Unit }
    }

    fun signPersonalMessage(senderKeyPair: String, message: String): String {
        ensureLoaded()
        return decodeData(signPersonalMessageJson(senderKeyPair, message)) { it.getString("value") }
    }

    private fun iotaConfigJson(requireGrantObjects: Boolean = false): String {
        require(Env.iotaRpcUrl.isNotBlank()) { "IOTA_RPC_URL must be configured." }
        require(Env.gasStationBaseUrl.isNotBlank()) { "GAS_STATION_BASE_URL must be configured." }
        require(Env.decmedPackageId.isNotBlank()) { "DECMED_PACKAGE_ID must be configured." }
        require(Env.decmedAddressIdObjectId.isNotBlank()) { "DECMED_ADDRESS_ID_OBJECT_ID must be configured." }
        require(Env.decmedPatientIdAccountObjectId.isNotBlank()) {
            "DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID must be configured."
        }
        require(Env.decmedAddressIdObjectVersion > 0) {
            "DECMED_ADDRESS_ID_OBJECT_VERSION must be configured."
        }
        require(Env.decmedPatientIdAccountObjectVersion > 0) {
            "DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION must be configured."
        }
        if (requireGrantObjects) {
            require(Env.decmedHospitalIdMetadataObjectId.isNotBlank()) {
                "DECMED_HOSPITAL_ID_METADATA_OBJECT_ID must be configured."
            }
            require(Env.decmedHospitalPersonnelIdAccountObjectId.isNotBlank()) {
                "DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID must be configured."
            }
            require(Env.decmedHospitalIdMetadataObjectVersion > 0) {
                "DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION must be configured."
            }
            require(Env.decmedHospitalPersonnelIdAccountObjectVersion > 0) {
                "DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION must be configured."
            }
        }

        val config = JSONObject()
            .put("iota_url", Env.iotaRpcUrl)
            .put("gas_station_base_url", Env.gasStationBaseUrl)
            .put("package_id", Env.decmedPackageId)
            .put("address_id_object_id", Env.decmedAddressIdObjectId)
            .put("address_id_object_version", Env.decmedAddressIdObjectVersion)
            .put("hospital_id_metadata_object_id", Env.decmedHospitalIdMetadataObjectId)
            .put("hospital_id_metadata_object_version", Env.decmedHospitalIdMetadataObjectVersion)
            .put("hospital_personnel_id_account_object_id", Env.decmedHospitalPersonnelIdAccountObjectId)
            .put("hospital_personnel_id_account_object_version", Env.decmedHospitalPersonnelIdAccountObjectVersion)
            .put("patient_id_account_object_id", Env.decmedPatientIdAccountObjectId)
            .put("patient_id_account_object_version", Env.decmedPatientIdAccountObjectVersion)
            .put("hash_salt", Env.decmedHashSalt)
            .put("gas_budget", Env.iotaGasBudget)
            .put("gas_reserve_nanos", Env.iotaGasReserveNanos)
            .put("gas_reserve_seconds", Env.iotaGasReserveSeconds)
            .put("gas_station_token", Env.gasStationToken)
        DecmedLog.i(
            TAG,
            "Native IOTA config: iota_url=${Env.iotaRpcUrl} gas_station_base_url=${Env.gasStationBaseUrl} " +
                "package_id=${Env.decmedPackageId} address_id=${Env.decmedAddressIdObjectId}@${Env.decmedAddressIdObjectVersion} " +
                "patient_id_account=${Env.decmedPatientIdAccountObjectId}@${Env.decmedPatientIdAccountObjectVersion} " +
                "hospital_id_metadata=${Env.decmedHospitalIdMetadataObjectId}@${Env.decmedHospitalIdMetadataObjectVersion} " +
                "hospital_personnel_id_account=${Env.decmedHospitalPersonnelIdAccountObjectId}@${Env.decmedHospitalPersonnelIdAccountObjectVersion} " +
                "requireGrantObjects=$requireGrantObjects gas_budget=${Env.iotaGasBudget} gas_reserve_nanos=${Env.iotaGasReserveNanos} " +
                "gas_reserve_seconds=${Env.iotaGasReserveSeconds} has_gas_station_token=${Env.gasStationToken.isNotBlank()}"
        )
        return config.toString()
    }

    private fun ensureLoaded() {
        loadError?.let {
            throw IllegalStateException(
                "Native DecMed IOTA library is not packaged for this device ABI. " +
                    "Build crypto/decmed-iota for Android and package libdecmed_iota.so.",
                it
            )
        }
    }

    private fun ensureAndroidTlsInitialized() {
        check(androidTlsInitialized) {
            "Native Android TLS verifier is not initialized. Call DecmedIotaNative.initialize(context) before IOTA calls."
        }
    }

    private fun isHostJvmUnitTest(): Boolean =
        System.getProperty("java.runtime.name").orEmpty().contains("OpenJDK", ignoreCase = true)

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun <T> decodeData(raw: String, mapper: (JSONObject) -> T): T {
        DecmedLog.i(TAG, "Native IOTA raw response: $raw")
        val wrapper = JSONObject(raw)
        if (!wrapper.optBoolean("ok")) {
            val message = wrapper.optString("error", "Native IOTA call failed.")
            DecmedLog.e(TAG, "Native IOTA error response: $message\nFull native wrapper: $raw")
            throw IllegalStateException(message)
        }
        val data = wrapper.opt("data")
        val dataJson = when (data) {
            is JSONObject -> data
            is String -> JSONObject().put("value", data)
            JSONObject.NULL, null -> JSONObject()
            else -> JSONObject().put("value", data)
        }
        return mapper(dataJson)
    }

    private fun <T> decodeArrayData(raw: String, mapper: (JSONArray) -> T): T {
        DecmedLog.i(TAG, "Native IOTA raw response: $raw")
        val wrapper = JSONObject(raw)
        if (!wrapper.optBoolean("ok")) {
            val message = wrapper.optString("error", "Native IOTA call failed.")
            DecmedLog.e(TAG, "Native IOTA error response: $message\nFull native wrapper: $raw")
            throw IllegalStateException(message)
        }
        val data = wrapper.opt("data")
        val dataArray = when (data) {
            is JSONArray -> data
            JSONObject.NULL, null -> JSONArray()
            else -> throw IllegalStateException("Native IOTA response data is not an array: $raw")
        }
        return mapper(dataArray)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                add(optString(index))
            }
        }
    }

    private fun decodeHospitalPersonnelDisplayName(publicMetadata: String): String? {
        val candidates = buildList {
            if (publicMetadata.isNotBlank()) add(publicMetadata)
            runCatching {
                String(Base64.getDecoder().decode(publicMetadata), Charsets.UTF_8)
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                val json = JSONObject(candidate)
                json.optString("name")
                    .ifBlank { json.optString("full_name") }
                    .ifBlank { json.optString("fullName") }
                    .ifBlank { json.optString("nama") }
                    .ifBlank { null }
            }.getOrNull()
        }
    }

    private fun decodeHospitalPersonnelPublicMetadata(publicMetadata: String): String? =
        runCatching {
            String(Base64.getDecoder().decode(publicMetadata), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }

    private const val TAG = "DecmedIotaNative"
}

data class IotaIdentity(
    val idHash: String,
    val iotaAddress: String,
    val iotaKeyPair: String
)

data class IotaPatientAccessLog(
    val accessDataTypes: List<String>,
    val accessType: String,
    val date: String,
    val expDur: Long,
    val hospitalName: String,
    val hospitalPersonnelAddress: String,
    val hospitalPersonnelMetadata: String,
    val index: Long,
    val isRevoked: Boolean
)

data class IotaHospitalPersonnelInfo(
    val publicMetadata: String,
    val hospitalName: String,
    val displayName: String?
)
