package com.hackastic.decmed.data.pghd

import android.os.Build
import com.hackastic.decmed.BuildConfig
import com.hackastic.decmed.data.local.entity.PghdBatchDataPointEntity
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdBatchPeriod
import com.hackastic.decmed.domain.model.pghd.PghdDataPointPayload
import com.hackastic.decmed.domain.model.pghd.PghdMeasurementValue
import com.hackastic.decmed.domain.model.pghd.PghdSourceDevice
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

object PghdPayloadConverter {
    fun recordsToBatchPayload(
        records: List<PghdRecordEntity>,
        patientId: String,
        batchId: String = UUID.randomUUID().toString(),
        sourceDevice: PghdSourceDevice = currentAndroidDevice()
    ): PghdBatchPayload {
        require(records.isNotEmpty()) { "PGHD batch must contain at least one data point." }

        val sortedRecords = records.sortedBy { it.endTimeEpochMillis }
        return PghdBatchPayload(
            batchId = batchId,
            patientId = patientId,
            sourceDevice = sourceDevice,
            batchPeriod = PghdBatchPeriod(
                startTimestamp = epochMillisToIsoUtc(sortedRecords.first().startTimeEpochMillis),
                endTimestamp = epochMillisToIsoUtc(sortedRecords.last().endTimeEpochMillis)
            ),
            dataPoints = sortedRecords.map(::recordToDataPoint)
        )
    }

    fun recordToDataPoint(record: PghdRecordEntity): PghdDataPointPayload =
        PghdDataPointPayload(
            measurementType = record.recordType.toSnakeCase(),
            timestamp = epochMillisToIsoUtc(record.endTimeEpochMillis),
            value = record.toMeasurementValue(),
            unit = record.unit.toPghdUnit(record.recordType),
            source = record.toPghdSource(),
            deviceType = record.toPghdDeviceType(),
            recordingMethod = record.toRecordingMethod()
        )

    fun payloadToBatchEntity(payload: PghdBatchPayload): PghdBatchEntity =
        PghdBatchEntity(
            batchId = payload.batchId,
            schemaVersion = payload.schemaVersion,
            patientId = payload.patientId,
            sourceDeviceType = payload.sourceDevice.type,
            sourceDevicePlatform = payload.sourceDevice.platform,
            sourceDeviceAppVersion = payload.sourceDevice.appVersion,
            sourceDeviceManufacturer = payload.sourceDevice.deviceManufacturer,
            sourceDeviceModel = payload.sourceDevice.deviceModel,
            startTimestamp = payload.batchPeriod.startTimestamp,
            endTimestamp = payload.batchPeriod.endTimestamp,
            startTimeEpochMillis = isoUtcToEpochMillis(payload.batchPeriod.startTimestamp),
            endTimeEpochMillis = isoUtcToEpochMillis(payload.batchPeriod.endTimestamp),
            payloadJson = PghdPayloadSerializer.toJson(payload)
        )

    fun payloadToDataPointEntities(payload: PghdBatchPayload): List<PghdBatchDataPointEntity> =
        payload.dataPoints.map { dataPoint ->
            PghdBatchDataPointEntity(
                batchId = payload.batchId,
                measurementType = dataPoint.measurementType,
                timestamp = dataPoint.timestamp,
                timestampEpochMillis = isoUtcToEpochMillis(dataPoint.timestamp),
                valueJson = PghdPayloadSerializer.dataPointValueToJson(dataPoint.value).toString(),
                unit = dataPoint.unit,
                source = dataPoint.source,
                deviceType = dataPoint.deviceType,
                recordingMethod = dataPoint.recordingMethod
            )
        }

    fun epochMillisToIsoUtc(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

    fun isoUtcToEpochMillis(timestamp: String): Long =
        Instant.parse(timestamp).toEpochMilli()

    private fun currentAndroidDevice(): PghdSourceDevice =
        PghdSourceDevice(
            type = "smartphone",
            platform = "android",
            appVersion = BuildConfig.VERSION_NAME,
            deviceManufacturer = Build.MANUFACTURER.orUnknown(),
            deviceModel = Build.MODEL.orUnknown()
        )

    private fun PghdRecordEntity.toMeasurementValue(): PghdMeasurementValue {
        val numeric = numericValue
        if (numeric != null) return PghdMeasurementValue.NumberValue(numeric)

        if (recordType == "blood_pressure" && valueText.contains("/")) {
            val parts = valueText.split("/")
            val systolic = parts.getOrNull(0)?.toDoubleOrNull()
            val diastolic = parts.getOrNull(1)?.toDoubleOrNull()
            if (systolic != null && diastolic != null) {
                return PghdMeasurementValue.ObjectValue(
                    mapOf(
                        "systolic" to systolic,
                        "diastolic" to diastolic
                    )
                )
            }
        }

        return PghdMeasurementValue.ObjectValue(mapOf("raw" to valueText))
    }

    private fun PghdRecordEntity.toPghdSource(): String =
        when (sourceTag) {
            PghdRecordEntity.SOURCE_HEALTH_CONNECT -> "android_health_connect"
            PghdRecordEntity.SOURCE_MANUAL -> "android_manual_input"
            else -> sourcePackageName ?: "android_sensor"
        }

    private fun PghdRecordEntity.toPghdDeviceType(): String =
        when (sourceTag) {
            PghdRecordEntity.SOURCE_HEALTH_CONNECT,
            PghdRecordEntity.SOURCE_MANUAL -> "smartphone"
            else -> "smartphone"
        }

    private fun PghdRecordEntity.toRecordingMethod(): String =
        when (sourceTag) {
            PghdRecordEntity.SOURCE_MANUAL -> "manual"
            else -> "auto"
        }

    private fun String.toPghdUnit(recordType: String): String =
        when {
            recordType == "heart_rate" && this == "bpm" -> "beats/min"
            recordType == "oxygen_saturation" && this == "%" -> "percent"
            recordType == "body_temperature" && this == "C" -> "Cel"
            recordType == "basal_body_temperature" && this == "C" -> "Cel"
            recordType == "blood_pressure" -> "mm[Hg]"
            else -> this
        }

    private fun String.toSnakeCase(): String =
        trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .trim('_')
            .lowercase()

    private fun String?.orUnknown(): String =
        this?.takeIf { it.isNotBlank() } ?: "unknown"
}
