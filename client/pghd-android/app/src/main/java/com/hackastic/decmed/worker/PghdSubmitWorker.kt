package com.hackastic.decmed.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.data.local.entity.PghdBatchEntity

class PghdSubmitWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        if (!container.pghdCollectionStateRepository.isEnabled()) return Result.success()

        val profile = runCatching { container.patientAuthRepository.getUnlockedProfile() }
            .getOrElse { return Result.retry() }
        container.prePghdClient.pushRegistration(profile)
        val results = container.pghdBatchRepository.submitPendingBatches(
            submitTriggerReason = PghdBatchEntity.TRIGGER_NETWORK_AVAILABLE
        )
        if (results.isNotEmpty()) {
            val sentCount = results.count { it.accepted }
            val failedCount = results.size - sentCount
            val message = if (failedCount == 0) {
                "PGHD sync succeeded: $sentCount batch(es) sent."
            } else {
                "PGHD sync finished: $sentCount sent, $failedCount failed. Open Batches for details."
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
        return Result.success()
    }
}
