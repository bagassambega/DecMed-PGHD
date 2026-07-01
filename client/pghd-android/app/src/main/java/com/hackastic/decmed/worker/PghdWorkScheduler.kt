package com.hackastic.decmed.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.utils.DecmedLog
import java.util.concurrent.TimeUnit

object PghdWorkScheduler {
    private const val BATCH_WORK = "decmed_pghd_batch_work"
    private const val BATCH_NOW_WORK = "decmed_pghd_batch_now_work"
    private const val BATCH_NOW_TAG = "decmed_pghd_batch_now_tag"
    private const val CONNECTIVITY_SUBMIT_WORK = "decmed_pghd_connectivity_submit_work"
    private const val HEALTH_CONNECT_SYNC_WORK = "decmed_pghd_health_connect_sync_work"
    private const val HEALTH_CONNECT_SYNC_NOW_WORK = "decmed_pghd_health_connect_sync_now_work"
    private const val KEY_SHOW_TOAST = "show_toast"
    private const val KEY_RESCHEDULE = "reschedule"

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
            ExistingWorkPolicy.APPEND_OR_REPLACE,
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
            .setInputData(syncInputData(showToast = true, reschedule = false))
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HEALTH_CONNECT_SYNC_NOW_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleBatchNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PghdBatchWorker>()
            .addTag(BATCH_NOW_TAG)
            .build()

        DecmedLog.i("PghdWorkScheduler", "PGHD_BATCH_NOW_ENQUEUE workId=${request.id}")
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancelStressTestWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CONNECTIVITY_SUBMIT_WORK)
        WorkManager.getInstance(context).cancelAllWorkByTag(BATCH_NOW_TAG)
    }

    private fun scheduleHealthConnectSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<PghdHealthConnectSyncWorker>()
            .setInputData(syncInputData(showToast = true, reschedule = true))
            .setInitialDelay(
                Env.pghdHealthConnectSyncIntervalMinutes.coerceAtLeast(1),
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HEALTH_CONNECT_SYNC_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun scheduleNextHealthConnectSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<PghdHealthConnectSyncWorker>()
            .setInputData(syncInputData(showToast = true, reschedule = true))
            .setInitialDelay(
                Env.pghdHealthConnectSyncIntervalMinutes.coerceAtLeast(1),
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HEALTH_CONNECT_SYNC_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
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

    private fun syncInputData(showToast: Boolean, reschedule: Boolean): Data =
        Data.Builder()
            .putBoolean(KEY_SHOW_TOAST, showToast)
            .putBoolean(KEY_RESCHEDULE, reschedule)
            .build()

    fun shouldShowToast(inputData: Data): Boolean =
        inputData.getBoolean(KEY_SHOW_TOAST, false)

    fun shouldReschedule(inputData: Data): Boolean =
        inputData.getBoolean(KEY_RESCHEDULE, false)
}
