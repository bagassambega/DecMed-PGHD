package com.hackastic.decmed.viewmodel

import android.app.Application
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.utils.DecmedLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.data.health.HealthConnectPghdClient
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.data.pghd.PghdPayloadSerializer
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.worker.PghdWorkScheduler
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
    val healthConnectStatusMessage: String = "Health Connect status has not been checked yet.",
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

    val requestedPermissions: Set<String> = HealthConnectPghdClient.READ_DATA_PERMISSIONS
    val requestedHistoryPermissions: Set<String> = HealthConnectPghdClient.READ_HISTORY_PERMISSIONS

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
            try {
                DecmedLog.i(TAG, "Refreshing Health Connect state")
                val availabilityState = healthConnectPghdClient.getAvailabilityState()
                val permissionState = if (availabilityState.isAvailable) healthConnectPghdClient.getPermissionState() else null
                _uiState.update {
                    it.copy(
                        isHealthConnectAvailable = availabilityState.isAvailable,
                        healthConnectStatusMessage = availabilityState.message,
                        hasHealthConnectPermissions = permissionState?.hasRequiredDataPermissions == true,
                        hasHealthConnectHistoryPermission = permissionState?.hasHistoryPermission == true
                    )
                }
                DecmedLog.i(TAG, "Health Connect state refreshed: availability=$availabilityState permissionState=$permissionState")
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to refresh Health Connect state", err)
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to refresh Health Connect state.")
                }
            }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        viewModelScope.launch {
            val availabilityState = healthConnectPghdClient.getAvailabilityState()
            val permissionState = if (availabilityState.isAvailable) {
                healthConnectPghdClient.getPermissionState()
            } else {
                null
            }
            val hasDataPermissions = permissionState?.hasRequiredDataPermissions == true
            val hasHistoryPermission = permissionState?.hasHistoryPermission == true
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = availabilityState.isAvailable,
                    healthConnectStatusMessage = availabilityState.message,
                    hasHealthConnectPermissions = hasDataPermissions,
                    hasHealthConnectHistoryPermission = hasHistoryPermission,
                    errorMessage = if (hasDataPermissions) {
                        null
                    } else {
                        "Health Connect data permissions are required before syncing PGHD."
                    },
                    lastSyncMessage = if (hasDataPermissions && hasHistoryPermission) {
                        "Health Connect data and history permissions granted."
                    } else if (hasDataPermissions) {
                        "Health Connect data permissions granted. You can approve health history later for older records."
                    } else {
                        null
                    }
                )
            }
            DecmedLog.i(
                TAG,
                "Health Connect permission result: availability=$availabilityState permissionState=$permissionState launcherGranted=$grantedPermissions"
            )
            if (hasDataPermissions) {
                syncFromHealthConnect()
            }
        }
    }

    fun syncFromHealthConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null, lastSyncMessage = null) }
            try {
                DecmedLog.i(TAG, "Starting Health Connect sync")
                val availabilityState = healthConnectPghdClient.getAvailabilityState()
                if (!availabilityState.isAvailable) {
                    DecmedLog.w(TAG, "Health Connect sync skipped: $availabilityState")
                    _uiState.update {
                        it.copy(
                            isHealthConnectAvailable = false,
                            healthConnectStatusMessage = availabilityState.message,
                            errorMessage = availabilityState.message
                        )
                    }
                    return@launch
                }

                val permissionState = healthConnectPghdClient.getPermissionState()
                if (!permissionState.hasRequiredDataPermissions) {
                    DecmedLog.w(TAG, "Health Connect sync skipped: missing permissions $permissionState")
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
                scheduleSizeThresholdBatchIfNeeded()
                refreshTotalCount()
                DecmedLog.i(TAG, "Health Connect sync succeeded: records=${records.size} daysBack=$daysBack")
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
                DecmedLog.e(TAG, "Health Connect sync failed", err)
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
                DecmedLog.i(TAG, "Saving manual PGHD record: type=$recordType displayName=$displayName unit=$unit")
                val trimmedValue = valueText.trim()
                pghdRepository.saveManualRecord(
                    recordType = recordType.ifBlank { "manual_pghd" },
                    displayName = displayName.ifBlank { "Manual PGHD" },
                    valueText = trimmedValue,
                    unit = unit.ifBlank { "value" },
                    numericValue = trimmedValue.toDoubleOrNull(),
                    notes = notes
                )
                scheduleSizeThresholdBatchIfNeeded()
                refreshTotalCount()
                DecmedLog.i(TAG, "Manual PGHD record saved")
                _uiState.update {
                    it.copy(lastSyncMessage = "Saved manual PGHD record.", errorMessage = null)
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to save manual PGHD record", err)
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
                DecmedLog.w(TAG, "Submit displayed PGHD skipped: no records")
                _uiState.update { it.copy(errorMessage = "No PGHD records are available to submit.") }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, lastSyncMessage = null) }
            try {
                DecmedLog.i(TAG, "Submitting displayed PGHD records: count=${recordsToSubmit.size}")
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                val result = pghdBatchRepository.createEncryptAndSubmitBatch(
                    records = recordsToSubmit,
                    patientProfile = patientProfile
                )
                DecmedLog.i(TAG, "Displayed PGHD submit finished: $result")
                _uiState.update {
                    it.copy(
                        lastSyncMessage = result.message ?: "Submitted encrypted PGHD batch ${result.batchId}.",
                        errorMessage = null
                    )
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to submit displayed PGHD", err)
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
                DecmedLog.i(TAG, "Submitting PGHD batch: batchId=$batchId")
                val result = pghdBatchRepository.submitBatch(batchId)
                DecmedLog.i(TAG, "PGHD batch submit finished: $result")
                _uiState.update {
                    it.copy(
                        lastSyncMessage = result.message ?: "Submitted PGHD batch $batchId.",
                        errorMessage = if (result.accepted) null else result.message
                    )
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to submit PGHD batch: batchId=$batchId", err)
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to submit PGHD batch.")
                }
            } finally {
                _uiState.update { it.copy(submittingBatchId = null) }
            }
        }
    }

    fun grantAccess(
        hospitalPersonnelIotaAddress: String,
        hospitalPersonnelPrePublicKey: String,
        accessKind: PatientGrantAccessKind
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGrantingAccess = true, errorMessage = null, lastSyncMessage = null) }
            try {
                DecmedLog.i(TAG, "Granting $accessKind access to $hospitalPersonnelIotaAddress")
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                val result = when (accessKind) {
                    PatientGrantAccessKind.PGHD_READ -> prePghdClient.grantPghdReadAccess(
                        profile = patientProfile,
                        hospitalPersonnelIotaAddress = hospitalPersonnelIotaAddress,
                        hospitalPersonnelPrePublicKey = hospitalPersonnelPrePublicKey
                    )
                    PatientGrantAccessKind.MEDICAL_RECORD_READ_UPDATE -> prePghdClient.grantMedicalRecordReadUpdateAccess(
                        profile = patientProfile,
                        hospitalPersonnelIotaAddress = hospitalPersonnelIotaAddress,
                        hospitalPersonnelPrePublicKey = hospitalPersonnelPrePublicKey
                    )
                }
                DecmedLog.i(TAG, "$accessKind access grant succeeded: $result")
                _uiState.update {
                    it.copy(
                        lastSyncMessage = "Granted ${accessKind.displayLabel} to ${result.hospitalPersonnelIotaAddress}.",
                        errorMessage = null
                    )
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to grant $accessKind access to $hospitalPersonnelIotaAddress", err)
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to grant access.")
                }
            } finally {
                _uiState.update { it.copy(isGrantingAccess = false) }
            }
        }
    }

    private suspend fun scheduleSizeThresholdBatchIfNeeded() {
        val records = pghdRepository.getUnbatchedRecords()
        if (records.isEmpty()) return
        val profile = runCatching { patientAuthRepository.getUnlockedProfile() }.getOrNull() ?: return
        val patientId = profile.iotaAddress ?: profile.idHash ?: profile.id
        val estimatedPayload = PghdPayloadConverter.recordsToBatchPayload(
            records = records,
            patientId = patientId,
            triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
        )
        val estimatedBytes = PghdPayloadSerializer.toJson(estimatedPayload).toByteArray(Charsets.UTF_8).size
        if (estimatedBytes > Env.pghdEarlyTriggerBytes) {
            DecmedLog.i(
                TAG,
                "Scheduling immediate PGHD batch because unbatched payload size=$estimatedBytes exceeds threshold=${Env.pghdEarlyTriggerBytes}"
            )
            PghdWorkScheduler.scheduleBatchNow(getApplication())
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

    companion object {
        private const val TAG = "PghdCollectionViewModel"
    }
}

enum class PatientGrantAccessKind(val displayLabel: String) {
    PGHD_READ("PGHD read access"),
    MEDICAL_RECORD_READ_UPDATE("medical record read/update access")
}
