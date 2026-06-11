package com.hackastic.decmed.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hackastic.decmed.data.local.entity.PghdBatchDataPointEntity
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PghdBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertBatch(batch: PghdBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertDataPoints(dataPoints: List<PghdBatchDataPointEntity>)

    @Query("DELETE FROM pghd_batch_data_points WHERE batchId = :batchId")
    protected abstract suspend fun deleteDataPointsForBatch(batchId: String)

    @Transaction
    open suspend fun saveEnvelope(payload: PghdBatchPayload, envelope: PghdSecureEnvelope) {
        upsertBatch(PghdPayloadConverter.payloadToBatchEntity(payload).copy(
            encPghd = envelope.encPghd,
            encAesKeyNonce = envelope.encAesKeyNonce,
            capsule = envelope.capsule,
            hCipher = envelope.hCipher,
            pghdOuterSignature = envelope.pghdOuterSignature
        ))
        deleteDataPointsForBatch(payload.batchId)
        insertDataPoints(PghdPayloadConverter.payloadToDataPointEntities(payload))
    }

    @Query("SELECT * FROM pghd_batches ORDER BY createdAtEpochMillis DESC")
    abstract fun getBatches(): Flow<List<PghdBatchEntity>>

    @Query("SELECT * FROM pghd_batches WHERE batchId = :batchId LIMIT 1")
    abstract suspend fun getBatch(batchId: String): PghdBatchEntity?

    @Query("SELECT * FROM pghd_batch_data_points WHERE batchId = :batchId ORDER BY timestampEpochMillis ASC")
    abstract fun getDataPointsForBatch(batchId: String): Flow<List<PghdBatchDataPointEntity>>

    @Query("DELETE FROM pghd_batches WHERE batchId = :batchId")
    abstract suspend fun deleteBatch(batchId: String)

    @Query(
        """
        SELECT * FROM pghd_batches
        WHERE status IN (:statuses)
        ORDER BY createdAtEpochMillis ASC
        """
    )
    abstract suspend fun getBatchesByStatus(statuses: List<String>): List<PghdBatchEntity>

    @Query(
        """
        UPDATE pghd_batches
        SET status = :status,
            retryCount = :retryCount,
            lastAttemptEpochMillis = :lastAttemptEpochMillis
        WHERE batchId = :batchId
        """
    )
    abstract suspend fun updateDeliveryState(
        batchId: String,
        status: String,
        retryCount: Int,
        lastAttemptEpochMillis: Long
    )

    @Query(
        """
        UPDATE pghd_batches
        SET status = :status,
            lastAttemptEpochMillis = :lastAttemptEpochMillis
        WHERE batchId = :batchId
        """
    )
    abstract suspend fun markDeliveryInProgress(
        batchId: String,
        status: String,
        lastAttemptEpochMillis: Long
    )

    @Query(
        """
        UPDATE pghd_batches
        SET status = :newStatus
        WHERE status = :pendingStatus
          AND (lastAttemptEpochMillis IS NULL OR lastAttemptEpochMillis < :staleBeforeEpochMillis)
        """
    )
    abstract suspend fun resetStalePendingBatches(
        pendingStatus: String,
        newStatus: String,
        staleBeforeEpochMillis: Long
    )
}
