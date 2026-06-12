package com.hackastic.decmed.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.data.pghd.PghdPayloadSerializer
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload

class PghdBatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        if (!container.pghdCollectionStateRepository.isEnabled()) return Result.success()

        val records = container.pghdRepository.getUnbatchedRecords()
        if (records.isEmpty()) return Result.success()

        val profile = runCatching { container.patientAuthRepository.getUnlockedProfile() }
            .getOrElse { return Result.retry() }
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

        val batch = container.pghdBatchRepository.createEncryptedBatch(
            records = records,
            patientProfile = profile,
            triggerReason = triggerReason
        )
        container.pghdRepository.markRecordsBatched(records.map { it.uid }, batch.batchId)
        container.prePghdClient.pushRegistration(profile)
        val submitResult = container.pghdBatchRepository.submitBatch(
            batchId = batch.batchId,
            submitTriggerReason = triggerReason
        )
        if (!submitResult.accepted) {
            PghdWorkScheduler.scheduleSubmitWhenConnected(applicationContext)
        }
        return Result.success()
    }
}
