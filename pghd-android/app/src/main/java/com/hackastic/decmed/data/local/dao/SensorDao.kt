package com.hackastic.decmed.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hackastic.decmed.data.local.entity.SensorData
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Sensor Data.
 * Memory/Performance Implication: 
 * We use `insertAll` to allow batch insertion of sensor data arrays. 
 * This reduces disk I/O operations compared to single inserts, saving battery and CPU.
 */
@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<SensorData>)

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT 100")
    fun getLatestData(): Flow<List<SensorData>>

    @Query("DELETE FROM sensor_data WHERE timestamp < :timestampThreshold")
    suspend fun deleteOldData(timestampThreshold: Long)
}
