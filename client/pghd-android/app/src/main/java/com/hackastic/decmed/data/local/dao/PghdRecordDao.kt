package com.hackastic.decmed.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PghdRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<PghdRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PghdRecordEntity)

    @Query("SELECT * FROM pghd_records ORDER BY endTimeEpochMillis DESC LIMIT :limit")
    fun getLatest(limit: Int = 250): Flow<List<PghdRecordEntity>>

    @Query(
        """
        SELECT * FROM pghd_records
        WHERE (:sourceTag IS NULL OR sourceTag = :sourceTag)
          AND (:recordType IS NULL OR recordType = :recordType)
        ORDER BY endTimeEpochMillis DESC
        LIMIT :limit
        """
    )
    fun getFiltered(
        sourceTag: String?,
        recordType: String?,
        limit: Int = 250
    ): Flow<List<PghdRecordEntity>>

    @Query("SELECT DISTINCT recordType FROM pghd_records ORDER BY recordType ASC")
    fun getRecordTypes(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM pghd_records")
    suspend fun getTotalCount(): Long
}
