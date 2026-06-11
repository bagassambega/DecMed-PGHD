package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pghd_records",
    indices = [
        Index(value = ["recordType", "endTimeEpochMillis"]),
        Index(value = ["sourceTag"])
    ]
)
data class PghdRecordEntity(
    @PrimaryKey
    val uid: String,
    val recordType: String,
    val displayName: String,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long,
    val unit: String,
    val valueText: String,
    val numericValue: Double? = null,
    val sourceTag: String,
    val sourcePackageName: String? = null,
    val notes: String? = null,
    val syncedAtEpochMillis: Long = System.currentTimeMillis(),
    val batchId: String? = null
) {
    companion object {
        const val SOURCE_HEALTH_CONNECT = "Health Connect"
        const val SOURCE_MANUAL = "Manual input"
        const val SOURCE_PHONE_SENSOR = "phone_sensor"
        const val SOURCE_ANDROID_SENSOR = SOURCE_PHONE_SENSOR
    }
}
