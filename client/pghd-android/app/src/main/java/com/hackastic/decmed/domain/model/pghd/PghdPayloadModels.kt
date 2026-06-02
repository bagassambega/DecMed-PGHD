package com.hackastic.decmed.domain.model.pghd

data class PghdBatchPayload(
    val schemaVersion: String = SCHEMA_VERSION,
    val batchId: String,
    val patientId: String,
    val sourceDevice: PghdSourceDevice,
    val batchPeriod: PghdBatchPeriod,
    val dataPoints: List<PghdDataPointPayload>
) {
    companion object {
        const val SCHEMA_VERSION = "1.0"
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
    val startTimestamp: String,
    val endTimestamp: String
)

data class PghdDataPointPayload(
    val measurementType: String,
    val timestamp: String,
    val value: PghdMeasurementValue,
    val unit: String,
    val source: String,
    val deviceType: String,
    val recordingMethod: String? = null
)

sealed class PghdMeasurementValue {
    data class NumberValue(val value: Double) : PghdMeasurementValue()
    data class ObjectValue(val values: Map<String, Any>) : PghdMeasurementValue()
}
