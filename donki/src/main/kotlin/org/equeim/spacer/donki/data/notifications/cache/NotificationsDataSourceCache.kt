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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.intersect
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

    private val _markedNotificationsAsRead = Channel<Unit>(Channel.CONFLATED)
    val markedNotificationsAsRead: ReceiveChannel<Unit> by ::_markedNotificationsAsRead

    override fun close() {
        Log.d(TAG, "close() called")
        db.invokeOnCompletion { cause ->
            if (cause == null) {
                @OptIn(ExperimentalCoroutinesApi::class)
                db.getCompleted().close()
            }
        }
        coroutineScope.cancel()
    }

    data class WeekThatNeedsRefresh(val week: Week, val cachedRecently: Boolean)

    fun getWeeksThatNeedRefresh(dateRange: DateRange?): Flow<List<WeekThatNeedsRefresh>> {
        Log.d(
            TAG,
            "getWeeksThatNeedRefresh() called with: dateRange = $dateRange"
        )
        return flow { emitAll(db.await().cachedWeeks().getWeeksThatNeedRefresh()) }.map { weeks ->
            if (weeks.isEmpty()) {
                Log.d(TAG, "getWeeksThatNeedRefresh: no weeks need refresh")
                return@map emptyList()
            }
            Log.d(TAG, "getWeeksThatNeedRefresh: all weeks that need refresh:\n${weeks.joinToString("\n") { it.toLogString(clock) }}")
            var filtered: Sequence<CachedNotificationsWeek> = weeks.asSequence()
            if (dateRange != null) {
                filtered = filtered.filter { dateRange.intersect(it.toDateRange()) }
            }
            filtered.map {
                WeekThatNeedsRefresh(
                    week = Week.fromInstant(it.timeAtStartOfFirstDay),
                    cachedRecently = Duration.between(
                        it.loadTime,
                        Instant.now(clock)
                    ) < RECENTLY_CACHED_INTERVAL
                )
            }.toList().also {
                Log.d(TAG, "getWeeksThatNeedRefresh() returned:\n${it.joinToString("\n")}")
            }
        }.catch {
            Log.e(TAG, "getWeeksThatNeedRefresh: db error with: dateRange = $dateRange", it)
        }
    }

    suspend fun getNotificationSummariesForWeek(
        week: Week,
        types: List<NotificationType>,
        dateRange: DateRange?,
    ): List<CachedNotificationSummary>? {
        Log.d(
            TAG,
            "getNotificationSummariesForWeek() called with: week = $week, types = $types, dateRange = $dateRange"
        )
        return try {
            val db = this.db.await()
            if (!db.cachedWeeks().isWeekCached(week.getFirstDayInstant())) {
                Log.d(
                    TAG,
                    "getNotificationSummariesForWeek: no cache for week = $week, returning null"
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
                    "getNotificationSummariesForWeek: db error with: week = $week, types = $types",
                    e
                )
            }
            throw e
        }
    }

    fun getNumberOfUnreadNotifications(): Flow<Int> = flow {
        emitAll(db.await().cachedNotifications().getNumberOfUnreadNotifications())
    }.catch {
        Log.e(
            TAG,
            "getNumberOfUnreadNotifications: failed to get number of unread notifications",
            it
        )
        emit(0)
    }

    suspend fun getCachedNotificationByIdAndMarkAsRead(id: NotificationId): CachedNotification? {
        Log.d(TAG, "getNotificationById() called with: id = $id")
        try {
            val dao = db.await().cachedNotifications()
            val notification = dao.getNotificationById(id)
            return if (notification != null && !notification.read) {
                coroutineScope.launch { markNotificationAsRead(id) }
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

    private suspend fun markNotificationAsRead(id: NotificationId) {
        Log.d(TAG, "markNotificationAsRead() called with: id = $id")
        try {
            db.await().cachedNotifications().markNotificationAsRead(id)
            Log.d(TAG, "markNotificationAsRead: sending event")
            _markedNotificationsAsRead.send(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "markNotificationAsRead: failed to mark notification $id as read", e)
        }
    }

    suspend fun markAllNotificationsAsRead() {
        Log.d(TAG, "markAllNotificationsAsRead() called")
        try {
            db.await().cachedNotifications().markAllNotificationsAsRead()
            Log.d(TAG, "markAllNotificationsAsRead: sending event")
            _markedNotificationsAsRead.send(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "markAllNotificationsAsRead: failed to mark notifications as read", e)
        }
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
                        week.getFirstDayInstant(),
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
        val RECENTLY_CACHED_INTERVAL: Duration = Duration.ofHours(1)
    }
}