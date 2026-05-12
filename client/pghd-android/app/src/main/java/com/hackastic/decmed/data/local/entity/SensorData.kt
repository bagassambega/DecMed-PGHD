package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single sensor sample stored in the local PGHD database.
 *
 * Schema design mirrors the Google Health Connect record model:
 *   https://developers.google.com/health/reference/rest/v1/users.dataSources.datasets
 *
 * Mapping to Health Connect concepts:
 *   - [dataType]            → DataType / data source stream type
 *                             (e.g. "com.google.heart_rate.bpm", "com.google.step_count.delta")
 *   - [startTimeEpochMillis]/[endTimeEpochMillis] → startTimeNanos / endTimeNanos
 *   - [value]               → dataPoint.value[0] (scalar, e.g. heart rate BPM)
 *   - [valueX/Y/Z]          → dataPoint.value[0/1/2] (vector, e.g. accelerometer axes)
 *   - [unit]                → dataPoint.value.fpVal unit descriptor
 *   - [accuracy]            → dataPoint.rawTimestampNanos accuracy qualifier (0–3)
 *   - [dataOrigin]          → DataSource.application.packageName
 *
 * Index on (dataType, endTimeEpochMillis) supports the common "latest N records
 * of a given type" query pattern without a full-table scan.
 *
 * Performance note:
 *   Scalar and vector fields are stored as separate columns rather than a
 *   serialised blob to allow SQL aggregation (AVG heart rate, SUM steps, etc.)
 *   without deserialisation overhead.
 */
@Entity(
    tableName = "sensor_data",
    indices = [Index(value = ["dataType", "endTimeEpochMillis"])]
)
data class SensorData(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Health Connect–style data type identifier.
     * Examples: "com.google.heart_rate.bpm", "com.google.step_count.delta",
     *           "com.google.acceleration.vector", "com.google.gyroscope.vector",
     *           "com.google.ambient_light.lux", "com.google.pressure.hPa"
     */
    val dataType: String,

    /** Android Sensor.TYPE_* constant — kept for low-level correlation. */
    val sensorType: Int,

    /** Epoch milliseconds for the start of the measurement window. */
    val startTimeEpochMillis: Long,

    /** Epoch milliseconds for the end of the measurement window. */
    val endTimeEpochMillis: Long,

    /**
     * Physical unit string (mirrors Health Connect fpVal unit annotation).
     * Examples: "bpm", "count", "m/s^2", "rad/s", "lux", "hPa", "°C", "%", "raw"
     */
    val unit: String,

    /**
     * Scalar value — populated for single-axis sensors.
     * Maps to dataPoint.value[0].fpVal in the Health Connect wire format.
     */
    val value: Float? = null,

    /**
     * X-axis / primary-axis value for vector sensors (accelerometer, gyroscope, etc.).
     * Maps to dataPoint.value[0].fpVal.
     */
    val valueX: Float? = null,

    /**
     * Y-axis value for vector sensors.
     * Maps to dataPoint.value[1].fpVal.
     */
    val valueY: Float? = null,

    /**
     * Z-axis value for vector sensors.
     * Maps to dataPoint.value[2].fpVal.
     */
    val valueZ: Float? = null,

    /**
     * Sensor accuracy at the time of capture (mirrors SensorEvent.accuracy):
     *   0 = SENSOR_STATUS_UNRELIABLE
     *   1 = SENSOR_STATUS_ACCURACY_LOW
     *   2 = SENSOR_STATUS_ACCURACY_MEDIUM
     *   3 = SENSOR_STATUS_ACCURACY_HIGH
     *
     * Analogous to the accuracy qualifier in a Health Connect raw data point.
     */
    val accuracy: Int = 3,

    /**
     * Package-name–style origin identifier.
     * Mirrors DataSource.application.packageName in Health Connect.
     * Defaults to the DecMed package; can be overridden for data imported
     * from external sources.
     */
    val dataOrigin: String = "com.hackastic.decmed"
)