package com.hackastic.decmed.data.pghd

import android.os.Build
import com.hackastic.decmed.BuildConfig
import com.hackastic.decmed.data.local.entity.PghdBatchDataPointEntity
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdBatchPeriod
import com.hackastic.decmed.domain.model.pghd.PghdCollectionPeriod
import com.hackastic.decmed.domain.model.pghd.PghdDataGroupPayload
import com.hackastic.decmed.domain.model.pghd.PghdDataPointPayload
import com.hackastic.decmed.domain.model.pghd.PghdMeasurementValue
import com.hackastic.decmed.domain.model.pghd.PghdSourceDevice
import org.json.JSONObject
import java.util.UUID

object PghdPayloadConverter {
    fun recordsToBatchPayload(
        records: List<PghdRecordEntity>,
        patientId: String,
        batchId: String = UUID.randomUUID().toString(),
        sourceDevice: PghdSourceDevice = currentAndroidDevice(),
        collectionStartedAtEpochMillis: Long? = null,
        collectionEndedAtEpochMillis: Long? = null,
        triggerReason: String? = PghdBatchPayload.TRIGGER_TIME_BASED
    ): PghdBatchPayload {
        require(records.isNotEmpty()) { "PGHD batch must contain at least one data point." }

        val sortedRecords = records.sortedBy { it.endTimeEpochMillis }
        return PghdBatchPayload(
            batchId = batchId,
            patientId = patientId,
            sourceDevice = sourceDevice,
            batchPeriod = PghdBatchPeriod(
                startTimestamp = epochMillisToUnixSeconds(sortedRecords.first().startTimeEpochMillis),
                endTimestamp = epochMillisToUnixSeconds(sortedRecords.last().endTimeEpochMillis)
            ),
            collectionPeriod = collectionStartedAtEpochMillis?.let { startedAt ->
                PghdCollectionPeriod(
                    startedAt = epochMillisToUnixSeconds(startedAt),
                    endedAt = epochMillisToUnixSeconds(collectionEndedAtEpochMillis ?: System.currentTimeMillis())
                )
            },
            triggerReason = triggerReason,
            dataGroup = PghdBatchAnalyzer.analyze(recordsToDataGroups(sortedRecords))
        )
    }

    fun recordsToDataGroups(records: List<PghdRecordEntity>): List<PghdDataGroupPayload> =
        records
            .groupBy { record ->
                PghdDataGroupKey(
                    measurementType = record.recordType.toSnakeCase(),
                    deviceType = record.toPghdDeviceType(),
                    recordingMethod = record.toRecordingMethod(),
                    source = record.toPghdSource(),
                    sourceLabel = record.sourceTag,
                    sourcePackageName = record.sourcePackageName,
                    deviceSource = record.toDeviceSource()
                )
            }
            .map { (key, groupRecords) ->
                PghdDataGroupPayload(
                    measurementType = key.measurementType,
                    deviceType = key.deviceType,
                    recordingMethod = key.recordingMethod,
                    source = key.source,
                    sourceLabel = key.sourceLabel,
                    sourcePackageName = key.sourcePackageName,
                    deviceSource = key.deviceSource,
                    dataPoints = groupRecords.sortedBy { it.endTimeEpochMillis }.map(::recordToDataPoint)
                )
            }
            .sortedWith(compareBy<PghdDataGroupPayload> { it.measurementType }.thenBy { it.source })

    fun recordToDataPoint(record: PghdRecordEntity): PghdDataPointPayload =
        PghdDataPointPayload(
            timestamp = epochMillisToUnixSeconds(record.endTimeEpochMillis),
            value = record.toMeasurementValue(),
            unit = record.unit.toPghdUnit(record.recordType)
        )

