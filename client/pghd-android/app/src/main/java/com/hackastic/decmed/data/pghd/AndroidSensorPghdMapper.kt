package com.hackastic.decmed.data.pghd

import android.hardware.Sensor
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.local.entity.SensorData
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

object AndroidSensorPghdMapper {
    private var lastStepCounterValue: Double? = null
    private var lastStepCounterTimeMillis: Long? = null
    private var lastPressureAltitudeMeters: Double? = null

    fun toPghdRecords(batch: List<SensorData>): List<PghdRecordEntity> =
        batch.sortedBy { it.endTimeEpochMillis }.flatMap(::toPghdRecords)

    fun toPghdRecords(sensorData: SensorData): List<PghdRecordEntity> {
        val scalarValue = sensorData.value?.toDouble()

        return when (sensorData.sensorType) {
            Sensor.TYPE_HEART_RATE -> scalarValue?.let {
                listOf(sensorData.toRecord("heart_rate", "Heart rate", it, "bpm", direct("TYPE_HEART_RATE")))
            }.orEmpty()

            Sensor.TYPE_HEART_BEAT -> scalarValue?.let {
                listOf(sensorData.toRecord("heart_beat", "Heart beat", it, "event", estimated("TYPE_HEART_BEAT", "android_heartbeat_event")))
            }.orEmpty()

            Sensor.TYPE_STEP_DETECTOR ->
                listOf(sensorData.toRecord("steps", "Steps", 1.0, "count", direct("TYPE_STEP_DETECTOR")))

            Sensor.TYPE_STEP_COUNTER -> stepCounterRecords(sensorData, scalarValue)

            Sensor.TYPE_SIGNIFICANT_MOTION ->
                listOf(sensorData.toRecord("activity_event", "Activity event", "significant_motion", "event", null, direct("TYPE_SIGNIFICANT_MOTION")))

            Sensor.TYPE_STATIONARY_DETECT ->
                listOf(sensorData.toRecord("activity_event", "Activity event", "stationary", "event", null, direct("TYPE_STATIONARY_DETECT")))

            Sensor.TYPE_MOTION_DETECT ->
                listOf(sensorData.toRecord("activity_event", "Activity event", "motion", "event", null, direct("TYPE_MOTION_DETECT")))

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

            Sensor.TYPE_PROXIMITY -> scalarValue?.let {
                listOf(sensorData.toRecord("proximity", "Proximity", it, "cm", direct("TYPE_PROXIMITY")))
            }.orEmpty()

            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> scalarValue?.let {
                val onBody = if (it == 0.0) "on_body" else "off_body"
                listOf(sensorData.toRecord("wear_status", "Wear status", onBody, "state", null, direct("TYPE_LOW_LATENCY_OFFBODY_DETECT")))
            }.orEmpty()

            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
            Sensor.TYPE_LINEAR_ACCELERATION -> vectorMagnitude(sensorData)?.let {
                listOf(sensorData.toRecord("movement_intensity", "Movement intensity", it, "m/s^2", estimated(sensorData.sensorName(), "vector_magnitude")))
            }.orEmpty()

            Sensor.TYPE_GRAVITY -> gravityTilt(sensorData)?.let {
                listOf(sensorData.toRecord("tilt_angle", "Tilt angle", it, "degrees", estimated("TYPE_GRAVITY", "atan2_horizontal_vs_vertical_gravity")))
            }.orEmpty()

            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> vectorMagnitude(sensorData)?.let {
                listOf(sensorData.toRecord("rotation_intensity", "Rotation intensity", it, "rad/s", estimated(sensorData.sensorName(), "angular_velocity_magnitude")))
            }.orEmpty()

            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> vectorMagnitude(sensorData)?.let {
                listOf(sensorData.toRecord("magnetic_field_strength", "Magnetic field strength", it, "uT", estimated(sensorData.sensorName(), "magnetic_vector_magnitude")))
            }.orEmpty()

            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> vectorMagnitude(sensorData)?.let {
                listOf(sensorData.toRecord("orientation_change", "Orientation change", it, "unitless", estimated(sensorData.sensorName(), "rotation_vector_magnitude")))
            }.orEmpty()

            else -> emptyList()
        }
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

        val durationMinutes = ((sensorData.endTimeEpochMillis - previousTime).coerceAtLeast(1)).toDouble() / 60_000.0
        val cadence = delta / durationMinutes
        return listOf(
            sensorData.toRecord(
                recordType = "steps",
                displayName = "Steps",
                value = delta,
                unit = "count",
                notes = direct("TYPE_STEP_COUNTER;baseline_delta")
            ),
            sensorData.toRecord(
                recordType = "steps_cadence",
                displayName = "Steps cadence",
                value = cadence,
                unit = "steps/min",
                notes = estimated("TYPE_STEP_COUNTER", "delta_steps_per_minute")
            )
        )
    }

