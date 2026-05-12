package com.hackastic.decmed.data.repository

import com.hackastic.decmed.data.local.dao.SensorConfigDao
import com.hackastic.decmed.data.local.entity.SensorConfigEntity
import com.hackastic.decmed.domain.model.SensorConfigModel
import com.hackastic.decmed.domain.repository.SensorConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of [SensorConfigRepository].
 *
 * This class bridges the domain layer and the Room persistence layer.
 * It maps between domain models (SensorConfigModel) and Room entities (SensorConfigEntity).
 * The mapping is done inline here rather than in a separate mapper class because
 * the transformation is trivial (field-to-field copy) and doesn't warrant the indirection.
 */
class SensorConfigRepositoryImpl(
    private val sensorConfigDao: SensorConfigDao
) : SensorConfigRepository {

    override fun getAllConfigs(): Flow<List<SensorConfigModel>> {
        return sensorConfigDao.getAllConfigs().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getApprovedConfigs(): Flow<List<SensorConfigModel>> {
        return sensorConfigDao.getApprovedConfigs().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveConfigs(configs: List<SensorConfigModel>) {
        val entities = configs.map { it.toEntity() }
        sensorConfigDao.insertAll(entities)
    }

    override suspend fun updateApproval(sensorType: Int, approved: Boolean) {
        sensorConfigDao.updateApproval(
            sensorType = sensorType,
            approved = approved,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun hasExistingConfig(): Boolean {
        return sensorConfigDao.getConfigCount() > 0
    }

    // --- Mapping functions ---

    private fun SensorConfigEntity.toDomainModel(): SensorConfigModel {
        return SensorConfigModel(
            sensorType = sensorType,
            sensorName = sensorName,
            isApproved = isApproved,
            healthDataDescription = healthDataDescription,
            collectionIntervalMs = collectionIntervalMs
        )
    }

    private fun SensorConfigModel.toEntity(): SensorConfigEntity {
        return SensorConfigEntity(
            sensorType = sensorType,
            sensorName = sensorName,
            isApproved = isApproved,
            healthDataDescription = healthDataDescription,
            collectionIntervalMs = collectionIntervalMs,
            lastModified = System.currentTimeMillis()
        )
    }
}
