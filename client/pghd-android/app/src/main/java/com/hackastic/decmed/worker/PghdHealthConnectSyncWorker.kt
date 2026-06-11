package com.hackastic.decmed.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.config.Env

class PghdHealthConnectSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MainApplication).container
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
        val records = client.readRecentPghd(daysBack)
        container.pghdRepository.saveHealthConnectRecords(records)
        return Result.success()
    }
}
