package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pghd_batches",
    indices = [
        Index(value = ["patientId", "createdAtEpochMillis"]),
        Index(value = ["startTimeEpochMillis", "endTimeEpochMillis"])
    ]
)
data class PghdBatchEntity(
    @PrimaryKey
    val batchId: String,
    val schemaVersion: String,
    val patientId: String,
    val sourceDeviceType: String,
    val sourceDevicePlatform: String,
    val sourceDeviceAppVersion: String,
    val sourceDeviceManufacturer: String,
    val sourceDeviceModel: String,
    val startTimestamp: String,
    val endTimestamp: String,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long,
    val payloadJson: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
