package com.hackastic.decmed.domain.model.pghd

data class PghdBatchPayload(
    val schemaVersion: String = SCHEMA_VERSION,
    val batchId: String,
    val patientId: String,
    val sourceDevice: PghdSourceDevice,
    val batchPeriod: PghdBatchPeriod,
    val collectionPeriod: PghdCollectionPeriod? = null,
    val triggerReason: String? = null,
    val dataGroup: List<PghdDataGroupPayload>
) {
    companion object {
        const val SCHEMA_VERSION = "1.1"
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

data class PghdCollectionPeriod(
    val startedAt: Long,
    val endedAt: Long
)

data class PghdDataGroupPayload(
    val measurementType: String,
    val deviceType: String,
    val recordingMethod: String? = null,
    val source: String,
    val sourceLabel: String? = null,
    val sourcePackageName: String? = null,
    val deviceSource: String? = null,
    val statistics: List<PghdStatisticsSummary> = emptyList(),
    val clinicalThresholds: List<PghdClinicalThreshold> = emptyList(),
    val anomalyCount: Int = 0,
    val dataPoints: List<PghdDataPointPayload>
)

data class PghdDataPointPayload(
    val timestamp: Long,
    val value: PghdMeasurementValue,
    val unit: String,
    val anomalies: List<PghdAnomalyFlag> = emptyList()
)

data class PghdStatisticsSummary(
    val field: String,
    val count: Int,
    val minimum: Double,
    val maximum: Double,
    val mean: Double,
    val median: Double,
    val mode: List<Double>,
    val percentiles: PghdPercentiles,
    val unit: String
)

data class PghdPercentiles(
    val p5: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p95: Double
)

data class PghdClinicalThreshold(
    val measurementType: String,
    val field: String,
    val minimum: Double,
    val maximum: Double,
    val minimumInclusive: Boolean,
    val maximumInclusive: Boolean,
    val unit: String,
    val label: String,
    val reference: String,
    val referenceUrl: String,
    val population: String
)

data class PghdAnomalyFlag(
    val field: String,
    val value: Double,
    val direction: String,
    val normalMinimum: Double,
    val normalMaximum: Double
) {
    companion object {
        const val BELOW_RANGE = "below_range"
        const val ABOVE_RANGE = "above_range"
    }
}

sealed class PghdMeasurementValue {
    data class NumberValue(val value: Double) : PghdMeasurementValue()
    data class ObjectValue(val values: Map<String, Any>) : PghdMeasurementValue()
}
