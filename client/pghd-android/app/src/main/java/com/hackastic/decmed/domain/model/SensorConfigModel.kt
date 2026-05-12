package com.hackastic.decmed.domain.model

/**
 * Domain model representing a user's approval decision for a specific sensor.
 * Used as the communication contract between the UI and domain layers.
 */
data class SensorConfigModel(
    val sensorType: Int,
    val sensorName: String,
    val isApproved: Boolean,
    val healthDataDescription: String,
    val collectionIntervalMs: Int = 5000
)
