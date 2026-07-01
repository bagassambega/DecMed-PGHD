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
import com.hackastic.decmed.iota.DecmedIotaNative
import com.hackastic.decmed.worker.PghdBatchCreationGuard
import com.hackastic.decmed.worker.PghdWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.random.Random

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
    val activeCollectionWindow: ActivePghdCollectionWindow? = null,
    val stressTestProgress: PghdStressTestProgress? = null
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

data class PghdStressTestProgress(
    val startedAtEpochMillis: Long,
    val totalRecords: Int,
    val generatedRecords: Int = 0,
    val generatedComplete: Boolean = false,
    val isRunning: Boolean = true,
    val formedBatchCount: Int = 0,
    val sentBatchCount: Int = 0,
    val failedBatchCount: Int = 0,
    val waitingBatchCount: Int = 0,
    val pendingBatchCount: Int = 0,
    val currentBatchBytes: Long = 0,
    val currentBatchRecordCount: Int = 0,
    val errorMessage: String? = null
) {
    val generationFraction: Float
        get() = if (totalRecords <= 0) 0f else generatedRecords.toFloat() / totalRecords.toFloat()
}

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
    private var stressTestJob: Job? = null
    private var stressBatchJob: Job? = null
    private var lastStressSizeTriggerRecordCount: Int = 0

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
            val displayedRecordIds = uiState.value.records
                .filter { it.batchId == null }
                .map { it.uid }
                .toSet()
            if (displayedRecordIds.isEmpty()) {
                DecmedLog.w(TAG, "Submit displayed PGHD skipped: no records")
                _uiState.update { it.copy(errorMessage = "No PGHD records are available to submit.") }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, lastSyncMessage = null) }
            try {
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                prePghdClient.pushRegistration(patientProfile)
                val batch = PghdBatchCreationGuard.withLock {
                    val recordsToSubmit = pghdRepository.getUnbatchedRecords()
                        .filter { it.uid in displayedRecordIds }
                    if (recordsToSubmit.isEmpty()) return@withLock null

                    DecmedLog.i(TAG, "Submitting displayed PGHD records: count=${recordsToSubmit.size}")
                    pghdBatchRepository.createEncryptedBatch(
                        records = recordsToSubmit,
                        patientProfile = patientProfile,
                        triggerReason = PghdBatchPayload.TRIGGER_MANUAL_SUBMIT
                    ).also { batch ->
                        pghdRepository.markRecordsBatched(recordsToSubmit.map { it.uid }, batch.batchId)
                    }
                }
                if (batch == null) {
                    DecmedLog.w(TAG, "Submit displayed PGHD skipped: displayed records were already batched")
                    _uiState.update {
                        it.copy(errorMessage = "The selected PGHD records have already been added to another batch.")
                    }
                    return@launch
                }
                val result = pghdBatchRepository.submitBatch(
                    batchId = batch.batchId,
                    submitTriggerReason = PghdBatchEntity.TRIGGER_MANUAL_SUBMIT
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
                val patientProfile = patientAuthRepository.getUnlockedProfile()
                prePghdClient.pushRegistration(patientProfile)
                val batch = PghdBatchCreationGuard.withLock {
                    val collectionState = pghdCollectionStateRepository.state.first()
                    val records = pghdRepository.getActiveWindowUnbatchedRecords(collectionState)
                    if (records.isEmpty()) return@withLock null

                    DecmedLog.i(TAG, "Submitting active PGHD collection: count=${records.size}")
                    val collectionEndedAt = if (collectionState.enabled) {
                        System.currentTimeMillis()
                    } else {
                        collectionState.stoppedAtEpochMillis ?: System.currentTimeMillis()
                    }
                    pghdBatchRepository.createEncryptedBatch(
                        records = records,
                        patientProfile = patientProfile,
                        collectionStartedAtEpochMillis = collectionState.startedAtEpochMillis,
                        collectionEndedAtEpochMillis = collectionEndedAt,
                        triggerReason = PghdBatchPayload.TRIGGER_MANUAL_SUBMIT
                    ).also { batch ->
                        pghdRepository.markRecordsBatched(records.map { it.uid }, batch.batchId)
                        if (collectionState.enabled) {
                            pghdCollectionStateRepository.restartWindow(collectionEndedAt)
                        }
                    }
                }
                if (batch == null) {
                    DecmedLog.w(TAG, "Submit active PGHD collection skipped: no unbatched records")
                    _uiState.update {
                        it.copy(errorMessage = "No active PGHD collection is available to send.")
                    }
                    return@launch
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

    suspend fun lookupHospitalPersonnelIdentity(
        hospitalPersonnelIotaAddress: String
    ): HospitalPersonnelIdentity = withContext(Dispatchers.IO) {
        val patientProfile = patientAuthRepository.getUnlockedProfile()
        val patientIotaAddress = patientProfile.iotaAddress.requireIotaAddress("patient IOTA address")
        val personnelIotaAddress = hospitalPersonnelIotaAddress.requireIotaAddress("hospital personnel IOTA address")
        DecmedLog.i(
            TAG,
            "Looking up hospital personnel identity from IOTA: personnel=$personnelIotaAddress sender=$patientIotaAddress rpc=${Env.iotaRpcUrl}"
        )
        val info = DecmedIotaNative.getHospitalPersonnelInfo(
            hospitalPersonnelAddress = personnelIotaAddress,
            senderAddress = patientIotaAddress
        )
        HospitalPersonnelIdentity(
            iotaAddress = personnelIotaAddress,
            displayName = info.displayName,
            hospitalName = info.hospitalName.ifBlank { null },
            publicMetadata = info.publicMetadata.ifBlank { null }
        )
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

    fun startStressTest() {
        if (stressTestJob?.isActive == true || uiState.value.stressTestProgress?.isRunning == true) return
        stressTestJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val totalRecords = STRESS_TEST_SENSOR_TYPES.size * STRESS_TEST_DAYS * 24 * 60
            lastStressSizeTriggerRecordCount = 0
            _uiState.update {
                it.copy(
                    stressTestProgress = PghdStressTestProgress(
                        startedAtEpochMillis = startedAt,
                        totalRecords = totalRecords
                    ),
                    errorMessage = null,
                    lastSyncMessage = null
                )
            }
            try {
                patientAuthRepository.getUnlockedProfile()
                DecmedLog.i(
                    TAG,
                    "Starting PGHD local stress test: sensors=${STRESS_TEST_SENSOR_TYPES.size} days=$STRESS_TEST_DAYS totalRecords=$totalRecords insertChunkRecords=${STRESS_TEST_SENSOR_TYPES.size * STRESS_TEST_INSERT_CHUNK_MINUTES}"
                )
                withContext(Dispatchers.IO) {
                    val baseTime = startedAt
                    var generated = 0
                    val totalMinutes = STRESS_TEST_DAYS * 24 * 60
                    for (chunkStartMinute in 0 until totalMinutes step STRESS_TEST_INSERT_CHUNK_MINUTES) {
                        currentCoroutineContext().ensureActive()
                        val chunkEndMinute = (chunkStartMinute + STRESS_TEST_INSERT_CHUNK_MINUTES)
                            .coerceAtMost(totalMinutes)
                        val records = buildList {
                            for (minuteOffset in chunkStartMinute until chunkEndMinute) {
                                val timestamp = baseTime + minuteOffset * 60_000L
                                STRESS_TEST_SENSOR_TYPES.forEach { type ->
                                    add(
                                        type.toStressRecord(
                                            runId = startedAt,
                                            minuteOffset = minuteOffset,
                                            timestamp = timestamp
                                        )
                                    )
                                }
                            }
                        }
                        pghdRepository.saveHealthConnectRecords(records)
                        generated += records.size
                        checkStressSizeThresholdAfterInsert(generated)
                        if (chunkStartMinute % STRESS_TEST_PROGRESS_UPDATE_MINUTES == 0 || generated == totalRecords) {
                            _uiState.update { state ->
                                state.copy(
                                    stressTestProgress = state.stressTestProgress?.copy(
                                        generatedRecords = generated
                                    )
                                )
                            }
                        }
                    }
                }
                scheduleSizeThresholdBatchIfNeeded()
                PghdWorkScheduler.scheduleBatchNow(getApplication())
                PghdWorkScheduler.scheduleSubmitWhenConnected(getApplication())
                refreshTotalCount()
                _uiState.update { state ->
                    state.copy(
                        stressTestProgress = state.stressTestProgress?.copy(
                            generatedRecords = totalRecords,
                            generatedComplete = true,
                            isRunning = false
                        ),
                        lastSyncMessage = "Stress test data generation finished. Batch creation and PRE submission are continuing through the normal PGHD pipeline."
                    )
                }
            } catch (err: CancellationException) {
                DecmedLog.w(TAG, "PGHD local stress test cancelled")
                _uiState.update { state ->
                    state.copy(
                        stressTestProgress = state.stressTestProgress?.copy(
                            isRunning = false,
                            errorMessage = "Stress test was cancelled. Local synthetic data and local stress batches are being removed."
                        )
                    )
                }
                cleanupStressTestArtifacts(startedAt)
            } catch (err: Exception) {
                DecmedLog.e(TAG, "PGHD local stress test failed", err)
                _uiState.update { state ->
                    state.copy(
                        stressTestProgress = state.stressTestProgress?.copy(
                            isRunning = false,
                            errorMessage = err.toVerboseUserMessage("Unable to run PGHD stress test.")
                        ),
                        errorMessage = err.toVerboseUserMessage("Unable to run PGHD stress test.")
                    )
                }
            }
        }
    }

    fun cancelStressTest() {
        val progress = uiState.value.stressTestProgress ?: return
        PghdWorkScheduler.cancelStressTestWork(getApplication())
        stressTestJob?.cancel()
        stressBatchJob?.cancel()
        lastStressSizeTriggerRecordCount = 0
        viewModelScope.launch {
            cleanupStressTestArtifacts(progress.startedAtEpochMillis)
            _uiState.update {
                it.copy(
                    stressTestProgress = it.stressTestProgress?.copy(
                        isRunning = false,
                        generatedComplete = false,
                        errorMessage = "Stress test cancelled. Local synthetic records and local stress batches were removed."
                    ),
                    lastSyncMessage = "Stress test cancelled and local synthetic artifacts were removed."
                )
            }
            refreshTotalCount()
        }
    }

    fun dismissStressTestProgress() {
        _uiState.update { it.copy(stressTestProgress = null) }
    }

    private suspend fun cleanupStressTestArtifacts(startedAtEpochMillis: Long) {
        withContext(Dispatchers.IO) {
            pghdRepository.deleteStressRecordsSince(STRESS_TEST_SOURCE_PACKAGE_PREFIX, startedAtEpochMillis)
            pghdBatchRepository.deleteBatchesCreatedSince(startedAtEpochMillis)
        }
    }

    private suspend fun checkStressSizeThresholdAfterInsert(generatedRecords: Int) {
        val progress = _uiState.value.stressTestProgress
        if (progress?.isRunning != true) return

        val records = pghdRepository.getUnbatchedRecords()
        val estimatedBytes = estimatePghdPayloadBytes(records)
        _uiState.update { state ->
            state.copy(
                stressTestProgress = state.stressTestProgress?.copy(
                    generatedRecords = generatedRecords,
                    currentBatchBytes = estimatedBytes,
                    currentBatchRecordCount = records.size
                )
            )
        }

        if (estimatedBytes < Env.pghdEarlyTriggerBytes) {
            if (generatedRecords % STRESS_TEST_SIZE_PROGRESS_LOG_RECORDS == 0) {
                DecmedLog.i(
                    TAG,
                    "STRESS_SIZE_PROGRESS generatedRecords=$generatedRecords unbatchedRecords=${records.size} estimatedBytes=$estimatedBytes threshold=${Env.pghdEarlyTriggerBytes}"
                )
            }
            lastStressSizeTriggerRecordCount = 0
            return
        }
        if (records.size == lastStressSizeTriggerRecordCount) return

        lastStressSizeTriggerRecordCount = records.size
        val thresholdReachedAt = System.currentTimeMillis()
        DecmedLog.i(
            TAG,
            "STRESS_SIZE_THRESHOLD_REACHED at=${Instant.ofEpochMilli(thresholdReachedAt)} atMillis=$thresholdReachedAt generatedRecords=$generatedRecords unbatchedRecords=${records.size} estimatedBytes=$estimatedBytes threshold=${Env.pghdEarlyTriggerBytes}; starting background stress batch"
        )
        startStressBatchBackground(generatedRecords)
    }

    private fun startStressBatchBackground(generatedRecords: Int) {
        if (stressBatchJob?.isActive == true) {
            DecmedLog.i(
                TAG,
                "STRESS_BATCH_ALREADY_RUNNING generatedRecords=$generatedRecords threshold=${Env.pghdEarlyTriggerBytes}"
            )
            return
        }
        val job = viewModelScope.launch(Dispatchers.IO) {
            var createdAny = false
            runCatching {
                var shouldContinue: Boolean
                do {
                    currentCoroutineContext().ensureActive()
                    val created = createStressBatchImmediately(generatedRecords)
                    createdAny = createdAny || created
                    val remainingRecords = pghdRepository.getUnbatchedRecords()
                    val remainingBytes = estimatePghdPayloadBytes(remainingRecords)
                    shouldContinue = _uiState.value.stressTestProgress?.isRunning == true &&
                        remainingRecords.isNotEmpty() &&
                        remainingBytes >= Env.pghdEarlyTriggerBytes
                    DecmedLog.i(
                        TAG,
                        "STRESS_BATCH_BACKGROUND_STEP generatedRecords=$generatedRecords created=$created remainingRecords=${remainingRecords.size} remainingBytes=$remainingBytes shouldContinue=$shouldContinue threshold=${Env.pghdEarlyTriggerBytes}"
                    )
                } while (created && shouldContinue)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                DecmedLog.e(
                    TAG,
                    "STRESS_BATCH_BACKGROUND_FAILED generatedRecords=$generatedRecords reason=${err.message.orEmpty()}",
                    err
                )
            }
            DecmedLog.i(
                TAG,
                "STRESS_BATCH_BACKGROUND_FINISH generatedRecords=$generatedRecords createdAny=$createdAny"
            )
        }
        stressBatchJob = job
        job.invokeOnCompletion {
            if (stressBatchJob == job) {
                stressBatchJob = null
            }
        }
    }

    private suspend fun createStressBatchImmediately(generatedRecords: Int): Boolean {
        val createdBatchId = runCatching {
            PghdBatchCreationGuard.withLock {
            val records = pghdRepository.getUnbatchedRecords()
            val attemptAt = System.currentTimeMillis()
            if (records.isEmpty()) {
                DecmedLog.i(
                    TAG,
                    "STRESS_BATCH_SKIPPED at=${Instant.ofEpochMilli(attemptAt)} atMillis=$attemptAt generatedRecords=$generatedRecords reason=no_unbatched_records"
                )
                return@withLock null
            }
            val profile = runCatching { patientAuthRepository.getUnlockedProfile() }
                .getOrElse { err ->
                    DecmedLog.w(
                        TAG,
                        "STRESS_BATCH_SKIPPED at=${Instant.ofEpochMilli(attemptAt)} atMillis=$attemptAt generatedRecords=$generatedRecords candidateRecords=${records.size} reason=no_unlocked_profile error=${err.message.orEmpty()}"
                    )
                    return@withLock null
                }
            val patientId = profile.iotaAddress ?: profile.idHash ?: profile.id
            val estimatedBytes = estimatePghdPayloadBytes(
                records = records,
                patientId = patientId,
                triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            )
            val recordsForBatch = selectStressRecordsWithinPayloadLimit(
                records = records,
                patientId = patientId,
                triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            )
            val selectedBytes = estimatePghdPayloadBytes(
                records = recordsForBatch,
                patientId = patientId,
                triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            )
            DecmedLog.i(
                TAG,
                "STRESS_BATCH_CREATE_ATTEMPT at=${Instant.ofEpochMilli(attemptAt)} atMillis=$attemptAt generatedRecords=$generatedRecords candidateRecords=${records.size} candidateBytes=$estimatedBytes selectedRecords=${recordsForBatch.size} selectedBytes=$selectedBytes threshold=${Env.pghdEarlyTriggerBytes}"
            )
            val batch = pghdBatchRepository.createEncryptedBatch(
                records = recordsForBatch,
                patientProfile = profile,
                triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            )
            pghdRepository.markRecordsBatched(recordsForBatch.map { it.uid }, batch.batchId)
            val successAt = System.currentTimeMillis()
            DecmedLog.i(
                TAG,
                "STRESS_BATCH_CREATE_SUCCESS at=${Instant.ofEpochMilli(successAt)} atMillis=$successAt generatedRecords=$generatedRecords batchId=${batch.batchId} selectedRecords=${recordsForBatch.size} selectedBytes=$selectedBytes candidateRecords=${records.size} candidateBytes=$estimatedBytes durationMs=${successAt - attemptAt}"
            )
            batch.batchId
            }
        }.getOrElse { err ->
            val failedAt = System.currentTimeMillis()
            DecmedLog.e(
                TAG,
                "STRESS_BATCH_CREATE_FAILED at=${Instant.ofEpochMilli(failedAt)} atMillis=$failedAt generatedRecords=$generatedRecords reason=${err.message.orEmpty()}",
                err
            )
            null
        }

        if (createdBatchId != null) {
            PghdWorkScheduler.scheduleSubmitWhenConnected(getApplication())
            refreshStressCurrentBatchProgress(generatedRecords)
        }
        return createdBatchId != null
    }

    private suspend fun refreshStressCurrentBatchProgress(generatedRecords: Int) {
        val remainingRecords = pghdRepository.getUnbatchedRecords()
        val remainingBytes = estimatePghdPayloadBytes(remainingRecords)
        val refreshedAt = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                stressTestProgress = state.stressTestProgress?.copy(
                    generatedRecords = generatedRecords,
                    currentBatchBytes = remainingBytes,
                    currentBatchRecordCount = remainingRecords.size
                )
            )
        }
        DecmedLog.i(
            TAG,
            "STRESS_BATCH_REMAINING at=${Instant.ofEpochMilli(refreshedAt)} atMillis=$refreshedAt generatedRecords=$generatedRecords unbatchedRecords=${remainingRecords.size} estimatedBytes=$remainingBytes threshold=${Env.pghdEarlyTriggerBytes}"
        )
    }

    private fun selectStressRecordsWithinPayloadLimit(
        records: List<PghdRecordEntity>,
        patientId: String,
        triggerReason: String
    ): List<PghdRecordEntity> {
        if (records.size <= 1) return records
        if (estimatePghdPayloadBytes(records, patientId, triggerReason) <= Env.pghdEarlyTriggerBytes) {
            return records
        }

        var low = 1
        var high = records.size
        var best = 1
        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = estimatePghdPayloadBytes(records.take(mid), patientId, triggerReason)
            if (bytes <= Env.pghdEarlyTriggerBytes) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        DecmedLog.i(
            TAG,
            "STRESS_BATCH_SELECT selectedRecords=$best candidateRecords=${records.size} threshold=${Env.pghdEarlyTriggerBytes}"
        )
        return records.take(best)
    }

    private fun scheduleStressBatchIfSizeExceeded(activeWindow: ActivePghdCollectionWindow?) {
        val progress = _uiState.value.stressTestProgress
        if (progress?.isRunning != true || activeWindow == null) return
        if (activeWindow.estimatedBytes < Env.pghdEarlyTriggerBytes) {
            lastStressSizeTriggerRecordCount = 0
            return
        }
        if (activeWindow.recordCount == lastStressSizeTriggerRecordCount) return

        lastStressSizeTriggerRecordCount = activeWindow.recordCount
        DecmedLog.i(
            TAG,
            "STRESS_SIZE_THRESHOLD_REACHED_OBSERVER unbatchedRecords=${activeWindow.recordCount} estimatedBytes=${activeWindow.estimatedBytes} threshold=${Env.pghdEarlyTriggerBytes}; stress generator performs immediate batching"
        )
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
                    val activeWindow = records.toActiveCollectionWindow(collectionState)
                    _uiState.update {
                        it.copy(
                            activeCollectionWindow = activeWindow,
                            stressTestProgress = it.stressTestProgress?.copy(
                                currentBatchBytes = activeWindow?.estimatedBytes ?: 0L,
                                currentBatchRecordCount = activeWindow?.recordCount ?: 0
                            )
                        )
                    }
                    scheduleStressBatchIfSizeExceeded(activeWindow)
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
                            dateFilterEndMillis = end,
                            stressTestProgress = it.stressTestProgress?.withBatchStats(batches)
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
        private const val STRESS_TEST_DAYS = 7
        private const val STRESS_TEST_INSERT_CHUNK_MINUTES = 40
        private const val STRESS_TEST_PROGRESS_UPDATE_MINUTES = 30
        private const val STRESS_TEST_SIZE_PROGRESS_LOG_RECORDS = 1_500
        private const val STRESS_TEST_SOURCE_PACKAGE_PREFIX = "com.hackastic.decmed.stress"
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

private fun estimatePghdPayloadBytes(
    records: List<PghdRecordEntity>,
    patientId: String = "local_patient",
    triggerReason: String = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
): Long =
    if (records.isEmpty()) {
        0L
    } else {
        runCatching {
            val payload = PghdPayloadConverter.recordsToBatchPayload(
                records = records,
                patientId = patientId,
                triggerReason = triggerReason
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

private data class StressTestDataType(
    val recordType: String,
    val displayName: String,
    val unit: String,
    val minValue: Double,
    val maxValue: Double,
    val precision: Int = 0
) {
    fun toStressRecord(
        runId: Long,
        minuteOffset: Int,
        timestamp: Long
    ): PghdRecordEntity {
        val value = syntheticValue(runId, minuteOffset)
        val valueText = if (precision <= 0) {
            value.toInt().toString()
        } else {
            "%.${precision}f".format(java.util.Locale.US, value)
        }
        return PghdRecordEntity(
            uid = "stress:$runId:$recordType:$minuteOffset",
            recordType = recordType,
            displayName = displayName,
            startTimeEpochMillis = timestamp - 60_000L,
            endTimeEpochMillis = timestamp,
            unit = unit,
            valueText = valueText,
            numericValue = value,
            sourceTag = PghdRecordEntity.SOURCE_PHONE_SENSOR,
            sourcePackageName = "com.hackastic.decmed.stress",
            notes = "Synthetic PGHD stress-test sample",
            syncedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun syntheticValue(runId: Long, minuteOffset: Int): Double {
        val random = Random(runId + recordType.hashCode() * 31L + minuteOffset)
        val wave = kotlin.math.sin((minuteOffset % 1440) / 1440.0 * 2.0 * Math.PI)
        val normalized = ((wave + 1.0) / 2.0 * 0.55) + (random.nextDouble() * 0.45)
        return minValue + ((maxValue - minValue) * normalized)
    }
}

private val STRESS_TEST_SENSOR_TYPES = listOf(
    StressTestDataType("steps", "Steps", "count", 0.0, 180.0),
    StressTestDataType("heart_rate", "Heart Rate", "bpm", 58.0, 132.0),
    StressTestDataType("oxygen_saturation", "Oxygen Saturation", "%", 94.0, 100.0),
    StressTestDataType("respiratory_rate", "Respiratory Rate", "breaths/min", 12.0, 24.0),
    StressTestDataType("resting_heart_rate", "Resting Heart Rate", "bpm", 52.0, 82.0),
    StressTestDataType("heart_rate_variability", "Heart Rate Variability", "ms", 20.0, 120.0),
    StressTestDataType("total_calories_burned", "Total Calories Burned", "kcal", 1.0, 5.0, precision = 2),
    StressTestDataType("active_calories_burned", "Active Calories Burned", "kcal", 0.0, 4.0, precision = 2),
    StressTestDataType("distance", "Distance", "m", 0.0, 160.0, precision = 2),
    StressTestDataType("speed", "Speed", "m/s", 0.0, 3.2, precision = 2),
    StressTestDataType("vo2_max", "VO2 Max", "mL/kg/min", 24.0, 58.0, precision = 1),
    StressTestDataType("skin_temperature", "Skin Temperature", "C", 32.0, 36.5, precision = 1),
    StressTestDataType("sleep_duration", "Sleep Duration", "min", 0.0, 60.0),
    StressTestDataType("floors_climbed", "Floors Climbed", "floors", 0.0, 4.0),
    StressTestDataType("elevation_gained", "Elevation Gained", "m", 0.0, 12.0, precision = 1)
)

private fun PghdStressTestProgress.withBatchStats(
    batches: List<PghdBatchEntity>
): PghdStressTestProgress {
    val stressBatches = batches.filter { it.createdAtEpochMillis >= startedAtEpochMillis }
    return copy(
        formedBatchCount = stressBatches.size,
        sentBatchCount = stressBatches.count { it.status == PghdBatchEntity.STATUS_SENT },
        failedBatchCount = stressBatches.count { it.status == PghdBatchEntity.STATUS_FAILED },
        waitingBatchCount = stressBatches.count { it.status == PghdBatchEntity.STATUS_WAITING_FOR_TRIGGER },
        pendingBatchCount = stressBatches.count { it.status == PghdBatchEntity.STATUS_PENDING }
    )
}

data class HospitalPersonnelIdentity(
    val iotaAddress: String,
    val displayName: String?,
    val hospitalName: String?,
    val publicMetadata: String?
)

private fun String?.requireNonBlank(label: String): String =
    require(!isNullOrBlank()) { "Missing $label." }.let { this!! }

private fun String?.requireIotaAddress(label: String): String {
    val value = requireNonBlank(label).trim()
    require(value.matches(Regex("^0x[0-9a-fA-F]{64}$"))) {
        "Invalid $label: expected 0x followed by 64 hex characters, got '$value'."
    }
    return value
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
