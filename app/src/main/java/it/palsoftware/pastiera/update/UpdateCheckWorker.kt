package it.palsoftware.pastiera.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.getUpdateCheckHours
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Periodic worker that checks for new app updates using the existing
 * GitHub-based update checker and shows a notification when a new
 * version is available.
 */
class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        if (!shouldUseGithubUpdateChecks(context)) {
            return Result.success()
        }

        // Use a latch to bridge the async callback-based API with the
        // synchronous Worker API.
        val resultRef = AtomicReference<UpdateCheckResult?>()
        val completedRef = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        checkForUpdateNotices(
            context = context,
            releaseChannel = BuildConfig.RELEASE_CHANNEL,
            // Respect releases dismissed by the user via the dialog.
            ignoreDismissedReleases = true
        ) { result ->
            resultRef.set(result)
            completedRef.set(true)
            latch.countDown()
        }

        // Wait for the network response (or timeout).
        val awaitSuccess = latch.await(30, TimeUnit.SECONDS)
        val result = resultRef.get()
        if (!awaitSuccess || !completedRef.get() || result == null) {
            // Network error or timeout: ask WorkManager to retry later.
            return Result.retry()
        }

        var announcement: UpdateCheckResult? = result
        while (announcement != null) {
            if (announcement.hasAnnouncement && announcement.displayName != null) {
                if (announcement.isForkUpdate) announcement.releaseTag?.let { rememberAnnouncedForkRelease(context, it) }
                NotificationHelper.showUpdateAvailableNotification(
                    context = context,
                    displayName = announcement.displayName,
                    releasePageUrl = announcement.releasePageUrl,
                    isNightlyUpdate = announcement.isNightlyUpdate,
                    isPastieraStableUpdate = announcement.isPastieraStableUpdate,
                    isForkUpdate = announcement.isForkUpdate,
                    downloadUrl = announcement.downloadUrl
                )
            }
            announcement = announcement.followUpAnnouncement
        }

        if (!result.successful) {
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pastiera_update_check"

        /**
         * Schedules a background update check every few hours (2 unless chosen: 1, 2, 6 or 24),
         * in the last quarter of each period so Android can run it alongside its other work
         * rather than waking the phone for it. A schedule from an earlier version is replaced.
         * Each check is one small request, and while nothing has changed GitHub answers "not
         * modified" (see checkRelease). Settings' Check for updates looks at once.
         */
        fun schedule(context: Context) {
            if (!shouldUseGithubUpdateChecks(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val hours = SettingsManager.getUpdateCheckHours(context).toLong()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                hours, TimeUnit.HOURS,
                hours * 15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                // A failed check (offline, GitHub not answering) is tried again after a while, not at once
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
