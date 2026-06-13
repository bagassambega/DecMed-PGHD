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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringConflicts(records: List<PghdRecordEntity>)

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

    @Query(
        """
        SELECT DISTINCT sourcePackageName FROM pghd_records
        WHERE sourceTag = :sourceTag
          AND sourcePackageName IS NOT NULL
        ORDER BY sourcePackageName ASC
        """
    )
    fun getSourcePackages(sourceTag: String = PghdRecordEntity.SOURCE_HEALTH_CONNECT): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM pghd_records")
    suspend fun getTotalCount(): Long

    @Query(
        """
        SELECT MAX(endTimeEpochMillis) FROM pghd_records
        WHERE sourceTag = :sourceTag
        """
    )
    suspend fun getLatestEndTimeMillis(sourceTag: String = PghdRecordEntity.SOURCE_HEALTH_CONNECT): Long?

    @Query(
        """
        SELECT * FROM pghd_records
        WHERE batchId IS NULL
        ORDER BY endTimeEpochMillis ASC
        """
    )
    suspend fun getUnbatchedRecords(): List<PghdRecordEntity>

    @Query(
        """
        SELECT * FROM pghd_records
        WHERE batchId IS NULL
        ORDER BY endTimeEpochMillis ASC
        """
    )
    fun observeUnbatchedRecords(): Flow<List<PghdRecordEntity>>

    @Query("UPDATE pghd_records SET batchId = :batchId WHERE uid IN (:recordIds)")
    suspend fun markRecordsBatched(recordIds: List<String>, batchId: String)
}
