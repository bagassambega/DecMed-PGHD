package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdBatchPeriod
import com.hackastic.decmed.domain.model.pghd.PghdClinicalThreshold
import com.hackastic.decmed.domain.model.pghd.PghdCollectionPeriod
import com.hackastic.decmed.domain.model.pghd.PghdDataGroupPayload
import com.hackastic.decmed.domain.model.pghd.PghdDataPointPayload
import com.hackastic.decmed.domain.model.pghd.PghdMeasurementValue
import com.hackastic.decmed.domain.model.pghd.PghdStatisticsSummary
import com.hackastic.decmed.domain.model.pghd.PghdSourceDevice
import org.json.JSONArray
import org.json.JSONObject

object PghdPayloadSerializer {
    fun toJson(payload: PghdBatchPayload): String =
        toJsonObject(payload).toString()

    fun toJsonObject(payload: PghdBatchPayload): JSONObject =
        JSONObject()
            .put("schema_version", payload.schemaVersion)
            .put("batch_id", payload.batchId)
            .put("patient_id", payload.patientId)
            .put("source_device", sourceDeviceToJson(payload.sourceDevice))
            .put("batch_period", batchPeriodToJson(payload.batchPeriod))
            .put("data_group", JSONArray(payload.dataGroup.map(::dataGroupToJson)))
            .also { json ->
                payload.collectionPeriod?.let { json.put("collection_period", collectionPeriodToJson(it)) }
                payload.triggerReason?.let { json.put("trigger_reason", it) }
            }

    fun dataPointValueToJson(value: PghdMeasurementValue): Any =
        when (value) {
            is PghdMeasurementValue.NumberValue -> value.value
            is PghdMeasurementValue.ObjectValue -> JSONObject(value.values)
        }

    private fun sourceDeviceToJson(sourceDevice: PghdSourceDevice): JSONObject =
        JSONObject()
            .put("type", sourceDevice.type)
            .put("platform", sourceDevice.platform)
            .put("app_version", sourceDevice.appVersion)
            .put("device_manufacturer", sourceDevice.deviceManufacturer)
            .put("device_model", sourceDevice.deviceModel)

    private fun batchPeriodToJson(batchPeriod: PghdBatchPeriod): JSONObject =
        JSONObject()
            .put("start_timestamp", batchPeriod.startTimestamp)
            .put("end_timestamp", batchPeriod.endTimestamp)

    private fun collectionPeriodToJson(collectionPeriod: PghdCollectionPeriod): JSONObject =
        JSONObject()
            .put("started_at", collectionPeriod.startedAt)
            .put("ended_at", collectionPeriod.endedAt)

    private fun dataGroupToJson(dataGroup: PghdDataGroupPayload): JSONObject {
        val json = JSONObject()
            .put("measurement_type", dataGroup.measurementType)
            .put("device_type", dataGroup.deviceType)
            .put("source", dataGroup.source)
            .put("statistics", JSONArray(dataGroup.statistics.map(::statisticsToJson)))
            .put("clinical_thresholds", JSONArray(dataGroup.clinicalThresholds.map(::thresholdToJson)))
            .put("anomaly_count", dataGroup.anomalyCount)
            .put("data_points", JSONArray(dataGroup.dataPoints.map(::dataPointToJson)))

        dataGroup.recordingMethod?.let { json.put("recording_method", it) }
        dataGroup.sourceLabel?.let { json.put("source_label", it) }
        dataGroup.sourcePackageName?.let { json.put("source_package_name", it) }
        dataGroup.deviceSource?.let { json.put("device_source", it) }
        return json
    }

    private fun dataPointToJson(dataPoint: PghdDataPointPayload): JSONObject {
        return JSONObject()
            .put("timestamp", dataPoint.timestamp)
            .put("value", dataPointValueToJson(dataPoint.value))
            .put("unit", dataPoint.unit)
            .put("anomalies", JSONArray(dataPoint.anomalies.map { anomaly ->
                JSONObject()
                    .put("field", anomaly.field)
                    .put("value", anomaly.value)
                    .put("direction", anomaly.direction)
                    .put("normal_minimum", anomaly.normalMinimum)
                    .put("normal_maximum", anomaly.normalMaximum)
            }))
    }

    private fun statisticsToJson(statistics: PghdStatisticsSummary): JSONObject =
        JSONObject()
            .put("field", statistics.field)
            .put("count", statistics.count)
            .put("minimum", statistics.minimum)
            .put("maximum", statistics.maximum)
            .put("mean", statistics.mean)
            .put("median", statistics.median)
            .put("mode", JSONArray(statistics.mode))
            .put(
                "percentiles",
                JSONObject()
                    .put("p5", statistics.percentiles.p5)
                    .put("p25", statistics.percentiles.p25)
                    .put("p50", statistics.percentiles.p50)
                    .put("p75", statistics.percentiles.p75)
                    .put("p95", statistics.percentiles.p95)
            )
            .put("unit", statistics.unit)

    private fun thresholdToJson(threshold: PghdClinicalThreshold): JSONObject =
        JSONObject()
            .put("field", threshold.field)
            .put("minimum", threshold.minimum)
            .put("maximum", threshold.maximum)
            .put("minimum_inclusive", threshold.minimumInclusive)
            .put("maximum_inclusive", threshold.maximumInclusive)
            .put("unit", threshold.unit)
            .put("label", threshold.label)
            .put("reference", threshold.reference)
            .put("reference_url", threshold.referenceUrl)
            .put("population", threshold.population)
}
