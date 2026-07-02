package com.hackastic.decmed.utils

import android.hardware.Sensor
import com.hackastic.decmed.domain.model.HealthDataTypeOption

object SensorHealthDataMap {

    data class SensorHealthInfo(
        val displayName: String,
        val healthDataTypes: List<HealthDataTypeOption>,
        val clinicalRelevance: String
    ) {
        val healthData: List<String>
            get() = healthDataTypes.map { option ->
                val method = if (option.isEstimated) "derived" else "direct"
                "${option.displayName} (${option.unit}, $method)"
            }
    }

    fun healthDataTypesFor(sensorType: Int): List<HealthDataTypeOption> =
        allSensorTypes[sensorType]?.healthDataTypes.orEmpty()

    fun recordTypesFor(sensorType: Int): Set<String> =
        healthDataTypesFor(sensorType).map { it.recordType }.toSet()

    private fun direct(recordType: String, displayName: String, unit: String) =
        HealthDataTypeOption(recordType, displayName, unit, isEstimated = false)

    private fun derived(recordType: String, displayName: String, unit: String) =
        HealthDataTypeOption(recordType, displayName, unit, isEstimated = true)

    val allSensorTypes: Map<Int, SensorHealthInfo> = mapOf(
        Sensor.TYPE_HEART_RATE to SensorHealthInfo(
            displayName = "Heart Rate Sensor",
            healthDataTypes = listOf(
                direct("heart_rate", "Heart rate", "bpm")
            ),
            clinicalRelevance = "Cardiovascular health monitoring, activity response, and resting heart-rate trends."
        ),
        Sensor.TYPE_HEART_BEAT to SensorHealthInfo(
            displayName = "Heart Beat Sensor",
            healthDataTypes = listOf(
                derived("heart_rate_variability", "Heart rate variability", "ms")
            ),
            clinicalRelevance = "Heart-beat interval can be converted into HRV when the sensor exposes a usable interval value."
        ),
        Sensor.TYPE_STEP_DETECTOR to SensorHealthInfo(
            displayName = "Step Detector",
            healthDataTypes = listOf(
                direct("steps", "Steps", "count")
            ),
            clinicalRelevance = "Physical activity tracking, gait monitoring, and walking-bout detection."
        ),
        Sensor.TYPE_STEP_COUNTER to SensorHealthInfo(
            displayName = "Step Counter",
            healthDataTypes = listOf(
                direct("steps", "Steps", "count")
            ),
            clinicalRelevance = "Daily activity level and longitudinal mobility trends."
        ),
        Sensor.TYPE_PRESSURE to SensorHealthInfo(
            displayName = "Barometric Pressure Sensor",
            healthDataTypes = listOf(
                direct("barometric_pressure", "Barometric pressure", "hPa")
            ),
            clinicalRelevance = "Environmental pressure context that may be relevant for respiratory or environmental-trigger review."
        ),
        Sensor.TYPE_LIGHT to SensorHealthInfo(
            displayName = "Ambient Light Sensor",
            healthDataTypes = listOf(
                direct("ambient_light", "Ambient light", "lux")
            ),
            clinicalRelevance = "Light exposure, sleep hygiene context, and circadian rhythm estimation."
        ),
        Sensor.TYPE_AMBIENT_TEMPERATURE to SensorHealthInfo(
            displayName = "Ambient Temperature Sensor",
            healthDataTypes = listOf(
                direct("environmental_temperature", "Environmental temperature", "C")
            ),
            clinicalRelevance = "Environmental health factors, heat stress risk, and respiratory trigger context."
        ),
        Sensor.TYPE_RELATIVE_HUMIDITY to SensorHealthInfo(
            displayName = "Relative Humidity Sensor",
            healthDataTypes = listOf(
                direct("environmental_humidity", "Environmental humidity", "%")
            ),
            clinicalRelevance = "Respiratory condition trigger monitoring and environmental comfort assessment."
        ),
        Sensor.TYPE_PROXIMITY to SensorHealthInfo(
            displayName = "Proximity Sensor",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because proximity is device-context data, not a health measurement."
        ),
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT to SensorHealthInfo(
            displayName = "Off-Body Detector",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because this is collection-quality context, not patient health data."
        ),
        Sensor.TYPE_SIGNIFICANT_MOTION to SensorHealthInfo(
            displayName = "Significant Motion Detector",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because motion events are not stored as standalone health measurements."
        ),
        Sensor.TYPE_STATIONARY_DETECT to SensorHealthInfo(
            displayName = "Stationary Detector",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because stationary events are device motion context, not health data."
        ),
        Sensor.TYPE_MOTION_DETECT to SensorHealthInfo(
            displayName = "Motion Detector",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because motion events are not stored as standalone health measurements."
        ),
        Sensor.TYPE_ACCELEROMETER to SensorHealthInfo(
            displayName = "Accelerometer",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because raw acceleration and magnitude are not sent as patient health measurements."
        ),
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to SensorHealthInfo(
            displayName = "Accelerometer (Uncalibrated)",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because raw acceleration and magnitude are not sent as patient health measurements."
        ),
        Sensor.TYPE_LINEAR_ACCELERATION to SensorHealthInfo(
            displayName = "Linear Acceleration Sensor",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because raw acceleration and magnitude are not sent as patient health measurements."
        ),
        Sensor.TYPE_GRAVITY to SensorHealthInfo(
            displayName = "Gravity Sensor",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because gravity/tilt values are device orientation context."
        ),
        Sensor.TYPE_GYROSCOPE to SensorHealthInfo(
            displayName = "Gyroscope",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because raw angular velocity and rotation magnitude are not sent as health measurements."
        ),
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED to SensorHealthInfo(
            displayName = "Gyroscope (Uncalibrated)",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because raw angular velocity and rotation magnitude are not sent as health measurements."
        ),
        Sensor.TYPE_MAGNETIC_FIELD to SensorHealthInfo(
            displayName = "Magnetometer",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No patient-facing PGHD conversion is enabled because raw magnetic-field values are not directly interpretable as health data."
        ),
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED to SensorHealthInfo(
            displayName = "Magnetometer (Uncalibrated)",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No patient-facing PGHD conversion is enabled because raw magnetic-field values are not directly interpretable as health data."
        ),
        Sensor.TYPE_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Rotation Vector Sensor",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because rotation-vector values are device orientation context."
        ),
        Sensor.TYPE_GAME_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Game Rotation Vector",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because rotation-vector values are device orientation context."
        ),
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Geomagnetic Rotation Vector",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because rotation-vector values are device orientation context."
        ),
        Sensor.TYPE_HINGE_ANGLE to SensorHealthInfo(
            displayName = "Hinge Angle Sensor",
            healthDataTypes = emptyList(),
            clinicalRelevance = "No PGHD conversion is enabled because hinge angle is device form-factor context."
        )
    )
}
