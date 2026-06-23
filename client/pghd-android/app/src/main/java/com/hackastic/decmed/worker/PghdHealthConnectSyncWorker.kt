package com.hackastic.decmed.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.local.database.SensorDatabase
import java.time.Instant

class PghdHealthConnectSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
        val shouldReschedule = PghdWorkScheduler.shouldReschedule(inputData)
        val shouldShowToast = PghdWorkScheduler.shouldShowToast(inputData)

        try {
            if (!container.pghdCollectionStateRepository.isEnabled()) return Result.success()

            val client = container.healthConnectPghdClient

            if (!client.isAvailable()) return Result.success()

            val permissionState = client.getPermissionState()
            if (!permissionState.hasRequiredDataPermissions) return Result.success()

            val daysBack = if (permissionState.hasHistoryPermission) {
                Env.pghdHistorySyncDays
            } else {
                Env.pghdDefaultSyncDays
            }
            val latestHealthConnectEnd = container.pghdRepository.getLatestHealthConnectEndTimeMillis()
            val syncStart = latestHealthConnectEnd
                ?.let { Instant.ofEpochMilli(it + 1) }
                ?: Instant.now().minusSeconds(daysBack * 24L * 60L * 60L)
            val records = client.readPghdSince(syncStart)
            container.pghdRepository.saveHealthConnectRecords(records)
            PghdSizeThresholdTrigger.scheduleBatchIfExceeded(
                context = applicationContext,
                database = SensorDatabase.getDatabase(applicationContext),
                sourceLabel = "Health Connect sync worker"
            )
            PghdTimeThresholdTrigger.scheduleBatchIfElapsed(
                context = applicationContext,
                database = SensorDatabase.getDatabase(applicationContext),
                sourceLabel = "Health Connect sync worker"
            )
            if (shouldShowToast) {
                showToast("Health Connect sync succeeded: ${records.size} records.")
            }
            return Result.success()
        } catch (err: Exception) {
            if (shouldShowToast) {
                showToast("Health Connect sync failed: ${err.message ?: "unknown error"}")
            }
            return Result.success()
        } finally {
            if (shouldReschedule && container.pghdCollectionStateRepository.isEnabled()) {
                PghdWorkScheduler.scheduleNextHealthConnectSync(applicationContext)
            }
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
