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
                direct("heart_beat", "Heart beat event", "event"),
                derived("heart_rate_variability", "Heart rate variability", "ms")
            ),
            clinicalRelevance = "Arrhythmia screening, stress assessment, and autonomic nervous system monitoring."
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
                direct("steps", "Steps", "count"),
                derived("steps_cadence", "Steps cadence", "steps/min")
            ),
            clinicalRelevance = "Daily activity level, longitudinal mobility trends, and cadence-based gait monitoring."
        ),
        Sensor.TYPE_SIGNIFICANT_MOTION to SensorHealthInfo(
            displayName = "Significant Motion Detector",
            healthDataTypes = listOf(
                direct("activity_event", "Activity event", "event")
            ),
            clinicalRelevance = "Sedentary-to-active transitions and activity bout detection."
        ),
        Sensor.TYPE_STATIONARY_DETECT to SensorHealthInfo(
            displayName = "Stationary Detector",
            healthDataTypes = listOf(
                direct("activity_event", "Activity event", "event")
            ),
            clinicalRelevance = "Sedentary behavior monitoring and prolonged inactivity detection."
        ),
        Sensor.TYPE_MOTION_DETECT to SensorHealthInfo(
            displayName = "Motion Detector",
            healthDataTypes = listOf(
                direct("activity_event", "Activity event", "event")
            ),
            clinicalRelevance = "Activity onset detection and rest-to-motion monitoring."
        ),
        Sensor.TYPE_PRESSURE to SensorHealthInfo(
            displayName = "Barometric Pressure Sensor",
            healthDataTypes = listOf(
                direct("barometric_pressure", "Barometric pressure", "hPa"),
                derived("elevation_estimate", "Elevation estimate", "m"),
                derived("elevation_gained", "Elevation gained", "m"),
                derived("floors_climbed", "Floors climbed", "floors")
            ),
            clinicalRelevance = "Stair climbing, activity context enrichment, and environmental trigger monitoring."
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
            healthDataTypes = listOf(
                direct("proximity", "Proximity", "cm")
            ),
            clinicalRelevance = "Phone-near-body context and usage behavior around sleep or activity sessions."
        ),
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT to SensorHealthInfo(
            displayName = "Off-Body Detector",
            healthDataTypes = listOf(
                direct("wear_status", "Wear status", "state")
            ),
            clinicalRelevance = "Wear-time compliance and confidence in continuous sensor collection."
        ),
        Sensor.TYPE_ACCELEROMETER to SensorHealthInfo(
            displayName = "Accelerometer",
            healthDataTypes = listOf(
                derived("movement_intensity", "Movement intensity", "m/s^2")
            ),
            clinicalRelevance = "Physical activity intensity, fall screening, gait analysis, and tremor monitoring."
        ),
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to SensorHealthInfo(
            displayName = "Accelerometer (Uncalibrated)",
            healthDataTypes = listOf(
                derived("movement_intensity", "Movement intensity", "m/s^2")
            ),
            clinicalRelevance = "Research-grade motion analysis after conversion into interpretable movement indicators."
        ),
        Sensor.TYPE_LINEAR_ACCELERATION to SensorHealthInfo(
            displayName = "Linear Acceleration Sensor",
            healthDataTypes = listOf(
                derived("movement_intensity", "Movement intensity", "m/s^2")
            ),
            clinicalRelevance = "Impact detection, sudden movement detection, and exercise movement intensity."
        ),
        Sensor.TYPE_GRAVITY to SensorHealthInfo(
            displayName = "Gravity Sensor",
            healthDataTypes = listOf(
                derived("tilt_angle", "Tilt angle", "degrees")
            ),
            clinicalRelevance = "Posture monitoring, body tilt estimation, and lying or sitting context inference."
        ),
        Sensor.TYPE_GYROSCOPE to SensorHealthInfo(
            displayName = "Gyroscope",
            healthDataTypes = listOf(
                derived("rotation_intensity", "Rotation intensity", "rad/s")
            ),
            clinicalRelevance = "Balance assessment, tremor characterization, and rotational movement monitoring."
        ),
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED to SensorHealthInfo(
            displayName = "Gyroscope (Uncalibrated)",
            healthDataTypes = listOf(
                derived("rotation_intensity", "Rotation intensity", "rad/s")
            ),
            clinicalRelevance = "Rotation measurement after conversion into interpretable motion indicators."
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
            healthDataTypes = listOf(
                derived("orientation_change", "Orientation change", "unitless")
            ),
            clinicalRelevance = "Complex motion pattern recognition, balance analysis, and device orientation context."
        ),
        Sensor.TYPE_GAME_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Game Rotation Vector",
            healthDataTypes = listOf(
                derived("orientation_change", "Orientation change", "unitless")
            ),
            clinicalRelevance = "Short-term exercise form analysis and rehabilitation movement tracking."
        ),
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Geomagnetic Rotation Vector",
            healthDataTypes = listOf(
                derived("orientation_change", "Orientation change", "unitless")
            ),
            clinicalRelevance = "Battery-efficient orientation tracking for long-running posture monitoring."
        ),
        Sensor.TYPE_HINGE_ANGLE to SensorHealthInfo(
            displayName = "Hinge Angle Sensor",
            healthDataTypes = listOf(
                direct("device_hinge_angle", "Device hinge angle", "degrees")
            ),
            clinicalRelevance = "Device usage posture and ergonomic context on foldable devices."
        )
    )
}
