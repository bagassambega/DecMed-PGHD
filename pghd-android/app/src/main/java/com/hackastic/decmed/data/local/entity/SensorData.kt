package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single batch of sensor data collected from the device.
 * Memory/Performance Implication: 
 * Using a flat table structure optimizes SQLite insertion speeds.
 * The timestamp acts as part of our time-series analysis foundation.
 */
@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorType: Int, // e.g., Sensor.TYPE_ACCELEROMETER
    val timestamp: Long,
    val value0: Float,
    val value1: Float,
    val value2: Float
)
