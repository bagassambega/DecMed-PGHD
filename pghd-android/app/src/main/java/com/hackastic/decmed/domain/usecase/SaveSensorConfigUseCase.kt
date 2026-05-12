package com.hackastic.decmed.domain.usecase

import com.hackastic.decmed.domain.model.SensorConfigModel
import com.hackastic.decmed.domain.repository.SensorConfigRepository

/**
 * Persists the user's sensor approval selections to the database.
 *
 * This use case is called in two scenarios:
 * 1. Initial setup: After the user first configures their sensor preferences.
 * 2. Reconfiguration: When the user modifies preferences via Settings.
 *
 * The repository's saveConfigs uses REPLACE conflict strategy,
 * so calling this with an updated list overwrites previous entries.
 */
class SaveSensorConfigUseCase(
    private val repository: SensorConfigRepository
) {
    suspend operator fun invoke(configs: List<SensorConfigModel>) {
        repository.saveConfigs(configs)
    }
}
