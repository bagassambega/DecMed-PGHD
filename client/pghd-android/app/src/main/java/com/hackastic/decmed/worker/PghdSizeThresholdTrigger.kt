package com.hackastic.decmed.worker

import android.content.Context
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.local.database.SensorDatabase
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.data.pghd.PghdPayloadSerializer
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.utils.DecmedLog
import kotlinx.coroutines.flow.first

object PghdSizeThresholdTrigger {
    suspend fun scheduleBatchIfExceeded(
        context: Context,
        database: SensorDatabase,
        sourceLabel: String
    ) {
        val collectionState = (context.applicationContext as? MainApplication)
            ?.container
            ?.pghdCollectionStateRepository
            ?.state
            ?.first()
        val activeStartedAt = collectionState
            ?.takeIf { it.enabled }
            ?.startedAtEpochMillis
        val records = activeStartedAt
            ?.let { database.pghdRecordDao().getUnbatchedRecordsSince(it) }
            ?: database.pghdRecordDao().getUnbatchedRecords()
        val estimatedBytes = estimatePayloadBytes(records)
        if (estimatedBytes > Env.pghdEarlyTriggerBytes) {
            DecmedLog.i(
                TAG,
                "Scheduling immediate PGHD batch from $sourceLabel because unbatched payload size=$estimatedBytes exceeds threshold=${Env.pghdEarlyTriggerBytes}"
            )
            PghdWorkScheduler.scheduleBatchNow(context.applicationContext)
        }
    }

    private fun estimatePayloadBytes(records: List<PghdRecordEntity>): Long =
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

    private const val TAG = "PghdSizeThresholdTrigger"
}
