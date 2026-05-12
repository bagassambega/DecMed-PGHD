package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.data.local.entity.SensorData
import kotlinx.coroutines.flow.Flow

interface SensorRepository {
    fun getLatestData(limit: Int = 100): Flow<List<SensorData>>
    fun getLatestByDataType(dataType: String, limit: Int = 50): Flow<List<SensorData>>
    fun getDistinctDataTypes(): Flow<List<String>>
    suspend fun getTotalRecordCount(): Long
    suspend fun deleteOldData(timestampThreshold: Long)
}
