package com.hackastic.decmed.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.data.local.entity.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataUiState(
    val sensorData: List<SensorData> = emptyList(),
    val availableDataTypes: List<String> = emptyList(),
    val selectedDataType: String? = null,
    val totalCount: Long = 0
)

class DataViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MainApplication).container
    private val sensorRepository = container.sensorRepository

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private val _selectedDataType = MutableStateFlow<String?>(null)

    init {
        observeData()
        loadDataTypes()
        loadTotalCount()
    }

    private fun observeData() {
        viewModelScope.launch {
            _selectedDataType.collectLatest { type ->
                val flow = if (type == null) {
                    sensorRepository.getLatestData(100)
                } else {
                    sensorRepository.getLatestByDataType(type, 100)
                }
                flow.collect { data ->
                    _uiState.update { it.copy(sensorData = data, selectedDataType = type) }
                }
            }
        }
    }

    private fun loadDataTypes() {
        viewModelScope.launch {
            sensorRepository.getDistinctDataTypes().collect { types ->
                _uiState.update { it.copy(availableDataTypes = types) }
            }
        }
    }

    private fun loadTotalCount() {
        viewModelScope.launch {
            // This is a simple one-time load or we could observe it if needed
            // For now, let's just get it once at start
            val count = sensorRepository.getTotalRecordCount()
            _uiState.update { it.copy(totalCount = count) }
        }
    }

    fun selectDataType(dataType: String?) {
        _selectedDataType.value = dataType
    }
}