    private fun pressureRecords(sensorData: SensorData, value: Double?): List<PghdRecordEntity> {
        val pressureHPa = value ?: return emptyList()
        val altitudeMeters = 44330.0 * (1.0 - (pressureHPa / STANDARD_PRESSURE_HPA).pow(1.0 / 5.255))
        val previousAltitude = lastPressureAltitudeMeters
        lastPressureAltitudeMeters = altitudeMeters

        val records = mutableListOf(
            sensorData.toRecord(
                recordType = "barometric_pressure",
                displayName = "Barometric pressure",
                value = pressureHPa,
                unit = "hPa",
                notes = direct("TYPE_PRESSURE")
            ),
            sensorData.toRecord(
                recordType = "elevation_estimate",
                displayName = "Elevation estimate",
                value = altitudeMeters,
                unit = "m",
                notes = estimated("TYPE_PRESSURE", "barometric_formula_standard_pressure_1013_25_hpa")
            )
        )

        if (previousAltitude != null) {
            val delta = altitudeMeters - previousAltitude
            if (delta >= MIN_ELEVATION_GAIN_METERS) {
                records += sensorData.toRecord(
                    recordType = "elevation_gained",
                    displayName = "Elevation gained",
                    value = delta,
                    unit = "m",
                    notes = estimated("TYPE_PRESSURE", "positive_barometric_altitude_delta")
                )
                records += sensorData.toRecord(
                    recordType = "floors_climbed",
                    displayName = "Floors climbed",
                    value = delta / METERS_PER_FLOOR,
                    unit = "floors",
                    notes = estimated("TYPE_PRESSURE", "elevation_gain_divided_by_3m_floor")
                )
            }
        }

        return records
    }

    private fun vectorMagnitude(sensorData: SensorData): Double? {
        val x = sensorData.valueX ?: return null
        val y = sensorData.valueY ?: return null
        val z = sensorData.valueZ ?: return null
        return sqrt((x * x + y * y + z * z).toDouble())
    }

    private fun gravityTilt(sensorData: SensorData): Double? {
        val x = sensorData.valueX?.toDouble() ?: return null
        val y = sensorData.valueY?.toDouble() ?: return null
        val z = sensorData.valueZ?.toDouble() ?: return null
        val horizontal = sqrt((x * x) + (y * y))
        return Math.toDegrees(atan2(horizontal, z))
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

    private fun SensorData.sensorName(): String = when (sensorType) {
        Sensor.TYPE_ACCELEROMETER -> "TYPE_ACCELEROMETER"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "TYPE_ACCELEROMETER_UNCALIBRATED"
        Sensor.TYPE_LINEAR_ACCELERATION -> "TYPE_LINEAR_ACCELERATION"
        Sensor.TYPE_GYROSCOPE -> "TYPE_GYROSCOPE"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "TYPE_GYROSCOPE_UNCALIBRATED"
        Sensor.TYPE_MAGNETIC_FIELD -> "TYPE_MAGNETIC_FIELD"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "TYPE_MAGNETIC_FIELD_UNCALIBRATED"
        Sensor.TYPE_ROTATION_VECTOR -> "TYPE_ROTATION_VECTOR"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "TYPE_GAME_ROTATION_VECTOR"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "TYPE_GEOMAGNETIC_ROTATION_VECTOR"
        else -> "TYPE_$sensorType"
    }

    private fun Double.toCompactText(): String =
        if (this % 1.0 == 0.0) this.toLong().toString() else "%.2f".format(this)

    private const val STANDARD_PRESSURE_HPA = 1013.25
    private const val MIN_ELEVATION_GAIN_METERS = 0.8
    private const val METERS_PER_FLOOR = 3.0
}
