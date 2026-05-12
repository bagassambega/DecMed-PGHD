package com.hackastic.decmed.utils

import android.hardware.Sensor

/**
 * Comprehensive mapping of Android sensor types to health data capabilities.
 *
 * Design rationale:
 * - This is a static lookup table, not a database table, because the mapping is
 *   determined by Android API definitions and clinical literature, not user data.
 * - Only sensor types available from API 30+ (the project's minSdk) are included.
 * - Deprecated sensor types (TYPE_ORIENTATION, TYPE_TEMPERATURE) are excluded.
 *
 * Sources:
 * - Android Sensor API documentation (developer.android.com)
 * - WHO Digital Health Guidelines for PGHD
 */
object SensorHealthDataMap {

    data class SensorHealthInfo(
        val displayName: String,
        val healthData: List<String>,
        val clinicalRelevance: String
    )

    val allSensorTypes: Map<Int, SensorHealthInfo> = mapOf(
        Sensor.TYPE_ACCELEROMETER to SensorHealthInfo(
            displayName = "Accelerometer",
            healthData = listOf(
                "Step counting",
                "Fall detection",
                "Gait analysis",
                "Tremor detection",
                "Physical activity intensity"
            ),
            clinicalRelevance = "Movement disorders, rehabilitation monitoring, sedentary behavior assessment"
        ),
        Sensor.TYPE_GYROSCOPE to SensorHealthInfo(
            displayName = "Gyroscope",
            healthData = listOf(
                "Balance assessment",
                "Posture analysis",
                "Tremor characterization",
                "Rotation-based gesture detection"
            ),
            clinicalRelevance = "Neurological assessment, fall risk evaluation, vestibular function"
        ),
        Sensor.TYPE_MAGNETIC_FIELD to SensorHealthInfo(
            displayName = "Magnetometer",
            healthData = listOf(
                "Compass heading",
                "Geomagnetic field strength",
                "Location context for activity tracking"
            ),
            clinicalRelevance = "Contextual data for outdoor activity and navigation patterns"
        ),
        Sensor.TYPE_LIGHT to SensorHealthInfo(
            displayName = "Ambient Light Sensor",
            healthData = listOf(
                "Ambient light exposure level",
                "Circadian rhythm estimation",
                "Screen brightness context"
            ),
            clinicalRelevance = "Sleep hygiene analysis, light therapy monitoring, seasonal affective disorder tracking"
        ),
        Sensor.TYPE_PRESSURE to SensorHealthInfo(
            displayName = "Barometric Pressure Sensor",
            healthData = listOf(
                "Atmospheric pressure",
                "Altitude estimation",
                "Floor/stair climbing detection"
            ),
            clinicalRelevance = "Activity context enrichment, environmental condition monitoring, respiratory trigger tracking"
        ),
        Sensor.TYPE_PROXIMITY to SensorHealthInfo(
            displayName = "Proximity Sensor",
            healthData = listOf(
                "Screen-to-face proximity events",
                "Phone usage duration patterns"
            ),
            clinicalRelevance = "Phone usage behavior, sleep quality estimation (phone-near-face during sleep)"
        ),
        Sensor.TYPE_GRAVITY to SensorHealthInfo(
            displayName = "Gravity Sensor",
            healthData = listOf(
                "Device orientation relative to gravity",
                "Body tilt angle estimation",
                "Lying/standing/sitting posture inference"
            ),
            clinicalRelevance = "Posture monitoring, bed positioning detection, activity classification"
        ),
        Sensor.TYPE_LINEAR_ACCELERATION to SensorHealthInfo(
            displayName = "Linear Acceleration Sensor",
            healthData = listOf(
                "Impact detection (gravity-compensated)",
                "Vibration analysis",
                "Sudden movement detection"
            ),
            clinicalRelevance = "Fall detection, seizure detection, impact injury assessment"
        ),
        Sensor.TYPE_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Rotation Vector Sensor",
            healthData = listOf(
                "3D device orientation",
                "Head tracking (when phone is head-mounted)",
                "Complex motion pattern recognition"
            ),
            clinicalRelevance = "Balance and vestibular assessment, complex movement analysis"
        ),
        Sensor.TYPE_RELATIVE_HUMIDITY to SensorHealthInfo(
            displayName = "Relative Humidity Sensor",
            healthData = listOf(
                "Environmental humidity level",
                "Heat index estimation (combined with temperature)"
            ),
            clinicalRelevance = "Respiratory condition trigger monitoring, environmental comfort assessment"
        ),
        Sensor.TYPE_AMBIENT_TEMPERATURE to SensorHealthInfo(
            displayName = "Ambient Temperature Sensor",
            healthData = listOf(
                "Environmental temperature",
                "Heat stress risk estimation"
            ),
            clinicalRelevance = "Hypothermia/heat stress risk, environmental health factor tracking"
        ),
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED to SensorHealthInfo(
            displayName = "Magnetometer (Uncalibrated)",
            healthData = listOf(
                "Raw magnetic field data",
                "Magnetic interference detection"
            ),
            clinicalRelevance = "Higher-fidelity geomagnetic data for research-grade location context"
        ),
        Sensor.TYPE_GAME_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Game Rotation Vector",
            healthData = listOf(
                "Drift-free relative orientation",
                "Short-term motion tracking"
            ),
            clinicalRelevance = "Exercise form analysis, rehabilitation movement tracking"
        ),
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED to SensorHealthInfo(
            displayName = "Gyroscope (Uncalibrated)",
            healthData = listOf(
                "Raw angular velocity with drift estimate",
                "High-fidelity rotation data"
            ),
            clinicalRelevance = "Research-grade tremor analysis, precise rotation measurement"
        ),
        Sensor.TYPE_SIGNIFICANT_MOTION to SensorHealthInfo(
            displayName = "Significant Motion Detector",
            healthData = listOf(
                "Motion trigger events (walking, driving, etc.)",
                "Sedentary-to-active state transitions"
            ),
            clinicalRelevance = "Sedentary behavior monitoring, activity bout detection"
        ),
        Sensor.TYPE_STEP_DETECTOR to SensorHealthInfo(
            displayName = "Step Detector",
            healthData = listOf(
                "Individual step events with timestamps",
                "Walking bout identification",
                "Cadence estimation"
            ),
            clinicalRelevance = "Gait analysis, walking pattern irregularity detection, sedentary behavior"
        ),
        Sensor.TYPE_STEP_COUNTER to SensorHealthInfo(
            displayName = "Step Counter",
            healthData = listOf(
                "Cumulative step count since last reboot",
                "Daily step totals",
                "Weekly activity trends"
            ),
            clinicalRelevance = "Physical activity level tracking, WHO activity guideline compliance"
        ),
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR to SensorHealthInfo(
            displayName = "Geomagnetic Rotation Vector",
            healthData = listOf(
                "Low-power orientation estimation",
                "Heading relative to magnetic north"
            ),
            clinicalRelevance = "Battery-efficient orientation for long-term posture monitoring"
        ),
        Sensor.TYPE_HEART_RATE to SensorHealthInfo(
            displayName = "Heart Rate Sensor",
            healthData = listOf(
                "Heart rate in BPM",
                "Resting heart rate trends",
                "Heart rate during activity"
            ),
            clinicalRelevance = "Cardiovascular health monitoring, arrhythmia screening, fitness assessment"
        ),
        Sensor.TYPE_STATIONARY_DETECT to SensorHealthInfo(
            displayName = "Stationary Detector",
            healthData = listOf(
                "Device stationary state detection",
                "Prolonged inactivity detection"
            ),
            clinicalRelevance = "Sedentary behavior monitoring, inactivity alerts"
        ),
        Sensor.TYPE_MOTION_DETECT to SensorHealthInfo(
            displayName = "Motion Detector",
            healthData = listOf(
                "Motion onset detection",
                "Activity state changes"
            ),
            clinicalRelevance = "Activity onset detection, wake-from-rest monitoring"
        ),
        Sensor.TYPE_HEART_BEAT to SensorHealthInfo(
            displayName = "Heart Beat Sensor",
            healthData = listOf(
                "Individual heartbeat events",
                "Inter-beat interval estimation",
                "Heart rate variability (HRV) data"
            ),
            clinicalRelevance = "Arrhythmia screening, stress level assessment, autonomic nervous system analysis"
        ),
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT to SensorHealthInfo(
            displayName = "Off-Body Detector",
            healthData = listOf(
                "On-body vs. off-body state",
                "Wear compliance tracking"
            ),
            clinicalRelevance = "Ensuring continuous data collection, wear-time compliance for clinical studies"
        ),
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to SensorHealthInfo(
            displayName = "Accelerometer (Uncalibrated)",
            healthData = listOf(
                "Raw acceleration with bias estimate",
                "High-fidelity motion data"
            ),
            clinicalRelevance = "Research-grade motion analysis, precise vibration characterization"
        ),
        Sensor.TYPE_HINGE_ANGLE to SensorHealthInfo(
            displayName = "Hinge Angle Sensor",
            healthData = listOf(
                "Device fold angle",
                "Usage posture inference"
            ),
            clinicalRelevance = "Device usage ergonomics, foldable device interaction patterns"
        )
    )
}
