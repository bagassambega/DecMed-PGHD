package com.hackastic.decmed.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hackastic.decmed.data.local.entity.SensorData
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PGHD sensor records.
 *
 * Query design mirrors the Google Health Connect dataset query model:
 *   GET users.dataSources.datasets — filtered by dataType, startTime, endTime.
 *
 * Performance notes:
 *   - [insertAll] batches writes to reduce WAL pressure and I/O syscalls.
 *   - [getLatestByDataType] leverages the (dataType, endTimeEpochMillis) index
 *     created in SensorData for O(log n) lookups instead of full-table scans.
 *   - [getDataInRange] is the primary read path for charting / export; the
 *     composite index makes it efficient for per-type time-window queries.
 */
@Dao
interface SensorDao {

    // ── Writes ─────────────────────────────────────────────────────────────────

    /**
     * Batch-insert sensor records.
     * REPLACE strategy handles the (unlikely) case of an auto-generated PK
     * collision after a DB rollback.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<SensorData>)

    // ── Reads ──────────────────────────────────────────────────────────────────

    /**
     * Returns the most recent [limit] records across all data types,
     * ordered newest-first. Mirrors a broad Health Connect dataset read
     * with no dataType filter.
     */
    @Query("SELECT * FROM sensor_data ORDER BY endTimeEpochMillis DESC LIMIT :limit")
    fun getLatestData(limit: Int = 100): Flow<List<SensorData>>

    /**
     * Returns the most recent [limit] records for a specific Health Connect
     * data type (e.g. "com.google.heart_rate.bpm").
     * Mirrors: GET users.dataSources/{dataSourceId}/datasets filtered by dataType.
     */
    @Query(
        """
        SELECT * FROM sensor_data
        WHERE dataType = :dataType
        ORDER BY endTimeEpochMillis DESC
        LIMIT :limit
        """
    )
    fun getLatestByDataType(dataType: String, limit: Int = 50): Flow<List<SensorData>>

    /**
     * Returns all records for a given data type within a time window.
     * Mirrors Health Connect dataset range queries:
     *   startTimeMillis ≤ endTimeEpochMillis AND endTimeEpochMillis ≤ endTimeMillis
     */
    @Query(
        """
        SELECT * FROM sensor_data
        WHERE dataType = :dataType
          AND endTimeEpochMillis >= :startTimeMillis
          AND endTimeEpochMillis <= :endTimeMillis
        ORDER BY endTimeEpochMillis ASC
        """
    )
    fun getDataInRange(
        dataType: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Flow<List<SensorData>>

    /**
     * Returns distinct data types that have been recorded.
     * Useful for building the "available streams" list in the UI.
     */
    @Query("SELECT DISTINCT dataType FROM sensor_data ORDER BY dataType ASC")
    fun getDistinctDataTypes(): Flow<List<String>>

    // ── Maintenance ────────────────────────────────────────────────────────────

    /**
     * Purge records older than [timestampThreshold].
     * Call periodically to enforce the platform retention policy.
     */
    @Query("DELETE FROM sensor_data WHERE endTimeEpochMillis < :timestampThreshold")
    suspend fun deleteOldData(timestampThreshold: Long)

    /** Total record count — used for storage dashboard / diagnostics. */
    @Query("SELECT COUNT(*) FROM sensor_data")
    suspend fun getTotalRecordCount(): Long
}