package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdBatchPeriod
import com.hackastic.decmed.domain.model.pghd.PghdDataPointPayload
import com.hackastic.decmed.domain.model.pghd.PghdInnerPlaintext
import com.hackastic.decmed.domain.model.pghd.PghdMeasurementValue
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
            .put("data_points", JSONArray(payload.dataPoints.map(::dataPointToJson)))

    fun dataPointValueToJson(value: PghdMeasurementValue): Any =
        when (value) {
            is PghdMeasurementValue.NumberValue -> value.value
            is PghdMeasurementValue.ObjectValue -> JSONObject(value.values)
        }

    fun innerPlaintextToJson(innerPlaintext: PghdInnerPlaintext): String =
        JSONObject()
            .put("pghd_data", JSONObject(innerPlaintext.pghdData))
            .put("inner_signature", innerPlaintext.innerSignature)
            .toString()

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

    private fun dataPointToJson(dataPoint: PghdDataPointPayload): JSONObject {
        val json = JSONObject()
            .put("measurement_type", dataPoint.measurementType)
            .put("timestamp", dataPoint.timestamp)
            .put("value", dataPointValueToJson(dataPoint.value))
            .put("unit", dataPoint.unit)
            .put("source", dataPoint.source)
            .put("device_type", dataPoint.deviceType)

        dataPoint.recordingMethod?.let { json.put("recording_method", it) }
        return json
    }
}
