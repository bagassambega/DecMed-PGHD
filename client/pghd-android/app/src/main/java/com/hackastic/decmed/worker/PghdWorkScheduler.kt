package com.hackastic.decmed.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackastic.decmed.config.Env
import java.util.concurrent.TimeUnit

object PghdWorkScheduler {
    private const val BATCH_WORK = "decmed_pghd_batch_work"
    private const val BATCH_NOW_WORK = "decmed_pghd_batch_now_work"
    private const val CONNECTIVITY_SUBMIT_WORK = "decmed_pghd_connectivity_submit_work"
    private const val HEALTH_CONNECT_SYNC_WORK = "decmed_pghd_health_connect_sync_work"
    private const val HEALTH_CONNECT_SYNC_NOW_WORK = "decmed_pghd_health_connect_sync_now_work"

    fun scheduleAll(context: Context) {
        PghdNetworkMonitor.start(context.applicationContext)
        scheduleSubmitWhenConnected(context)
    }

    fun scheduleSubmitWhenConnected(context: Context) {
        val request = OneTimeWorkRequestBuilder<PghdSubmitWorker>()
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CONNECTIVITY_SUBMIT_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleCollectionWork(context: Context) {
        scheduleHealthConnectSync(context)
        scheduleBatching(context)
        scheduleSubmitWhenConnected(context)
    }

    fun cancelCollectionWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CONNECTIVITY_SUBMIT_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(HEALTH_CONNECT_SYNC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(HEALTH_CONNECT_SYNC_NOW_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(BATCH_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(BATCH_NOW_WORK)
    }

    fun scheduleHealthConnectSyncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PghdHealthConnectSyncWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HEALTH_CONNECT_SYNC_NOW_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleBatchNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PghdBatchWorker>().build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            BATCH_NOW_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleHealthConnectSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<PghdHealthConnectSyncWorker>(
            Env.pghdBatchIntervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HEALTH_CONNECT_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleBatching(context: Context) {
        val request = PeriodicWorkRequestBuilder<PghdBatchWorker>(
            Env.pghdBatchIntervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BATCH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
