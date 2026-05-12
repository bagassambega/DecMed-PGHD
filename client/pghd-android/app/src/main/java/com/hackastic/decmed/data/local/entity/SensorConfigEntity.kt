package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists the user's sensor data-collection approval decisions.
 * 
 * Design rationale:
 * - sensorType is the PK because each Android sensor type (Sensor.TYPE_*) is unique per device.
 * - isApproved is mutable: the user can revoke or grant consent at any time via Settings.
 * - healthDataDescription stores the pre-computed description so we avoid re-mapping at read time.
 * - lastModified enables audit-trail queries (important for health data compliance).
 */
@Entity(tableName = "sensor_config")
data class SensorConfigEntity(
    @PrimaryKey val sensorType: Int,
    val sensorName: String,
    val isApproved: Boolean,
    val healthDataDescription: String,
    val collectionIntervalMs: Int,
    val lastModified: Long
)
