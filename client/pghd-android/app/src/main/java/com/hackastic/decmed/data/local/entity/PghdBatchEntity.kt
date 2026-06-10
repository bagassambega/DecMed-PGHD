package com.hackastic.decmed.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pghd_batches",
    indices = [
        Index(value = ["patientId", "createdAtEpochMillis"]),
        Index(value = ["startTimestamp", "endTimestamp"]),
        Index(value = ["status", "retryCount"])
    ]
)
data class PghdBatchEntity(
    @PrimaryKey
    val batchId: String,
    val patientId: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val encPghd: String = "",
    val encAesKeyNonce: String = "",
    val capsule: String = "",
    val hCipher: String = "",
    val pghdOuterSignature: String = "",
    val triggerReason: String = TRIGGER_TIME_BASED,
    val status: String = STATUS_PENDING,
    val retryCount: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val lastAttemptEpochMillis: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
        const val STATUS_PERMANENT_FAILURE = "permanent_failure"
        const val TRIGGER_TIME_BASED = "time_based"
        const val TRIGGER_SIZE_THRESHOLD = "size_threshold"
    }
}
