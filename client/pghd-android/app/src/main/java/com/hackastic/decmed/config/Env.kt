package com.hackastic.decmed.config

import com.hackastic.decmed.BuildConfig

object Env {
    val preBaseUrl: String = BuildConfig.PRE_BASE_URL
    val iotaRpcUrl: String = BuildConfig.IOTA_RPC_URL
    val gasStationBaseUrl: String = BuildConfig.GAS_STATION_BASE_URL
    val gasStationToken: String = BuildConfig.GAS_STATION_TOKEN
    val decmedPackageId: String = BuildConfig.DECMED_PACKAGE_ID
    val decmedAddressIdObjectId: String = BuildConfig.DECMED_ADDRESS_ID_OBJECT_ID
    val decmedAddressIdObjectVersion: Long = BuildConfig.DECMED_ADDRESS_ID_OBJECT_VERSION
    val decmedHospitalIdMetadataObjectId: String = BuildConfig.DECMED_HOSPITAL_ID_METADATA_OBJECT_ID
    val decmedHospitalIdMetadataObjectVersion: Long = BuildConfig.DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION
    val decmedHospitalPersonnelIdAccountObjectId: String =
        BuildConfig.DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID
    val decmedHospitalPersonnelIdAccountObjectVersion: Long =
        BuildConfig.DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION
    val decmedPatientIdAccountObjectId: String = BuildConfig.DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID
    val decmedPatientIdAccountObjectVersion: Long = BuildConfig.DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION
    val decmedHashSalt: String = BuildConfig.DECMED_HASH_SALT
    val iotaGasBudget: Long = BuildConfig.IOTA_GAS_BUDGET
    val iotaGasReserveNanos: Long = BuildConfig.IOTA_GAS_RESERVE_NANOS
    val iotaGasReserveSeconds: Long = BuildConfig.IOTA_GAS_RESERVE_SECONDS
    val pghdBatchIntervalMinutes: Long = BuildConfig.PGHD_BATCH_INTERVAL_MINUTES
    val pghdHealthConnectSyncIntervalMinutes: Long =
        BuildConfig.PGHD_HEALTH_CONNECT_SYNC_INTERVAL_MINUTES
    val pghdEarlyTriggerBytes: Long = BuildConfig.PGHD_EARLY_TRIGGER_BYTES
    val pghdDefaultSyncDays: Long = BuildConfig.PGHD_DEFAULT_SYNC_DAYS
    val pghdHistorySyncDays: Long = BuildConfig.PGHD_HISTORY_SYNC_DAYS
    val pghdSensorBatchSize: Int = BuildConfig.PGHD_SENSOR_BATCH_SIZE
    val pghdDefaultSensorIntervalMs: Int = BuildConfig.PGHD_DEFAULT_SENSOR_INTERVAL_MS
    val pghdSensorIntervalOptionsMs: List<Int> =
        BuildConfig.PGHD_SENSOR_INTERVAL_OPTIONS_MS
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(pghdDefaultSensorIntervalMs) }
    val pghdDefaultTestVectorMnemonic: String =
        BuildConfig.PGHD_DEFAULT_TEST_VECTOR_MNEMONIC.ifBlank {
            error("PGHD_DEFAULT_TEST_VECTOR_MNEMONIC must be configured in .env for deterministic patient test vectors.")
        }
}
