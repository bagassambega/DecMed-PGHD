package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pghd_batch_data_points",
    foreignKeys = [
        ForeignKey(
            entity = PghdBatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["batchId"]),
        Index(value = ["measurementType", "timestampEpochMillis"])
    ]
)
data class PghdBatchDataPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val batchId: String,
    val measurementType: String,
    val timestamp: String,
    val timestampEpochMillis: Long,
    val valueJson: String,
    val unit: String,
    val source: String,
    val deviceType: String,
    val recordingMethod: String?
)
