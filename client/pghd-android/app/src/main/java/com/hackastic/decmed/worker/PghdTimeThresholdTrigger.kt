package com.hackastic.decmed.worker

import android.content.Context
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.local.database.SensorDatabase
import com.hackastic.decmed.utils.DecmedLog
import kotlinx.coroutines.flow.first

object PghdTimeThresholdTrigger {
    suspend fun scheduleBatchIfElapsed(
        context: Context,
        database: SensorDatabase,
        sourceLabel: String
    ) {
        val appContext = context.applicationContext
        val collectionState = (appContext as? MainApplication)
            ?.container
            ?.pghdCollectionStateRepository
            ?.state
            ?.first()
            ?: return
        val startedAt = collectionState.takeIf { it.enabled }?.startedAtEpochMillis ?: return
        val elapsedMillis = System.currentTimeMillis() - startedAt
        val thresholdMillis = Env.pghdBatchIntervalMinutes.coerceAtLeast(15) * 60_000L
        if (elapsedMillis < thresholdMillis) return

        val records = database.pghdRecordDao().getUnbatchedRecordsSince(startedAt)
        if (records.isEmpty()) return

        DecmedLog.i(
            TAG,
            "Scheduling immediate PGHD batch from $sourceLabel because active window elapsed=${elapsedMillis}ms threshold=${thresholdMillis}ms records=${records.size}"
        )
        PghdWorkScheduler.scheduleBatchNow(appContext)
    }

    private const val TAG = "PghdTimeThresholdTrigger"
}