    fun payloadToBatchEntity(payload: PghdBatchPayload): PghdBatchEntity =
        PghdBatchEntity(
            batchId = payload.batchId,
            patientId = payload.patientId,
            startTimestamp = payload.batchPeriod.startTimestamp,
            endTimestamp = payload.batchPeriod.endTimestamp,
            collectionStartedAtEpochMillis = payload.collectionPeriod?.startedAt?.let(::unixSecondsToEpochMillis),
            collectionEndedAtEpochMillis = payload.collectionPeriod?.endedAt?.let(::unixSecondsToEpochMillis),
            triggerReason = payload.triggerReason ?: PghdBatchEntity.TRIGGER_TIME_BASED
        )

    fun payloadToDataPointEntities(payload: PghdBatchPayload): List<PghdBatchDataPointEntity> =
        payload.dataGroup.flatMap { dataGroup ->
            dataGroup.dataPoints.map { dataPoint ->
                PghdBatchDataPointEntity(
                    batchId = payload.batchId,
                    measurementType = dataGroup.measurementType,
                    timestamp = dataPoint.timestamp.toString(),
                    timestampEpochMillis = unixSecondsToEpochMillis(dataPoint.timestamp),
                    valueJson = PghdPayloadSerializer.dataPointValueToJson(dataPoint.value).toString(),
                    unit = dataPoint.unit,
                    source = dataGroup.source,
                    deviceType = dataGroup.deviceType,
                    recordingMethod = dataGroup.recordingMethod
                )
            }
        }

    fun epochMillisToUnixSeconds(epochMillis: Long): Long =
        epochMillis / 1000L

    fun unixSecondsToEpochMillis(timestamp: Long): Long =
        timestamp * 1000L

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

        valueText.toJsonObjectValue()?.let { return it }

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
            PghdRecordEntity.SOURCE_PHONE_SENSOR -> "phone_sensor"
            else -> sourcePackageName ?: "android_sensor"
        }

    private fun PghdRecordEntity.toPghdDeviceType(): String =
        when {
            sourceTag == PghdRecordEntity.SOURCE_HEALTH_CONNECT && sourcePackageName?.isLikelyWearablePackage() == true -> "wearable"
            sourceTag == PghdRecordEntity.SOURCE_HEALTH_CONNECT -> "wearable"
            sourceTag == PghdRecordEntity.SOURCE_MANUAL -> "smartphone"
            sourceTag == PghdRecordEntity.SOURCE_PHONE_SENSOR -> "smartphone"
            else -> "smartphone"
        }

    private fun PghdRecordEntity.toRecordingMethod(): String =
        when (sourceTag) {
            PghdRecordEntity.SOURCE_MANUAL -> "manual"
            else -> "auto"
        }

    private fun PghdRecordEntity.toDeviceSource(): String =
        when (sourceTag) {
            PghdRecordEntity.SOURCE_HEALTH_CONNECT -> sourcePackageName ?: "Health Connect provider"
            PghdRecordEntity.SOURCE_MANUAL -> "DecMed patient manual entry"
            PghdRecordEntity.SOURCE_PHONE_SENSOR -> "DecMed Android phone sensor"
            else -> sourcePackageName ?: sourceTag
        }

    private fun String.toJsonObjectValue(): PghdMeasurementValue.ObjectValue? {
        if (!trimStart().startsWith("{")) return null
        return runCatching {
            val json = JSONObject(this)
            val map = json.keys().asSequence().associateWith { key ->
                when (val value = json.get(key)) {
                    is Number -> value.toDouble()
                    is Boolean -> value
                    else -> value.toString()
                }
            }
            PghdMeasurementValue.ObjectValue(map)
        }.getOrNull()
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

    private fun String.isLikelyWearablePackage(): Boolean {
        val normalized = lowercase()
        return listOf("xiaomi", "mihealth", "mifitness", "huami", "zepp").any(normalized::contains)
    }

    private data class PghdDataGroupKey(
        val measurementType: String,
        val deviceType: String,
        val recordingMethod: String?,
        val source: String,
        val sourceLabel: String?,
        val sourcePackageName: String?,
        val deviceSource: String?
    )
}
