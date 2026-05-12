package com.hackastic.decmed.domain.model

/**
 * Domain model representing a sensor's metadata and its health data capabilities.
 * This is a pure domain object — no Room or Android framework dependencies.
 *
 * type: Maps to android.hardware.Sensor.TYPE_* constants.
 * isAvailable: Whether the physical device has this sensor.
 * healthDataCapabilities: Human-readable list of health data derivable from this sensor.
 */
data class SensorInfo(
    val type: Int,
    val name: String,
    val isAvailable: Boolean,
    val healthDataCapabilities: List<String>,
    val clinicalRelevance: String
)
