package com.hackastic.decmed.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hackastic.decmed.data.local.entity.SensorConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for sensor configuration persistence.
 *
 * Design notes:
 * - getAllConfigs() returns a Flow so the UI layer reactively updates
 *   when the user modifies their sensor approval from the Settings screen.
 * - updateApproval() targets a single row by sensorType, avoiding a full table rewrite.
 * - insertAll uses REPLACE strategy so re-enumeration overwrites stale entries.
 */
@Dao
interface SensorConfigDao {
    @Query("SELECT * FROM sensor_config ORDER BY sensorName ASC")
    fun getAllConfigs(): Flow<List<SensorConfigEntity>>

    @Query("SELECT * FROM sensor_config WHERE isApproved = 1")
    fun getApprovedConfigs(): Flow<List<SensorConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<SensorConfigEntity>)

    @Query("UPDATE sensor_config SET isApproved = :approved, lastModified = :timestamp WHERE sensorType = :sensorType")
    suspend fun updateApproval(sensorType: Int, approved: Boolean, timestamp: Long)

    @Query("SELECT COUNT(*) FROM sensor_config")
    suspend fun getConfigCount(): Int
}
