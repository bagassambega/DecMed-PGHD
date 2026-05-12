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

/**
 * UI state for the sensor enumeration and configuration screens.
 * Kept as a single data class so state updates are atomic (no partial states).
 */
data class SensorUiState(
    val availableSensors: List<SensorInfo> = emptyList(),
    val unavailableSensors: List<SensorInfo> = emptyList(),
    val sensorConfigs: List<SensorConfigModel> = emptyList(),
    val isEnumerating: Boolean = false,
    val enumerationComplete: Boolean = false,
    val configSaved: Boolean = false
)

/**
 * ViewModel shared across SensorListScreen, SensorConfigScreen, HomeScreen, and Settings.
 *
 * Design rationale:
 * - Uses AndroidViewModel because it needs the Application context to obtain SensorManager
 *   and access the AppContainer (manual DI) for repository and use cases.
 * - Scoped to the Activity lifecycle so state persists across navigation within the NavHost.
 */
class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MainApplication).container
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val getAvailableSensorsUseCase = GetAvailableSensorsUseCase()
    private val saveSensorConfigUseCase = SaveSensorConfigUseCase(container.sensorConfigRepository)
    private val getSensorConfigUseCase = GetSensorConfigUseCase(container.sensorConfigRepository)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    init {
        // Load existing config from DB if available (for Home screen and Settings re-entry)
        loadExistingConfig()
    }

    /**
     * Enumerates all known sensor types against the device's SensorManager.
     * A short artificial delay (500ms) is added so the loading indicator is visible
     * to the user, providing feedback that work is happening.
     */
    fun enumerateSensors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEnumerating = true) }

            // Short delay for UX feedback — the actual enumeration is near-instant
            delay(500)

            val allSensors = getAvailableSensorsUseCase(sensorManager)
            val available = allSensors.filter { it.isAvailable }
            val unavailable = allSensors.filter { !it.isAvailable }

            // Initialize config models for available sensors (all unapproved by default)
            val configs = available.map { sensor ->
                SensorConfigModel(
                    sensorType = sensor.type,
                    sensorName = sensor.name,
                    isApproved = false,
                    healthDataDescription = sensor.healthDataCapabilities.joinToString(", ")
                )
            }

            _uiState.update {
                it.copy(
                    availableSensors = available,
                    unavailableSensors = unavailable,
                    sensorConfigs = configs,
                    isEnumerating = false,
                    enumerationComplete = true
                )
            }
        }
    }

    /**
     * Toggles a single sensor's approval status in the in-memory config list.
     * Does NOT persist to DB — that happens on explicit "Save".
     */
    fun toggleSensorApproval(sensorType: Int, approved: Boolean) {
        _uiState.update { state ->
            state.copy(
                sensorConfigs = state.sensorConfigs.map { config ->
                    if (config.sensorType == sensorType) {
                        config.copy(isApproved = approved)
                    } else {
                        config
                    }
                }
            )
        }
    }

    /**
     * Persists the current sensor config list to the Room database.
     */
    fun saveConfiguration() {
        viewModelScope.launch {
            saveSensorConfigUseCase(_uiState.value.sensorConfigs)
            _uiState.update { it.copy(configSaved = true) }
        }
    }

    /**
     * Loads existing sensor configuration from the database.
     * Used on app restart (Home screen) and when entering Settings → SensorConfig.
     */
    private fun loadExistingConfig() {
        viewModelScope.launch {
            val hasConfig = getSensorConfigUseCase.hasExistingConfig()
            if (hasConfig) {
                val configs = getSensorConfigUseCase().first()
                _uiState.update {
                    it.copy(
                        sensorConfigs = configs,
                        configSaved = true
                    )
                }
            }
        }
    }

    /**
     * Prepares the ViewModel state for reconfiguration from Settings.
     * Re-enumerates sensors and merges with existing DB config to preserve
     * the user's previous toggle states.
     */
    fun prepareForReconfiguration() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEnumerating = true) }
            delay(300)

            val allSensors = getAvailableSensorsUseCase(sensorManager)
            val available = allSensors.filter { it.isAvailable }
            val unavailable = allSensors.filter { !it.isAvailable }

            // Load existing config to preserve previous toggle states
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
                    isApproved = existing?.isApproved ?: false,
                    healthDataDescription = sensor.healthDataCapabilities.joinToString(", ")
                )
            }

            _uiState.update {
                it.copy(
                    availableSensors = available,
                    unavailableSensors = unavailable,
                    sensorConfigs = configs,
                    isEnumerating = false,
                    enumerationComplete = true
                )
            }
        }
    }
}
