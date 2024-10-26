// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class NotificationsDataSourceCache(
    private val context: Context,
    db: NotificationsDatabase?,
    coroutineDispatchers: CoroutineDispatchers,
    private val clock: Clock,
) : Closeable {
    private val coroutineScope = CoroutineScope(coroutineDispatchers.IO + SupervisorJob())

    private val db: Deferred<NotificationsDatabase> = if (db != null) {
        CompletableDeferred(db)
    } else {
        coroutineScope.async {
            Room.databaseBuilder(
                context,
                NotificationsDatabase::class.java,
                NotificationsDatabase.NAME
            ).build()
        }
    }

    override fun close() {
        Log.d(TAG, "close() called")
        coroutineScope.cancel()
    }

    suspend fun isWeekCachedAndNeedsRefresh(week: Week, refreshIfRecentlyLoaded: Boolean): Boolean {
        val cacheLoadTime = db.await().cachedWeeks()
            .getWeekLoadTime(week.weekBasedYear, week.weekOfWeekBasedYear)
        return cacheLoadTime != null && week.needsRefresh(cacheLoadTime, refreshIfRecentlyLoaded)
    }

    suspend fun getNotificationSummariesForWeek(
        week: Week,
        types: List<NotificationType>,
        dateRange: DateRange?,
        returnCacheThatNeedsRefreshing: Boolean,
    ): List<CachedNotificationSummary>? {
        Log.d(
            TAG,
            "getNotificationSummariesForWeek() called with: week = $week, types = $types, dateRange = $dateRange, returnCacheThatNeedsRefreshing = $returnCacheThatNeedsRefreshing"
        )
        val db = this.db.await()
        return try {
            val weekCacheTime = db.cachedWeeks()
                .getWeekLoadTime(week.weekBasedYear, week.weekOfWeekBasedYear)
            if (weekCacheTime == null) {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: no cache for week = $week, returning null"
                )
                return null
            }
            if (!returnCacheThatNeedsRefreshing && week.needsRefresh(weekCacheTime, refreshIfRecentlyLoaded = true)) {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: cache needs refreshing for week = $week, returning null"
                )
                return null
            }
            val startTime: Instant
            val endTime: Instant
            if (dateRange != null) {
                startTime = dateRange.firstDayInstant
                endTime = dateRange.instantAfterLastDay
            } else {
                startTime = week.getFirstDayInstant()
                endTime = week.getInstantAfterLastDay()
            }
            db.cachedNotifications().getNotificationSummaries(startTime, endTime, types).also {
                Log.d(
                    TAG,
                    "getNotificationSummariesForWeek: returning ${it.size} events for week = $week, types = $types"
                )
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(
                    TAG,
                    "getNotificationSummariesForWeek: failed to get events summaries for week = $week, types = $types",
                    e
                )
            }
            throw e
        }
    }

    suspend fun getCachedNotificationByIdAndMarkAsRead(id: NotificationId): CachedNotification? {
        Log.d(TAG, "getNotificationById() called with: id = $id")
        try {
            val dao = db.await().cachedNotifications()
            val notification = dao.getNotificationById(id)
            return if (notification != null && !notification.read) {
                dao.markNotificationAsRead(id)
                notification.copy(read = true)
            } else {
                notification
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(
                    TAG,
                    "getNotificationById: failed to get notification $id",
                    e
                )
            }
            throw e
        }
    }

    private fun Week.needsRefresh(
        cacheLoadTime: Instant,
        refreshIfRecentlyLoaded: Boolean,
    ): Boolean {
        return cacheLoadTime < getInstantAfterLastDay() &&
                (refreshIfRecentlyLoaded || Duration.between(
                    cacheLoadTime,
                    Instant.now(clock)
                ) > WINDOW_BETWEEN_LOAD_TIME_AND_CURRENT_TIME_WHEN_REFRESH_IS_NOT_NEEDED)
    }

    suspend fun cacheWeek(
        week: Week,
        notifications: List<CachedNotification>,
        loadTime: Instant,
    ) {
        Log.d(
            TAG,
            "cacheWeek() called with: week = $week, notifications count = ${notifications.size}, loadTime = $loadTime"
        )
        val db = this.db.await()
        db.withTransaction {
            Log.d(TAG, "cacheWeek: starting transaction for $week")
            db.cachedWeeks()
                .updateWeek(
                    CachedNotificationsWeek(
                        week.weekBasedYear,
                        week.weekOfWeekBasedYear,
                        loadTime
                    )
                )
            if (notifications.isNotEmpty()) {
                db.cachedNotifications().storeNotifications(notifications)
            }
            Log.d(TAG, "cacheWeek: completing transaction for $week")
        }
    }

    private companion object {
        const val TAG = "DonkiNotificationsDataSourceCache"
        val WINDOW_BETWEEN_LOAD_TIME_AND_CURRENT_TIME_WHEN_REFRESH_IS_NOT_NEEDED: Duration =
            Duration.ofHours(1)
    }
}