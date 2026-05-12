package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.domain.model.SensorConfigModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for sensor configuration persistence.
 *
 * Design rationale:
 * This interface lives in the domain layer so the domain/use-case layer
 * depends only on abstractions, not on Room or any persistence framework.
 * The data layer provides the concrete implementation (Dependency Inversion Principle).
 */
interface SensorConfigRepository {
    fun getAllConfigs(): Flow<List<SensorConfigModel>>
    fun getApprovedConfigs(): Flow<List<SensorConfigModel>>
    suspend fun saveConfigs(configs: List<SensorConfigModel>)
    suspend fun updateApproval(sensorType: Int, approved: Boolean)
    suspend fun hasExistingConfig(): Boolean
}
