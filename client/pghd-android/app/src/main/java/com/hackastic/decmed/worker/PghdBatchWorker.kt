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
import kotlinx.coroutines.flow.first

class PghdBatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        val createdBatch = PghdBatchCreationGuard.withLock {
            val collectionState = container.pghdCollectionStateRepository.state.first()
            if (!collectionState.enabled) return@withLock null

            val records = collectionState.startedAtEpochMillis
                ?.let { container.pghdRepository.getUnbatchedRecordsSince(it) }
                ?: container.pghdRepository.getUnbatchedRecords()
            if (records.isEmpty()) return@withLock null

            val profile = runCatching { container.patientAuthRepository.getUnlockedProfile() }
                .getOrElse { return@withLock null }
            val patientId = profile.iotaAddress ?: profile.idHash ?: profile.id

            val estimatedPayload = PghdPayloadConverter.recordsToBatchPayload(
                records = records,
                patientId = patientId,
                triggerReason = PghdBatchPayload.TRIGGER_TIME_BASED
            )
            val triggerReason = if (
                PghdPayloadSerializer.toJson(estimatedPayload).toByteArray(Charsets.UTF_8).size >
                Env.pghdEarlyTriggerBytes
            ) {
                PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
            } else {
                PghdBatchPayload.TRIGGER_TIME_BASED
            }

            val collectionEndedAt = System.currentTimeMillis()
            val batch = container.pghdBatchRepository.createEncryptedBatch(
                records = records,
                patientProfile = profile,
                collectionStartedAtEpochMillis = collectionState.startedAtEpochMillis,
                collectionEndedAtEpochMillis = collectionEndedAt,
                triggerReason = triggerReason
            )
            container.pghdRepository.markRecordsBatched(records.map { it.uid }, batch.batchId)
            if (collectionState.enabled) {
                container.pghdCollectionStateRepository.restartWindow(collectionEndedAt)
            }
            CreatedPghdBatch(batch.batchId, triggerReason, profile)
        }

        if (createdBatch == null) return Result.success()

        container.prePghdClient.pushRegistration(createdBatch.profile)
        val submitResult = container.pghdBatchRepository.submitBatch(
            batchId = createdBatch.batchId,
            submitTriggerReason = createdBatch.triggerReason
        )
        if (!submitResult.accepted) {
            PghdWorkScheduler.scheduleSubmitWhenConnected(applicationContext)
        }
        return Result.success()
    }

    private data class CreatedPghdBatch(
        val batchId: String,
        val triggerReason: String,
        val profile: PatientProfile
    )
}
