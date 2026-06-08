package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.local.entity.SensorData
import kotlin.math.sqrt

object AndroidSensorPghdMapper {
    fun toPghdRecord(sensorData: SensorData): PghdRecordEntity {
        val vectorValue = sensorData.vectorValueText()
        val scalarValue = sensorData.value?.toDouble()
        val valueText = vectorValue ?: scalarValue?.toString() ?: "raw"

        return PghdRecordEntity(
            uid = "android-sensor:${sensorData.dataType}:${sensorData.startTimeEpochMillis}:${sensorData.endTimeEpochMillis}:${sensorData.sensorType}",
            recordType = sensorData.dataType.toPghdRecordType(),
            displayName = sensorData.dataType.toPghdDisplayName(),
            startTimeEpochMillis = sensorData.startTimeEpochMillis,
            endTimeEpochMillis = sensorData.endTimeEpochMillis,
            unit = sensorData.unit,
            valueText = valueText,
            numericValue = if (vectorValue == null) scalarValue else null,
            sourceTag = PghdRecordEntity.SOURCE_ANDROID_SENSOR,
            sourcePackageName = sensorData.dataOrigin,
            notes = "sensor_type=${sensorData.sensorType};accuracy=${sensorData.accuracy}"
        )
    }

    private fun SensorData.vectorValueText(): String? {
        val x = valueX ?: return null
        val y = valueY ?: return null
        val z = valueZ ?: return null
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        return """{"x":$x,"y":$y,"z":$z,"magnitude":$magnitude}"""
    }

    private fun String.toPghdRecordType(): String =
        removePrefix("com.google.")
            .replace('.', '_')
            .replace('-', '_')
            .lowercase()

    private fun String.toPghdDisplayName(): String =
        toPghdRecordType()
            .split('_')
            .joinToString(" ") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
}
