package com.dawood.orbit.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic background check against GitHub Releases. Downloads the APK when
 * auto-update is on, then posts a notification so the user can install.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = AppUpdateManager.get(applicationContext)
        if (!manager.autoUpdateEnabled) return Result.success()
        return when (val result = manager.checkAndMaybeDownload(forceDownload = false)) {
            is UpdateResult.Error -> Result.retry()
            else -> Result.success()
        }
    }
}

object UpdateScheduler {
    private const val UNIQUE_NAME = "orbit_update_check"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Minimum period for PeriodicWorkRequest is 15 minutes; 12h is enough
        // for sideloaded builds without burning battery.
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .addTag(UNIQUE_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
    }

    /** One-off immediate check (manual "Check now" from settings). */
    fun enqueueOnce(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag("${UNIQUE_NAME}_once")
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
