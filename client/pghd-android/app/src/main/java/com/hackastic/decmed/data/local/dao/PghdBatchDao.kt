package com.hackastic.decmed.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hackastic.decmed.data.local.entity.PghdBatchDataPointEntity
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
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
    open suspend fun savePayload(payload: PghdBatchPayload) {
        upsertBatch(PghdPayloadConverter.payloadToBatchEntity(payload))
        deleteDataPointsForBatch(payload.batchId)
        insertDataPoints(PghdPayloadConverter.payloadToDataPointEntities(payload))
    }

    @Query("SELECT * FROM pghd_batches ORDER BY createdAtEpochMillis DESC")
    abstract fun getBatches(): Flow<List<PghdBatchEntity>>

    @Query("SELECT * FROM pghd_batch_data_points WHERE batchId = :batchId ORDER BY timestampEpochMillis ASC")
    abstract fun getDataPointsForBatch(batchId: String): Flow<List<PghdBatchDataPointEntity>>

    @Query("SELECT payloadJson FROM pghd_batches WHERE batchId = :batchId")
    abstract suspend fun getPayloadJson(batchId: String): String?

    @Query("DELETE FROM pghd_batches WHERE batchId = :batchId")
    abstract suspend fun deleteBatch(batchId: String)
}
