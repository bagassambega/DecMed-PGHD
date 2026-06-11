package com.hackastic.decmed.domain.model.pghd

data class PghdBatchPayload(
    val schemaVersion: String = SCHEMA_VERSION,
    val batchId: String,
    val patientId: String,
    val sourceDevice: PghdSourceDevice,
    val batchPeriod: PghdBatchPeriod,
    val triggerReason: String? = null,
    val dataGroup: List<PghdDataGroupPayload>
) {
    companion object {
        const val SCHEMA_VERSION = "1.0"
        const val TRIGGER_TIME_BASED = "time_based"
        const val TRIGGER_SIZE_THRESHOLD = "size_threshold"
        const val TRIGGER_MANUAL_SUBMIT = "manual_submit"
    }
}

data class PghdSourceDevice(
    val type: String,
    val platform: String,
    val appVersion: String,
    val deviceManufacturer: String,
    val deviceModel: String
)

data class PghdBatchPeriod(
    val startTimestamp: Long,
    val endTimestamp: Long
)

data class PghdDataGroupPayload(
    val measurementType: String,
    val deviceType: String,
    val recordingMethod: String? = null,
    val source: String,
    val sourceLabel: String? = null,
    val sourcePackageName: String? = null,
    val deviceSource: String? = null,
    val dataPoints: List<PghdDataPointPayload>
)

data class PghdDataPointPayload(
    val timestamp: Long,
    val value: PghdMeasurementValue,
    val unit: String
)

sealed class PghdMeasurementValue {
    data class NumberValue(val value: Double) : PghdMeasurementValue()
    data class ObjectValue(val values: Map<String, Any>) : PghdMeasurementValue()
}
