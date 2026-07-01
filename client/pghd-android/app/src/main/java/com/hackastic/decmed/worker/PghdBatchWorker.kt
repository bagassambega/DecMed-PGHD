package com.hackastic.decmed.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.data.pghd.PghdPayloadSerializer
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.utils.DecmedLog
import kotlinx.coroutines.flow.first

class PghdBatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        DecmedLog.i(TAG, "PGHD_BATCH_WORKER_START workId=$id")
        return runCatching { createBatchAndScheduleSubmit() }
            .getOrElse { err ->
                DecmedLog.e(TAG, "PGHD_BATCH_WORKER_FAILED workId=$id reason=${err.message.orEmpty()}", err)
                Result.failure()
            }
    }

    private suspend fun createBatchAndScheduleSubmit(): Result {
        val container = (applicationContext as MainApplication).container
        val createdBatch = PghdBatchCreationGuard.withLock {
            val collectionState = container.pghdCollectionStateRepository.state.first()
            val activeStartedAt = collectionState
                .takeIf { it.enabled }
                ?.startedAtEpochMillis

            val records = activeStartedAt
                ?.let { container.pghdRepository.getUnbatchedRecordsSince(it) }
                ?: container.pghdRepository.getUnbatchedRecords()
            if (records.isEmpty()) {
                DecmedLog.i(TAG, "PGHD_BATCH_CREATE_SKIPPED workId=$id reason=no_unbatched_records")
                return@withLock null
            }

            val profile = runCatching { container.patientAuthRepository.getUnlockedProfile() }
                .getOrElse { err ->
                    DecmedLog.w(
                        TAG,
                        "PGHD_BATCH_CREATE_SKIPPED workId=$id reason=no_unlocked_profile error=${err.message.orEmpty()}"
                    )
                    return@withLock null
                }
            val patientId = profile.iotaAddress ?: profile.idHash ?: profile.id

            val estimatedBytes = estimatePayloadBytes(
                records = records,
                patientId = patientId,
                triggerReason = PghdBatchPayload.TRIGGER_TIME_BASED
            )
            val triggerReason = if (estimatedBytes > Env.pghdEarlyTriggerBytes) {
                PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            } else {
                PghdBatchPayload.TRIGGER_TIME_BASED
            }
            DecmedLog.i(
                TAG,
                "PGHD_BATCH_CREATE_ATTEMPT workId=$id candidateRecords=${records.size} estimatedBytes=$estimatedBytes threshold=${Env.pghdEarlyTriggerBytes} triggerReason=$triggerReason activeStartedAt=$activeStartedAt"
            )
            val recordsForBatch = selectRecordsWithinPayloadLimit(
                records = records,
                patientId = patientId,
                triggerReason = triggerReason
            )

            val collectionEndedAt = System.currentTimeMillis()
            val batch = container.pghdBatchRepository.createEncryptedBatch(
                records = recordsForBatch,
                patientProfile = profile,
                collectionStartedAtEpochMillis = activeStartedAt,
                collectionEndedAtEpochMillis = activeStartedAt?.let { collectionEndedAt },
                triggerReason = triggerReason
            )
            container.pghdRepository.markRecordsBatched(recordsForBatch.map { it.uid }, batch.batchId)
            if (activeStartedAt != null) {
                container.pghdCollectionStateRepository.restartWindow(collectionEndedAt)
            }
            DecmedLog.i(
                TAG,
                "PGHD_BATCH_CREATE_SUCCESS workId=$id batchId=${batch.batchId} selectedRecords=${recordsForBatch.size} candidateRecords=${records.size} triggerReason=$triggerReason"
            )
            CreatedPghdBatch(batch.batchId, triggerReason, profile)
        }

        if (createdBatch == null) {
            DecmedLog.i(TAG, "PGHD_BATCH_WORKER_FINISH workId=$id result=no_batch_created")
            return Result.success()
        }

        runCatching {
            container.prePghdClient.pushRegistration(createdBatch.profile)
        }.onFailure { err ->
            DecmedLog.w(
                TAG,
                "Unable to refresh PGHD registration before submit; continuing so the batch enters retry state. ${err.message.orEmpty()}"
            )
        }
        PghdWorkScheduler.scheduleSubmitWhenConnected(applicationContext)
        scheduleNextBatchIfRemainingPayloadStillLarge(container)
        DecmedLog.i(
            TAG,
            "PGHD_BATCH_WORKER_FINISH workId=$id result=batch_created batchId=${createdBatch.batchId} triggerReason=${createdBatch.triggerReason}"
        )
        return Result.success()
    }

    private suspend fun scheduleNextBatchIfRemainingPayloadStillLarge(container: com.hackastic.decmed.di.AppContainer) {
        val collectionState = container.pghdCollectionStateRepository.state.first()
        val remainingRecords = collectionState.startedAtEpochMillis
            ?.takeIf { collectionState.enabled }
            ?.let { container.pghdRepository.getUnbatchedRecordsSince(it) }
            ?: container.pghdRepository.getUnbatchedRecords()
        if (remainingRecords.isEmpty()) return
        val estimatedPayload = PghdPayloadConverter.recordsToBatchPayload(
            records = remainingRecords,
            patientId = "local_patient",
            triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
        )
        val estimatedBytes = PghdPayloadSerializer.toJson(estimatedPayload).toByteArray(Charsets.UTF_8).size
        if (estimatedBytes > Env.pghdEarlyTriggerBytes) {
            DecmedLog.i(
                TAG,
                "Scheduling next PGHD batch because remaining unbatched payload size=$estimatedBytes exceeds threshold=${Env.pghdEarlyTriggerBytes}"
            )
            PghdWorkScheduler.scheduleBatchNow(applicationContext)
        }
    }

    private fun selectRecordsWithinPayloadLimit(
        records: List<com.hackastic.decmed.data.local.entity.PghdRecordEntity>,
        patientId: String,
        triggerReason: String
    ): List<com.hackastic.decmed.data.local.entity.PghdRecordEntity> {
        if (records.size <= 1) return records
        if (estimatePayloadBytes(records, patientId, triggerReason) <= Env.pghdEarlyTriggerBytes) {
            return records
        }

        var low = 1
        var high = records.size
        var best = 1
        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = estimatePayloadBytes(records.take(mid), patientId, triggerReason)
            if (bytes <= Env.pghdEarlyTriggerBytes) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        DecmedLog.i(
            TAG,
            "Selected $best/${records.size} PGHD records for batch to stay under threshold=${Env.pghdEarlyTriggerBytes}"
        )
        return records.take(best)
    }

    private fun estimatePayloadBytes(
        records: List<com.hackastic.decmed.data.local.entity.PghdRecordEntity>,
        patientId: String,
        triggerReason: String
    ): Int {
        val payload = PghdPayloadConverter.recordsToBatchPayload(
            records = records,
            patientId = patientId,
            triggerReason = triggerReason
        )
        return PghdPayloadSerializer.toJson(payload).toByteArray(Charsets.UTF_8).size
    }

    private data class CreatedPghdBatch(
        val batchId: String,
        val triggerReason: String,
        val profile: PatientProfile
    )

    private companion object {
        const val TAG = "PghdBatchWorker"
    }
}
