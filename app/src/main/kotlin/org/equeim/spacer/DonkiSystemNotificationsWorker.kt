// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.equeim.spacer.donki.data.common.DonkiException
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException
import org.equeim.spacer.donki.data.notifications.cache.CachedNotification
import java.time.Duration

class DonkiSystemNotificationsWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() called")

        val notificationsManager = DonkiSystemNotificationsManager(applicationContext)
        notificationsManager.diagnoseNotificationsIssues().takeIf { it.isNotEmpty() }?.let { issues ->
            Log.e(TAG, "doWork: can't show notifications: $issues")
            // This is technically an error but we want to continue to run the worker periodically in case user enables notifications
            // We can't just stop the worker since notifications can be enabled without opening the app in which case we won't be able to start the worker again
            return Result.success()
        }

        Log.d(TAG, "doWork: updating notifications")
        var newUnreadNotifications: List<CachedNotification>?
        var error: DonkiException?
        var result: Result
        try {
            newUnreadNotifications = getDonkiNotificationsRepositoryInstance(applicationContext)
                .performBackgroundUpdate(includingCachedRecently = true)
                ?.newUnreadNotifications
            error = null
            result = Result.success()
            if (newUnreadNotifications != null) {
                Log.d(
                    TAG,
                    "doWork: updated, new unread notifications = ${newUnreadNotifications.map { it.id }}"
                )
            } else {
                Log.d(TAG, "doWork: already updating")
            }
        } catch (e: DonkiException) {
            Log.e(TAG, "doWork: update failed", e)
            newUnreadNotifications = null
            error = e
            // We are unlikely to recover from something other than a network error, so stop the worker in that case
            result = if (e is DonkiNetworkDataSourceException) Result.retry() else Result.failure()
        }
        when {
            newUnreadNotifications?.isNotEmpty() == true ->
                notificationsManager.showNotifications(newUnreadNotifications)
            error != null -> notificationsManager.showFailedUpdateNotification(error)
        }
        return result
    }

    enum class BackgroundUpdateIssue {
        NotIgnoringBatteryOptimizations,
        RestrictedBackgroundDataUsage
    }

    companion object {
        private const val TAG = "DonkiNotificationsWorker"
        const val WORK_NAME = "notifications"

        val INTERVALS: List<Duration> = listOf(
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(3),
            Duration.ofHours(6),
            Duration.ofHours(12),
            Duration.ofHours(24)
        )

        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(30)

        fun diagnoseBackgroundUpdateIssues(context: Context): List<BackgroundUpdateIssue> = buildList {
            val powerManager = context.getSystemService<PowerManager>()
            if (powerManager != null) {
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    Log.e(TAG, "diagnoseBackgroundUpdateIssues: not ignoring battery optimizations")
                    add(BackgroundUpdateIssue.NotIgnoringBatteryOptimizations)
                }
            } else {
                Log.e(TAG, "diagnoseBackgroundUpdateIssues: PowerManager is null")
            }
            val connectivityManager = context.getSystemService<ConnectivityManager>()
            if (connectivityManager != null) {
                if (connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
                    Log.e(TAG, "diagnoseBackgroundUpdateIssues: background data usage is restricted")
                    add(BackgroundUpdateIssue.RestrictedBackgroundDataUsage)
                }
            } else {
                Log.e(TAG, "diagnoseBackgroundUpdateIssues: ConnectivityManager is null")
            }
            Log.d(TAG, "diagnoseBackgroundUpdateIssues() returned: $this")
        }
    }
}
