package com.hackastic.decmed.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.domain.model.SensorConfigModel
import com.hackastic.decmed.domain.model.SensorInfo
import com.hackastic.decmed.domain.usecase.GetAvailableSensorsUseCase
import com.hackastic.decmed.domain.usecase.GetSensorConfigUseCase
import com.hackastic.decmed.domain.usecase.SaveSensorConfigUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SensorUiState(
    val availableSensors: List<SensorInfo> = emptyList(),
    val unavailableSensors: List<SensorInfo> = emptyList(),
    val sensorConfigs: List<SensorConfigModel> = emptyList(),
    val collectionSelection: Map<Int, Boolean> = emptyMap(),
    val collectionIntervals: Map<Int, Int> = emptyMap(),
    val isEnumerating: Boolean = false,
    val enumerationComplete: Boolean = false,
    val configSaved: Boolean = false,
    val isCollecting: Boolean = false
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val DEFAULT_COLLECTION_INTERVAL_MS = 5000
        val INTERVAL_OPTIONS_MS = listOf(1000, 5000, 10000, 15000, 30000, 60000)
    }

    private val container = (application as MainApplication).container
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val getAvailableSensorsUseCase = GetAvailableSensorsUseCase()
    private val saveSensorConfigUseCase = SaveSensorConfigUseCase(container.sensorConfigRepository)
    private val getSensorConfigUseCase = GetSensorConfigUseCase(container.sensorConfigRepository)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    init {
        loadExistingConfig()
    }

    fun enumerateSensors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEnumerating = true) }
            delay(500)

            val allSensors = getAvailableSensorsUseCase(sensorManager)
            val available = allSensors.filter { it.isAvailable }
            val unavailable = allSensors.filter { !it.isAvailable }

            val configs = available.map { sensor ->
                SensorConfigModel(
                    sensorType = sensor.type,
                    sensorName = sensor.name,
                    isApproved = true,
                    healthDataDescription = sensor.healthDataCapabilities.joinToString(", "),
                    collectionIntervalMs = DEFAULT_COLLECTION_INTERVAL_MS
                )
            }

            _uiState.update {
                it.copy(
                    availableSensors = available,
                    unavailableSensors = unavailable,
                    sensorConfigs = configs,
                    collectionSelection = configs.associate { cfg -> cfg.sensorType to cfg.isApproved },
                    collectionIntervals = configs.associate { cfg ->
                        cfg.sensorType to cfg.collectionIntervalMs
                    },
                    isEnumerating = false,
                    enumerationComplete = true
                )
            }
        }
    }

    fun toggleSensorApproval(sensorType: Int, approved: Boolean) {
        _uiState.update { state ->
            val updatedConfigs = state.sensorConfigs.map { config ->
                if (config.sensorType == sensorType) {
                    config.copy(isApproved = approved)
                } else {
                    config
                }
            }
            state.copy(
                sensorConfigs = updatedConfigs,
                collectionSelection = state.collectionSelection.toMutableMap().apply {
                    this[sensorType] = approved
                }
            )
        }
    }

    fun setAllSensorApproval(approved: Boolean) {
        _uiState.update { state ->
            val updatedConfigs = state.sensorConfigs.map { it.copy(isApproved = approved) }
            state.copy(
                sensorConfigs = updatedConfigs,
                collectionSelection = updatedConfigs.associate { it.sensorType to approved }
            )
        }
    }

    fun updateSensorInterval(sensorType: Int, intervalMs: Int) {
        _uiState.update { state ->
            val updatedConfigs = state.sensorConfigs.map { config ->
                if (config.sensorType == sensorType) {
                    config.copy(collectionIntervalMs = intervalMs)
                } else {
                    config
                }
            }
            state.copy(
                sensorConfigs = updatedConfigs,
                collectionIntervals = state.collectionIntervals.toMutableMap().apply {
                    this[sensorType] = intervalMs
                }
            )
        }
    }

    fun toggleCollectionSensorSelection(sensorType: Int, selected: Boolean) {
        _uiState.update { state ->
            state.copy(
                collectionSelection = state.collectionSelection.toMutableMap().apply {
                    this[sensorType] = selected
                }
            )
        }
    }

    fun setAllCollectionSensorsSelected(selected: Boolean) {
        _uiState.update { state ->
            val approvedSensors = state.sensorConfigs.filter { it.isApproved }
            state.copy(
                collectionSelection = state.collectionSelection.toMutableMap().apply {
                    approvedSensors.forEach { cfg -> this[cfg.sensorType] = selected }
                }
            )
        }
    }

    fun setCollectionInterval(sensorType: Int, intervalMs: Int) {
        updateSensorInterval(sensorType, intervalMs)
    }

    fun markCollectionRunning(running: Boolean) {
        _uiState.update { it.copy(isCollecting = running) }
    }

    fun getActiveCollectionConfig(): List<Pair<Int, Int>> {
        val state = _uiState.value
        val approved = state.sensorConfigs.filter { it.isApproved }
        return approved
            .filter { state.collectionSelection[it.sensorType] == true }
            .map { cfg ->
                cfg.sensorType to (state.collectionIntervals[cfg.sensorType] ?: cfg.collectionIntervalMs)
            }
    }

    fun saveConfiguration() {
        viewModelScope.launch {
            saveSensorConfigUseCase(_uiState.value.sensorConfigs)
            _uiState.update { it.copy(configSaved = true) }
        }
    }

    private fun loadExistingConfig() {
        viewModelScope.launch {
            val hasConfig = getSensorConfigUseCase.hasExistingConfig()
            if (!hasConfig) return@launch

            val configs = getSensorConfigUseCase().first()
            _uiState.update { state ->
                state.copy(
                    sensorConfigs = configs,
                    collectionSelection = configs.associate { cfg -> cfg.sensorType to cfg.isApproved },
                    collectionIntervals = configs.associate { cfg ->
                        cfg.sensorType to cfg.collectionIntervalMs
                    },
                    configSaved = true
                )
            }
        }
    }

    fun prepareForReconfiguration() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEnumerating = true) }
            delay(300)

            val allSensors = getAvailableSensorsUseCase(sensorManager)
            val available = allSensors.filter { it.isAvailable }
            val unavailable = allSensors.filter { !it.isAvailable }

            val existingConfigs = try {
                getSensorConfigUseCase().first()
            } catch (_: Exception) {
                emptyList()
            }

            val configs = available.map { sensor ->
                val existing = existingConfigs.find { it.sensorType == sensor.type }
                SensorConfigModel(
                    sensorType = sensor.type,
                    sensorName = sensor.name,
                    isApproved = existing?.isApproved ?: true,
                    healthDataDescription = sensor.healthDataCapabilities.joinToString(", "),
                    collectionIntervalMs = existing?.collectionIntervalMs ?: DEFAULT_COLLECTION_INTERVAL_MS
                )
            }

            _uiState.update {
                it.copy(
                    availableSensors = available,
                    unavailableSensors = unavailable,
                    sensorConfigs = configs,
                    collectionSelection = configs.associate { cfg -> cfg.sensorType to cfg.isApproved },
                    collectionIntervals = configs.associate { cfg ->
                        cfg.sensorType to cfg.collectionIntervalMs
                    },
                    isEnumerating = false,
                    enumerationComplete = true
                )
            }
        }
    }
}
