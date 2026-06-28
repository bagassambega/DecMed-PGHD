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
import com.hackastic.decmed.data.remote.PghdActiveAccessGrant
import com.hackastic.decmed.data.remote.PreHttpException
import com.hackastic.decmed.data.repository.PghdCollectionState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class PghdCollectionUiState(
    val records: List<PghdRecordEntity> = emptyList(),
    val homeRecords: List<PghdRecordEntity> = emptyList(),
    val batches: List<PghdBatchEntity> = emptyList(),
    val visibleBatches: List<PghdBatchEntity> = emptyList(),
    val recordTypes: List<String> = emptyList(),
    val selectedSourceTag: String? = null,
    val selectedRecordType: String? = null,
    val dateFilterStartMillis: Long? = null,
    val dateFilterEndMillis: Long? = null,
    val totalCount: Long = 0,
    val isHealthConnectAvailable: Boolean = false,
    val healthConnectStatusMessage: String = "Health Connect status has not been checked yet.",
    val hasHealthConnectPermissions: Boolean = false,
    val hasHealthConnectHistoryPermission: Boolean = false,
    val isRefreshingHealthConnectState: Boolean = false,
    val isSyncing: Boolean = false,
    val isSavingManualRecord: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmittingActiveCollection: Boolean = false,
    val submittingBatchId: String? = null,
    val isGrantingAccess: Boolean = false,
    val isRevokingAccess: Boolean = false,
    val isRefreshingAccessGrants: Boolean = false,
    val lastSyncMessage: String? = null,
    val errorMessage: String? = null,
    val healthConnectSourcePackages: List<String> = emptyList(),
    val hasDetectedXiaomiSource: Boolean = false,
    val activeAccessGrants: List<PghdActiveAccessGrant> = emptyList(),
    val activeCollectionWindow: ActivePghdCollectionWindow? = null
)

data class ActivePghdCollectionWindow(
    val recordCount: Int,
    val estimatedBytes: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val dataStartEpochMillis: Long?,
    val dataEndEpochMillis: Long?,
    val latestRecordEpochMillis: Long,
    val isCollecting: Boolean
)

class PghdCollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MainApplication).container
    private val pghdRepository = container.pghdRepository
    private val pghdBatchRepository = container.pghdBatchRepository
    private val prePghdClient = container.prePghdClient
    private val patientAuthRepository = container.patientAuthRepository
    private val healthConnectPghdClient = container.healthConnectPghdClient
    private val pghdAccessGrantRepository = container.pghdAccessGrantRepository
    private val pghdCollectionStateRepository = container.pghdCollectionStateRepository

    private val selectedSourceTag = MutableStateFlow<String?>(null)
    private val selectedRecordType = MutableStateFlow<String?>(null)
    private val dateFilterStartMillis = MutableStateFlow<Long?>(null)
    private val dateFilterEndMillis = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(PghdCollectionUiState())
    val uiState: StateFlow<PghdCollectionUiState> = _uiState.asStateFlow()

    val requestedPermissions: Set<String> = HealthConnectPghdClient.READ_DATA_PERMISSIONS
    val requestedHistoryPermissions: Set<String> = HealthConnectPghdClient.READ_HISTORY_PERMISSIONS

    init {
        normalizeBatchStatuses()
        observeRecords()
        observeHomeRecords()
        observeActiveCollectionWindow()
        observeBatches()
        observeRecordTypes()
        observeHealthConnectSourcePackages()
        refreshHealthConnectState()
        refreshTotalCount()
    }

    private fun normalizeBatchStatuses() {
        viewModelScope.launch {
            runCatching { pghdBatchRepository.normalizeBatchStatuses() }
                .onFailure { DecmedLog.e(TAG, "Failed to normalize PGHD batch statuses", it) }
        }
    }

    fun refreshHealthConnectState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingHealthConnectState = true, errorMessage = null) }
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
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to refresh Health Connect state."))
                }
            } finally {
                _uiState.update { it.copy(isRefreshingHealthConnectState = false) }
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

                val fallbackDaysBack = if (permissionState.hasHistoryPermission) {
                    HealthConnectPghdClient.HISTORY_SYNC_DAYS
                } else {
                    HealthConnectPghdClient.DEFAULT_SYNC_DAYS
                }
                val latestHealthConnectEnd = pghdRepository.getLatestHealthConnectEndTimeMillis()
                val syncStart = latestHealthConnectEnd
                    ?.let { Instant.ofEpochMilli(it + 1) }
                    ?: Instant.now().minusSeconds(fallbackDaysBack * 24L * 60L * 60L)
                val records = healthConnectPghdClient.readPghdSince(syncStart)
                pghdRepository.saveHealthConnectRecords(records)
                scheduleSizeThresholdBatchIfNeeded()
                refreshTotalCount()
                DecmedLog.i(
                    TAG,
                    "Health Connect sync succeeded: records=${records.size} syncStart=$syncStart latestPreviousEnd=$latestHealthConnectEnd fallbackDaysBack=$fallbackDaysBack"
                )
                _uiState.update {
                    it.copy(
                        hasHealthConnectPermissions = true,
                        hasHealthConnectHistoryPermission = permissionState.hasHistoryPermission,
                        lastSyncMessage = buildString {
                            append("Synced ${records.size} Health Connect records since $syncStart.")
                            if (!permissionState.hasHistoryPermission) {
                                append(" Grant health history permission to include older records.")
                            }
                        }
                    )
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Health Connect sync failed", err)
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to sync Health Connect data."))
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
            _uiState.update { it.copy(isSavingManualRecord = true, errorMessage = null, lastSyncMessage = null) }
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
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to save manual PGHD record."))
                }
            } finally {
                _uiState.update { it.copy(isSavingManualRecord = false) }
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
                if (!result.accepted) {
                    PghdWorkScheduler.scheduleSubmitWhenConnected(getApplication())
                }
                DecmedLog.i(TAG, "Displayed PGHD submit finished: $result")
                _uiState.update {
                    if (result.accepted) {
                        it.copy(
                            lastSyncMessage = result.message ?: "Submitted encrypted PGHD batch ${result.batchId}.",
                            errorMessage = null
                        )
                    } else {
                        it.copy(
                            lastSyncMessage = null,
                            errorMessage = result.message ?: "Failed to submit encrypted PGHD batch ${result.batchId}."
                        )
                    }
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to submit displayed PGHD", err)
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to submit encrypted PGHD batch."))
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
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                prePghdClient.pushRegistration(patientProfile)
                val result = pghdBatchRepository.submitBatch(
                    batchId = batchId,
                    submitTriggerReason = PghdBatchEntity.TRIGGER_MANUAL_SUBMIT
                )
                if (!result.accepted) {
                    PghdWorkScheduler.scheduleSubmitWhenConnected(getApplication())
                }
                DecmedLog.i(TAG, "PGHD batch submit finished: $result")
                _uiState.update {
                    if (result.accepted) {
                        it.copy(
                            lastSyncMessage = result.message ?: "Submitted PGHD batch $batchId.",
                            errorMessage = null
                        )
                    } else {
                        it.copy(
                            lastSyncMessage = null,
                            errorMessage = result.message ?: "Failed to submit PGHD batch $batchId."
                        )
                    }
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to submit PGHD batch: batchId=$batchId", err)
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to submit PGHD batch."))
                }
            } finally {
                _uiState.update { it.copy(submittingBatchId = null) }
            }
        }
    }

    fun submitActiveCollection() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmittingActiveCollection = true,
                    errorMessage = null,
                    lastSyncMessage = null
                )
            }
            try {
                val collectionState = pghdCollectionStateRepository.state.first()
                val records = pghdRepository.getActiveWindowUnbatchedRecords(collectionState)
                if (records.isEmpty()) {
                    DecmedLog.w(TAG, "Submit active PGHD collection skipped: no unbatched records")
                    _uiState.update {
                        it.copy(errorMessage = "No active PGHD collection is available to send.")
                    }
                    return@launch
                }

                DecmedLog.i(TAG, "Submitting active PGHD collection: count=${records.size}")
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                prePghdClient.pushRegistration(patientProfile)
                val collectionEndedAt = if (collectionState.enabled) {
                    System.currentTimeMillis()
                } else {
                    collectionState.stoppedAtEpochMillis ?: System.currentTimeMillis()
                }
                val batch = pghdBatchRepository.createEncryptedBatch(
                    records = records,
                    patientProfile = patientProfile,
                    collectionStartedAtEpochMillis = collectionState.startedAtEpochMillis,
                    collectionEndedAtEpochMillis = collectionEndedAt,
                    triggerReason = PghdBatchPayload.TRIGGER_MANUAL_SUBMIT
                )
                pghdRepository.markRecordsBatched(records.map { it.uid }, batch.batchId)
                if (collectionState.enabled) {
                    pghdCollectionStateRepository.restartWindow(collectionEndedAt)
                }
                val result = pghdBatchRepository.submitBatch(
                    batchId = batch.batchId,
                    submitTriggerReason = PghdBatchEntity.TRIGGER_MANUAL_SUBMIT
                )
                if (!result.accepted) {
                    PghdWorkScheduler.scheduleSubmitWhenConnected(getApplication())
                }
                DecmedLog.i(TAG, "Active PGHD collection submit finished: $result")
                _uiState.update {
                    if (result.accepted) {
                        it.copy(
                            lastSyncMessage = result.message ?: "Submitted active PGHD collection as batch ${batch.batchId}.",
                            errorMessage = null
                        )
                    } else {
                        it.copy(
                            lastSyncMessage = null,
                            errorMessage = result.message ?: "Created batch ${batch.batchId}, but sending failed. Use Send now to retry."
                        )
                    }
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to submit active PGHD collection", err)
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to submit active PGHD collection."))
                }
            } finally {
                _uiState.update { it.copy(isSubmittingActiveCollection = false) }
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
                val firstAccessLogIndex = pghdAccessGrantRepository.nextAccessLogIndex()
                val accessLogIndexes = when (accessKind) {
                    PatientGrantAccessKind.PGHD_READ -> listOf(firstAccessLogIndex)
                    PatientGrantAccessKind.MEDICAL_RECORD_READ_UPDATE -> listOf(
                        firstAccessLogIndex,
                        firstAccessLogIndex + 1
                    )
                }
                pghdAccessGrantRepository.saveGrant(
                    hospitalPersonnelIotaAddress = result.hospitalPersonnelIotaAddress,
                    accessKind = accessKind,
                    accessLogIndexes = accessLogIndexes,
                    grantedAt = result.date
                )
                _uiState.update {
                    it.copy(
                        lastSyncMessage = "Granted ${accessKind.displayLabel} to ${result.hospitalPersonnelIotaAddress}.",
                        errorMessage = null
                    )
                }
                refreshActiveAccessGrants()
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to grant $accessKind access to $hospitalPersonnelIotaAddress", err)
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to grant access."))
                }
            } finally {
                _uiState.update { it.copy(isGrantingAccess = false) }
            }
        }
    }

    fun refreshActiveAccessGrants() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingAccessGrants = true, errorMessage = null) }
            try {
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                val activeGrants = prePghdClient.getActiveAccessGrants(patientProfile)
                _uiState.update {
                    it.copy(
                        activeAccessGrants = activeGrants,
                        errorMessage = null
                    )
                }
            } catch (err: Exception) {
                DecmedLog.e(TAG, "Failed to refresh active PGHD access grants from IOTA", err)
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to load active access grants from IOTA."))
                }
            } finally {
                _uiState.update { it.copy(isRefreshingAccessGrants = false) }
            }
        }
    }

    fun revokeAccess(grant: PghdActiveAccessGrant) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRevokingAccess = true, errorMessage = null, lastSyncMessage = null) }
            try {
                val firstAccessLogIndex = grant.accessLogIndexes.minOrNull()
                    ?: error("Selected access grant does not have an access log index.")
                DecmedLog.i(
                    TAG,
                    "Revoking ${grant.accessKind} access for hospitalPersonnel=${grant.hospitalPersonnelIotaAddress} indexes=${grant.accessLogIndexes}"
                )
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                val result = prePghdClient.revokeAccess(
                    profile = patientProfile,
                    hospitalPersonnelIotaAddress = grant.hospitalPersonnelIotaAddress,
                    accessLogIndex = firstAccessLogIndex,
                    purpose = grant.accessKind.reencryptionPurpose
                )
                refreshActiveAccessGrants()
                _uiState.update {
                    it.copy(
                        lastSyncMessage = "Revoked ${grant.accessKind.displayLabel} for ${result.hospitalPersonnelIotaAddress}.",
                        errorMessage = null
                    )
                }
            } catch (err: Exception) {
                DecmedLog.e(
                    TAG,
                    "Failed to revoke access grant id=${grant.id}",
                    err
                )
                _uiState.update {
                    it.copy(errorMessage = err.toVerboseUserMessage("Unable to revoke access."))
                }
            } finally {
                _uiState.update { it.copy(isRevokingAccess = false) }
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

    fun setDateFilter(startMillis: Long?, endMillis: Long?) {
        val normalizedStart = startMillis?.coerceAtMost(endMillis ?: startMillis)
        val normalizedEnd = endMillis?.coerceAtLeast(startMillis ?: endMillis)
        dateFilterStartMillis.value = normalizedStart
        dateFilterEndMillis.value = normalizedEnd
        _uiState.update {
            it.copy(
                dateFilterStartMillis = normalizedStart,
                dateFilterEndMillis = normalizedEnd
            )
        }
    }

    fun clearDateFilter() {
        setDateFilter(null, null)
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, lastSyncMessage = null) }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            combine(
                selectedSourceTag,
                selectedRecordType,
                dateFilterStartMillis,
                dateFilterEndMillis
            ) { source, type, start, end -> DateFilteredQuery(source, type, start, end) }
                .collectLatest { query ->
                    pghdRepository.getRecords(sourceTag = query.sourceTag, recordType = query.recordType).collect { records ->
                        val visibleRecords = records.filterRecordsByDateRange(
                            startMillis = query.startMillis,
                            endMillis = query.endMillis
                        )
                        _uiState.update {
                            it.copy(
                                records = visibleRecords,
                                selectedSourceTag = query.sourceTag,
                                selectedRecordType = query.recordType,
                                dateFilterStartMillis = query.startMillis,
                                dateFilterEndMillis = query.endMillis
                            )
                        }
                    }
                }
        }
    }

    private fun observeHomeRecords() {
        viewModelScope.launch {
            pghdRepository.getRecords(sourceTag = null, recordType = null).collect { records ->
                _uiState.update { it.copy(homeRecords = records) }
            }
        }
    }

    private fun observeActiveCollectionWindow() {
        viewModelScope.launch {
            combine(
                pghdRepository.observeUnbatchedRecords(),
                pghdCollectionStateRepository.state
            ) { records, collectionState -> records to collectionState }
                .collect { (records, collectionState) ->
                    _uiState.update {
                        it.copy(activeCollectionWindow = records.toActiveCollectionWindow(collectionState))
                    }
                }
        }
    }

    private fun observeBatches() {
        viewModelScope.launch {
            combine(
                pghdBatchRepository.getBatches(),
                dateFilterStartMillis,
                dateFilterEndMillis
            ) { batches, start, end -> Triple(batches, start, end) }
                .collect { (batches, start, end) ->
                    _uiState.update {
                        it.copy(
                            batches = batches,
                            visibleBatches = batches.filterBatchesByDateRange(start, end),
                            dateFilterStartMillis = start,
                            dateFilterEndMillis = end
                        )
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

private data class DateFilteredQuery(
    val sourceTag: String?,
    val recordType: String?,
    val startMillis: Long?,
    val endMillis: Long?
)

private fun List<PghdRecordEntity>.filterRecordsByDateRange(
    startMillis: Long?,
    endMillis: Long?
): List<PghdRecordEntity> =
    filter { record ->
        val afterStart = startMillis == null || record.endTimeEpochMillis >= startMillis
        val beforeEnd = endMillis == null || record.startTimeEpochMillis <= endMillis
        afterStart && beforeEnd
    }

private fun List<PghdBatchEntity>.filterBatchesByDateRange(
    startMillis: Long?,
    endMillis: Long?
): List<PghdBatchEntity> =
    filter { batch ->
        val batchStartMillis = batch.startTimestamp.toEpochMillisForDisplay()
        val batchEndMillis = batch.endTimestamp.toEpochMillisForDisplay()
        val afterStart = startMillis == null || batchEndMillis >= startMillis
        val beforeEnd = endMillis == null || batchStartMillis <= endMillis
        afterStart && beforeEnd
    }

private fun Long.toEpochMillisForDisplay(): Long =
    if (this < 10_000_000_000L) this * 1000L else this

private suspend fun com.hackastic.decmed.domain.repository.PghdRepository.getActiveWindowUnbatchedRecords(
    collectionState: PghdCollectionState
): List<PghdRecordEntity> =
    if (collectionState.enabled && collectionState.startedAtEpochMillis != null) {
        getUnbatchedRecordsSince(collectionState.startedAtEpochMillis)
    } else {
        getUnbatchedRecords()
    }

private fun List<PghdRecordEntity>.toActiveCollectionWindow(
    collectionState: PghdCollectionState
): ActivePghdCollectionWindow? {
    val startedAt = collectionState.startedAtEpochMillis
    val isCollecting = collectionState.enabled && startedAt != null
    val windowRecords = if (isCollecting) {
        filter { it.endTimeEpochMillis >= startedAt!! }
    } else {
        this
    }
    if (windowRecords.isEmpty() && !isCollecting) return null

    val sorted = windowRecords.sortedBy { it.endTimeEpochMillis }
    val startedAtEpochMillis = if (isCollecting) {
        startedAt!!
    } else {
        sorted.first().startTimeEpochMillis
    }
    val latestRecordEpochMillis = sorted.lastOrNull()?.endTimeEpochMillis ?: startedAtEpochMillis
    return ActivePghdCollectionWindow(
        recordCount = windowRecords.size,
        estimatedBytes = estimatePghdPayloadBytes(windowRecords),
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = collectionState.stoppedAtEpochMillis,
        dataStartEpochMillis = sorted.firstOrNull()?.startTimeEpochMillis,
        dataEndEpochMillis = sorted.lastOrNull()?.endTimeEpochMillis,
        latestRecordEpochMillis = latestRecordEpochMillis,
        isCollecting = isCollecting
    )
}

private fun estimatePghdPayloadBytes(records: List<PghdRecordEntity>): Long =
    if (records.isEmpty()) {
        0L
    } else {
        runCatching {
            val payload = PghdPayloadConverter.recordsToBatchPayload(
                records = records,
                patientId = "local_patient",
                triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            )
            PghdPayloadSerializer.toJson(payload).toByteArray(Charsets.UTF_8).size.toLong()
        }.getOrDefault(0L)
    }

private fun Throwable.toVerboseUserMessage(prefix: String): String {
    val chain = generateSequence(this as Throwable?) { it.cause }
        .toList()
    val responseException = chain.filterIsInstance<PreHttpException>().firstOrNull()
    val root = chain.lastOrNull() ?: this
    val detail = buildString {
        appendLine(prefix)
        appendLine()
        appendLine("Error detail")
        appendLine("- Type: ${this@toVerboseUserMessage::class.java.simpleName.ifBlank { this@toVerboseUserMessage::class.java.name }}")
        appendLine("- Message: ${this@toVerboseUserMessage.message?.takeIf { it.isNotBlank() } ?: "(no message)"}")
        if (root !== this@toVerboseUserMessage) {
            appendLine("- Root cause: ${root::class.java.simpleName}: ${root.message ?: "(no message)"}")
        }
        appendLine()
        appendLine("Error response")
        if (responseException != null) {
            appendLine("- HTTP status: ${responseException.statusCode}")
            appendLine("- URL: ${responseException.url}")
            appendLine("Body:")
            appendLine(responseException.responseBody.ifBlank { "(empty response body)" })
        } else {
            appendLine("(No HTTP error response was attached to this failure.)")
        }
        appendLine()
        appendLine("Trace")
        appendLine(
            chain.mapIndexed { index, throwable ->
                val type = throwable::class.java.simpleName.ifBlank { throwable::class.java.name }
                val message = throwable.message?.takeIf { it.isNotBlank() } ?: "(no message)"
                if (index == 0) "$type: $message" else "caused by $index: $type: $message"
            }.joinToString(separator = "\n")
        )
        appendLine()
        appendLine("Stack trace")
        append(this@toVerboseUserMessage.stackTraceToString())
    }
    return detail.trimEnd()
}

enum class PatientGrantAccessKind(val displayLabel: String) {
    PGHD_READ("PGHD read access"),
    MEDICAL_RECORD_READ_UPDATE("medical record read/update access");

    val reencryptionPurpose: String
        get() = when (this) {
            PGHD_READ -> "ReadPghd"
            MEDICAL_RECORD_READ_UPDATE -> "Update"
        }
}
