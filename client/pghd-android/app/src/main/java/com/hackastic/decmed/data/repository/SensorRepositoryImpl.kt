package com.hackastic.decmed.data.repository

import com.hackastic.decmed.data.local.dao.SensorDao
import com.hackastic.decmed.data.local.entity.SensorData
import com.hackastic.decmed.domain.repository.SensorRepository
import kotlinx.coroutines.flow.Flow

class SensorRepositoryImpl(private val sensorDao: SensorDao) : SensorRepository {
    override fun getLatestData(limit: Int): Flow<List<SensorData>> =
        sensorDao.getLatestData(limit)

    override fun getLatestByDataType(dataType: String, limit: Int): Flow<List<SensorData>> =
        sensorDao.getLatestByDataType(dataType, limit)

    override fun getDistinctDataTypes(): Flow<List<String>> =
        sensorDao.getDistinctDataTypes()

    override suspend fun getTotalRecordCount(): Long =
        sensorDao.getTotalRecordCount()

    override suspend fun deleteOldData(timestampThreshold: Long) =
        sensorDao.deleteOldData(timestampThreshold)
}
