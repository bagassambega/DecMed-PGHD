package com.hackastic.decmed.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.utils.DecmedLog

class PghdSubmitWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        if (container.pghdBatchRepository.getPendingSubmitBatchCount() == 0) {
            DecmedLog.i(TAG, "Skipping PGHD submit worker because no pending/failed batch is available.")
            return Result.success()
        }

        val profile = runCatching { container.patientAuthRepository.getUnlockedProfile() }
            .getOrElse { err ->
                DecmedLog.w(TAG, "Skipping PGHD reconnect submit because patient session is locked or unavailable. ${err.message.orEmpty()}")
                return Result.success()
            }
        runCatching {
            container.prePghdClient.pushRegistration(profile)
        }.onFailure { err ->
            DecmedLog.w(
                TAG,
                "Unable to refresh PGHD registration before reconnect submit; continuing with pending batch retry. ${err.message.orEmpty()}"
            )
        }
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

    private companion object {
        const val TAG = "PghdSubmitWorker"
    }
}
