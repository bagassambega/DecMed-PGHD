package com.hackastic.decmed.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env

class PghdSubmitWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        if (!container.pghdCollectionStateRepository.isEnabled()) return Result.success()

        container.pghdBatchRepository.submitPendingBatches(Env.pghdMaxRetryCount)
        PghdWorkScheduler.scheduleRetry(applicationContext)
        return Result.success()
    }
}
