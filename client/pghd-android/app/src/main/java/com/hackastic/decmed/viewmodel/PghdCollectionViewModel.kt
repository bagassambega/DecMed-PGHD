package com.hackastic.decmed.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.data.health.HealthConnectPghdClient
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
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
    val batches: List<PghdBatchEntity> = emptyList(),
    val recordTypes: List<String> = emptyList(),
    val selectedSourceTag: String? = null,
    val selectedRecordType: String? = null,
    val totalCount: Long = 0,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false,
    val hasHealthConnectHistoryPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val isSubmitting: Boolean = false,
    val submittingBatchId: String? = null,
    val isGrantingAccess: Boolean = false,
    val lastSyncMessage: String? = null,
    val errorMessage: String? = null,
    val healthConnectSourcePackages: List<String> = emptyList(),
    val hasDetectedXiaomiSource: Boolean = false
)

class PghdCollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MainApplication).container
    private val pghdRepository = container.pghdRepository
    private val pghdBatchRepository = container.pghdBatchRepository
    private val prePghdClient = container.prePghdClient
    private val patientAuthRepository = container.patientAuthRepository
    private val healthConnectPghdClient = container.healthConnectPghdClient

    private val selectedSourceTag = MutableStateFlow<String?>(null)
    private val selectedRecordType = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(PghdCollectionUiState())
    val uiState: StateFlow<PghdCollectionUiState> = _uiState.asStateFlow()

    val requestedPermissions: Set<String> = HealthConnectPghdClient.READ_PERMISSIONS

    init {
        observeRecords()
        observeBatches()
        observeRecordTypes()
        observeHealthConnectSourcePackages()
        refreshHealthConnectState()
        refreshTotalCount()
    }

    fun refreshHealthConnectState() {
        viewModelScope.launch {
            val available = healthConnectPghdClient.isAvailable()
            val permissionState = if (available) healthConnectPghdClient.getPermissionState() else null
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = available,
                    hasHealthConnectPermissions = permissionState?.hasRequiredDataPermissions == true,
                    hasHealthConnectHistoryPermission = permissionState?.hasHistoryPermission == true
                )
            }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        val hasDataPermissions = grantedPermissions.containsAll(HealthConnectPghdClient.XIAOMI_BAND_READ_PERMISSIONS)
        val hasHistoryPermission = HealthConnectPghdClient.READ_PERMISSIONS
            .minus(HealthConnectPghdClient.XIAOMI_BAND_READ_PERMISSIONS)
            .all { it in grantedPermissions }
        _uiState.update {
            it.copy(
                hasHealthConnectPermissions = hasDataPermissions,
                hasHealthConnectHistoryPermission = hasHistoryPermission,
                errorMessage = if (hasDataPermissions) {
                    null
                } else {
                    "Health Connect data permissions are required before syncing PGHD."
                }
            )
        }
        if (hasDataPermissions) {
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

                val permissionState = healthConnectPghdClient.getPermissionState()
                if (!permissionState.hasRequiredDataPermissions) {
                    _uiState.update {
                        it.copy(
                            hasHealthConnectPermissions = false,
                            errorMessage = "Health Connect permission is required before syncing PGHD."
                        )
                    }
                    return@launch
                }

                val daysBack = if (permissionState.hasHistoryPermission) {
                    HealthConnectPghdClient.HISTORY_SYNC_DAYS
                } else {
                    HealthConnectPghdClient.DEFAULT_SYNC_DAYS
                }
                val records = healthConnectPghdClient.readXiaomiBandPghd(daysBack)
                pghdRepository.saveHealthConnectRecords(records)
                refreshTotalCount()
                _uiState.update {
                    it.copy(
                        hasHealthConnectPermissions = true,
                        hasHealthConnectHistoryPermission = permissionState.hasHistoryPermission,
                        lastSyncMessage = buildString {
                            append("Synced ${records.size} Xiaomi-band Health Connect records from the last $daysBack days.")
                            if (!permissionState.hasHistoryPermission) {
                                append(" Grant health history permission to include older records.")
                            }
                        }
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

    fun submitDisplayedPghd() {
        viewModelScope.launch {
            val recordsToSubmit = uiState.value.records
            if (recordsToSubmit.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "No PGHD records are available to submit.") }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, lastSyncMessage = null) }
            try {
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                val result = pghdBatchRepository.createEncryptAndSubmitBatch(
                    records = recordsToSubmit,
                    patientProfile = patientProfile
                )
                _uiState.update {
                    it.copy(
                        lastSyncMessage = result.message ?: "Submitted encrypted PGHD batch ${result.batchId}.",
                        errorMessage = null
                    )
                }
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to submit encrypted PGHD batch.")
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun submitBatch(batchId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    submittingBatchId = batchId,
                    errorMessage = null,
                    lastSyncMessage = null
                )
            }
            try {
                val result = pghdBatchRepository.submitBatch(batchId)
                _uiState.update {
                    it.copy(
                        lastSyncMessage = result.message ?: "Submitted PGHD batch $batchId.",
                        errorMessage = if (result.accepted) null else result.message
                    )
                }
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to submit PGHD batch.")
                }
            } finally {
                _uiState.update { it.copy(submittingBatchId = null) }
            }
        }
    }

    fun grantPghdAccess(
        hospitalPersonnelIotaAddress: String,
        hospitalPersonnelPrePublicKey: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGrantingAccess = true, errorMessage = null, lastSyncMessage = null) }
            try {
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                val result = prePghdClient.grantPghdReadAccess(
                    profile = patientProfile,
                    hospitalPersonnelIotaAddress = hospitalPersonnelIotaAddress,
                    hospitalPersonnelPrePublicKey = hospitalPersonnelPrePublicKey
                )
                _uiState.update {
                    it.copy(
                        lastSyncMessage = "Granted PGHD read access to ${result.hospitalPersonnelIotaAddress}.",
                        errorMessage = null
                    )
                }
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to grant PGHD access.")
                }
            } finally {
                _uiState.update { it.copy(isGrantingAccess = false) }
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

    private fun observeBatches() {
        viewModelScope.launch {
            pghdBatchRepository.getBatches().collect { batches ->
                _uiState.update { it.copy(batches = batches) }
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

    private fun observeHealthConnectSourcePackages() {
        viewModelScope.launch {
            pghdRepository.getHealthConnectSourcePackages().collect { sourcePackages ->
                _uiState.update {
                    it.copy(
                        healthConnectSourcePackages = sourcePackages,
                        hasDetectedXiaomiSource = sourcePackages.any(::isLikelyXiaomiPackage)
                    )
                }
            }
        }
    }

    private fun isLikelyXiaomiPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return listOf("xiaomi", "mihealth", "mifitness", "huami", "zepp").any(normalized::contains)
    }

    private fun refreshTotalCount() {
        viewModelScope.launch {
            _uiState.update { it.copy(totalCount = pghdRepository.getTotalCount()) }
        }
    }
}
