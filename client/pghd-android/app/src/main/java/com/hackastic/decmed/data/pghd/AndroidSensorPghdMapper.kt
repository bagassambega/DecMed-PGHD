package com.hackastic.decmed.data.pghd

import android.hardware.Sensor
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.local.entity.SensorData

object AndroidSensorPghdMapper {
    private var lastStepCounterValue: Double? = null
    private var lastStepCounterTimeMillis: Long? = null

    fun toPghdRecords(
        batch: List<SensorData>,
        enabledRecordTypesBySensor: Map<Int, Set<String>> = emptyMap()
    ): List<PghdRecordEntity> =
        batch.sortedBy { it.endTimeEpochMillis }.flatMap { sensorData ->
            toPghdRecords(sensorData, enabledRecordTypesBySensor[sensorData.sensorType])
        }

    fun toPghdRecords(
        sensorData: SensorData,
        enabledRecordTypes: Set<String>? = null
    ): List<PghdRecordEntity> {
        val scalarValue = sensorData.value?.toDouble()

        val records = when (sensorData.sensorType) {
            Sensor.TYPE_HEART_RATE -> scalarValue?.let {
                listOf(sensorData.toRecord("heart_rate", "Heart rate", it, "bpm", direct("TYPE_HEART_RATE")))
            }.orEmpty()

            Sensor.TYPE_HEART_BEAT ->
                scalarValue?.let {
                    listOf(sensorData.toRecord("heart_rate_variability", "Heart rate variability", it, "ms", estimated("TYPE_HEART_BEAT", "android_heartbeat_interval_estimate")))
                }.orEmpty()

            Sensor.TYPE_STEP_DETECTOR ->
                listOf(sensorData.toRecord("steps", "Steps", 1.0, "count", direct("TYPE_STEP_DETECTOR")))

            Sensor.TYPE_STEP_COUNTER -> stepCounterRecords(sensorData, scalarValue)

            Sensor.TYPE_PRESSURE -> pressureRecords(sensorData, scalarValue)

            Sensor.TYPE_LIGHT -> scalarValue?.let {
                listOf(sensorData.toRecord("ambient_light", "Ambient light", it, "lux", direct("TYPE_LIGHT")))
            }.orEmpty()

            Sensor.TYPE_AMBIENT_TEMPERATURE -> scalarValue?.let {
                listOf(sensorData.toRecord("environmental_temperature", "Environmental temperature", it, "C", direct("TYPE_AMBIENT_TEMPERATURE;not_body_temperature")))
            }.orEmpty()

            Sensor.TYPE_RELATIVE_HUMIDITY -> scalarValue?.let {
                listOf(sensorData.toRecord("environmental_humidity", "Environmental humidity", it, "%", direct("TYPE_RELATIVE_HUMIDITY")))
            }.orEmpty()

            Sensor.TYPE_SIGNIFICANT_MOTION,
            Sensor.TYPE_STATIONARY_DETECT,
            Sensor.TYPE_MOTION_DETECT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
            Sensor.TYPE_HINGE_ANGLE -> emptyList()

            else -> emptyList()
        }

        if (enabledRecordTypes == null) return records
        if (enabledRecordTypes.isEmpty()) return emptyList()
        return records.filter { it.recordType in enabledRecordTypes }
    }

    private fun stepCounterRecords(sensorData: SensorData, value: Double?): List<PghdRecordEntity> {
        val current = value ?: return emptyList()
        val previous = lastStepCounterValue
        val previousTime = lastStepCounterTimeMillis
        lastStepCounterValue = current
        lastStepCounterTimeMillis = sensorData.endTimeEpochMillis

        if (previous == null || previousTime == null || current < previous) {
            return emptyList()
        }

        val delta = current - previous
        if (delta <= 0.0) return emptyList()

        return listOf(
            sensorData.toRecord(
                recordType = "steps",
                displayName = "Steps",
                value = delta,
                unit = "count",
                notes = direct("TYPE_STEP_COUNTER;baseline_delta")
            )
        )
    }

    private fun pressureRecords(sensorData: SensorData, value: Double?): List<PghdRecordEntity> {
        val pressureHPa = value ?: return emptyList()
        return listOf(
            sensorData.toRecord(
                recordType = "barometric_pressure",
                displayName = "Barometric pressure",
                value = pressureHPa,
                unit = "hPa",
                notes = direct("TYPE_PRESSURE")
            )
        )
    }

    private fun SensorData.toRecord(
        recordType: String,
        displayName: String,
        value: Double,
        unit: String,
        notes: String
    ): PghdRecordEntity =
        toRecord(recordType, displayName, value.toCompactText(), unit, value, notes)

    private fun SensorData.toRecord(
        recordType: String,
        displayName: String,
        valueText: String,
        unit: String,
        numericValue: Double?,
        notes: String
    ): PghdRecordEntity =
        PghdRecordEntity(
            uid = "phone-sensor:$recordType:$startTimeEpochMillis:$endTimeEpochMillis:$sensorType:${valueText.hashCode()}",
            recordType = recordType,
            displayName = displayName,
            startTimeEpochMillis = startTimeEpochMillis,
            endTimeEpochMillis = endTimeEpochMillis,
            unit = unit,
            valueText = valueText,
            numericValue = numericValue,
            sourceTag = PghdRecordEntity.SOURCE_PHONE_SENSOR,
            sourcePackageName = dataOrigin,
            notes = "$notes;sensor_type=$sensorType;accuracy=$accuracy"
        )

    private fun direct(sensorName: String): String =
        "source=phone_sensor;method=direct;estimated=false;raw_sensor=$sensorName"

    private fun estimated(sensorName: String, algorithm: String): String =
        "source=phone_sensor;method=derived;estimated=true;raw_sensor=$sensorName;algorithm=$algorithm"

    private fun Double.toCompactText(): String =
        if (this % 1.0 == 0.0) this.toLong().toString() else "%.2f".format(this)
}
