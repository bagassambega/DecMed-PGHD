package com.hackastic.decmed.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.data.health.HealthConnectPghdClient
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PghdCollectionUiState(
    val records: List<PghdRecordEntity> = emptyList(),
    val recordTypes: List<String> = emptyList(),
    val selectedSourceTag: String? = null,
    val selectedRecordType: String? = null,
    val totalCount: Long = 0,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String? = null,
    val errorMessage: String? = null
)

class PghdCollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MainApplication).container
    private val pghdRepository = container.pghdRepository
    private val healthConnectPghdClient = container.healthConnectPghdClient

    private val selectedSourceTag = MutableStateFlow<String?>(null)
    private val selectedRecordType = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(PghdCollectionUiState())
    val uiState: StateFlow<PghdCollectionUiState> = _uiState.asStateFlow()

    val requestedPermissions: Set<String> = HealthConnectPghdClient.READ_PERMISSIONS

    init {
        observeRecords()
        observeRecordTypes()
        refreshHealthConnectState()
        refreshTotalCount()
    }

    fun refreshHealthConnectState() {
        viewModelScope.launch {
            val available = healthConnectPghdClient.isAvailable()
            val hasPermissions = available && healthConnectPghdClient.hasAllPermissions()
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = available,
                    hasHealthConnectPermissions = hasPermissions
                )
            }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        _uiState.update {
            it.copy(
                hasHealthConnectPermissions = grantedPermissions.containsAll(requestedPermissions),
                errorMessage = null
            )
        }
        if (grantedPermissions.containsAll(requestedPermissions)) {
            syncFromHealthConnect()
        }
    }

    fun syncFromHealthConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null, lastSyncMessage = null) }
            try {
                if (!healthConnectPghdClient.isAvailable()) {
                    _uiState.update {
                        it.copy(
                            isHealthConnectAvailable = false,
                            errorMessage = "Health Connect is not available on this device."
                        )
                    }
                    return@launch
                }

                if (!healthConnectPghdClient.hasAllPermissions()) {
                    _uiState.update {
                        it.copy(
                            hasHealthConnectPermissions = false,
                            errorMessage = "Health Connect permission is required before syncing PGHD."
                        )
                    }
                    return@launch
                }

                val records = healthConnectPghdClient.readRecentPghd()
                pghdRepository.saveHealthConnectRecords(records)
                refreshTotalCount()
                _uiState.update {
                    it.copy(
                        hasHealthConnectPermissions = true,
                        lastSyncMessage = "Synced ${records.size} Health Connect records."
                    )
                }
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to sync Health Connect data.")
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun addManualRecord(
        recordType: String,
        displayName: String,
        valueText: String,
        unit: String,
        notes: String?
    ) {
        viewModelScope.launch {
            try {
                val trimmedValue = valueText.trim()
                pghdRepository.saveManualRecord(
                    recordType = recordType.ifBlank { "manual_pghd" },
                    displayName = displayName.ifBlank { "Manual PGHD" },
                    valueText = trimmedValue,
                    unit = unit.ifBlank { "value" },
                    numericValue = trimmedValue.toDoubleOrNull(),
                    notes = notes
                )
                refreshTotalCount()
                _uiState.update {
                    it.copy(lastSyncMessage = "Saved manual PGHD record.", errorMessage = null)
                }
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to save manual PGHD record.")
                }
            }
        }
    }

    fun selectSourceTag(sourceTag: String?) {
        selectedSourceTag.value = sourceTag
    }

    fun selectRecordType(recordType: String?) {
        selectedRecordType.value = recordType
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, lastSyncMessage = null) }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            combine(selectedSourceTag, selectedRecordType) { source, type -> source to type }
                .collectLatest { (source, type) ->
                    pghdRepository.getRecords(sourceTag = source, recordType = type).collect { records ->
                        _uiState.update {
                            it.copy(
                                records = records,
                                selectedSourceTag = source,
                                selectedRecordType = type
                            )
                        }
                    }
                }
        }
    }

    private fun observeRecordTypes() {
        viewModelScope.launch {
            pghdRepository.getRecordTypes().collect { types ->
                _uiState.update { it.copy(recordTypes = types) }
            }
        }
    }

    private fun refreshTotalCount() {
        viewModelScope.launch {
            _uiState.update { it.copy(totalCount = pghdRepository.getTotalCount()) }
        }
    }
}
