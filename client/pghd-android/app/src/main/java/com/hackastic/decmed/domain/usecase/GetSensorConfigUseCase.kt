package com.hackastic.decmed.domain.usecase

import com.hackastic.decmed.domain.model.SensorConfigModel
import com.hackastic.decmed.domain.repository.SensorConfigRepository
import kotlinx.coroutines.flow.Flow

/**
 * Retrieves the current sensor configuration as a reactive Flow.
 *
 * The Flow emits a new list whenever the underlying Room table changes,
 * which means the UI updates automatically when the user modifies
 * their sensor preferences from the Settings screen.
 */
class GetSensorConfigUseCase(
    private val repository: SensorConfigRepository
) {
    operator fun invoke(): Flow<List<SensorConfigModel>> {
        return repository.getAllConfigs()
    }

    fun approvedOnly(): Flow<List<SensorConfigModel>> {
        return repository.getApprovedConfigs()
    }

    suspend fun hasExistingConfig(): Boolean {
        return repository.hasExistingConfig()
    }
}
