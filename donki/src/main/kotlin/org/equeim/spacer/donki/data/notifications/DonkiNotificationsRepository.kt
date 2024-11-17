// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.DONKI_BASE_URL
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.cache.CachedNotification
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDataSourceCache
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDatabase
import org.equeim.spacer.donki.data.notifications.network.NotificationsDataSourceNetwork
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Instant

class DonkiNotificationsRepository internal constructor(
    customNasaApiKey: Flow<String?>,
    context: Context,
    baseUrl: HttpUrl,
    db: NotificationsDatabase?,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val clock: Clock,
) : Closeable {
    constructor(customNasaApiKey: Flow<String?>, context: Context) : this(
        customNasaApiKey = customNasaApiKey,
        context = context,
        baseUrl = DONKI_BASE_URL,
        db = null,
        coroutineDispatchers = CoroutineDispatchers(),
        clock = Clock.systemDefaultZone()
    )

    private val networkDataSource = NotificationsDataSourceNetwork(
        customNasaApiKey = customNasaApiKey,
        baseUrl = baseUrl
    )
    private val cacheDataSource = NotificationsDataSourceCache(context, db, coroutineDispatchers, clock)

    override fun close() {
        cacheDataSource.close()
    }

    @Immutable
    data class Filters(
        val types: List<NotificationType>,
        val dateRange: DateRange?,
    )

    @OptIn(ExperimentalPagingApi::class)
    fun getNotificationSummariesPager(filters: StateFlow<Filters>): Pager<*, CachedNotificationSummary> {
        val mediator = createRemoteMediator(filters)
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            initialKey = null,
            remoteMediator = mediator
        ) {
            createPagingSource(filters.value, merge(mediator.refreshed, filters.drop(1)))
        }
    }

    @VisibleForTesting
    internal fun createRemoteMediator(filters: StateFlow<Filters>): NotificationsRemoteMediator =
        NotificationsRemoteMediator(this, cacheDataSource, filters, clock)

    @VisibleForTesting
    internal fun createPagingSource(filters: Filters, invalidationEvents: Flow<Any>): NotificationSummariesPagingSource =
        NotificationSummariesPagingSource(
            repository = this,
            invalidationEvents = invalidationEvents,
            filters = filters,
            coroutineDispatchers = coroutineDispatchers,
            clock = clock
        )

    suspend fun getCachedNotificationByIdAndMarkAsRead(id: NotificationId): CachedNotification {
        Log.d(TAG, "getNotificationById() called with: id = $id")
        return try {
            cacheDataSource.getCachedNotificationByIdAndMarkAsRead(id)
                ?: throw RuntimeException("Notification $id does not exist in the database")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getCachedNotificationById: failed to get notification $id", e)
            throw e
        }
    }

    suspend fun isLastWeekNeedsRefreshing(filters: Filters): Boolean {
        Log.d(TAG, "isLastWeekNeedsRefreshing() called with: filters = $filters")
        val week = filters.dateRange?.lastWeek ?: Week.getCurrentWeek(clock)
        return try {
            cacheDataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = false)
                .also {
                    Log.d(TAG, "isLastWeekNeedsRefreshing() returned: $it")
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "isLastWeekNeedsRefreshing: NotificationsDataSourceCache error", e)
            false
        }
    }

    internal suspend fun updateNotificationsForWeek(week: Week) {
        Log.d(TAG, "updateNotificationsForWeek() called with: week = $week")
        updateNotificationsForWeekImpl(week, cacheAsync = false)
    }

    internal suspend fun getNotificationSummariesForWeek(
        week: Week,
        types: List<NotificationType>,
        dateRange: DateRange?,
        refreshCacheIfNeeded: Boolean,
    ): List<CachedNotificationSummary> {
        Log.d(
            TAG,
            "getNotificationsForWeek() called with: week = $week, types = $types, dateRange = $dateRange"
        )

        val cachedNotifications = cacheDataSource.getNotificationSummariesForWeek(
            week = week,
            types = types,
            dateRange = dateRange,
            returnCacheThatNeedsRefreshing = !refreshCacheIfNeeded
        )
        if (cachedNotifications != null) {
            return cachedNotifications
        }
        val notifications = updateNotificationsForWeekImpl(week, cacheAsync = true)
        if (types == NotificationType.entries && dateRange == null) {
            return notifications
        }
        var filtered = notifications.asSequence()
        if (dateRange != null) {
            filtered = filtered
                .dropWhile { it.time >= dateRange.instantAfterLastDay }
                .takeWhile { it.time >= dateRange.firstDayInstant }

        }
        if (types != NotificationType.entries) {
            filtered = filtered.filter { types.contains(it.type) }
        }
        return filtered.toList()
    }

    private suspend fun updateNotificationsForWeekImpl(
        week: Week,
        cacheAsync: Boolean
    ): List<CachedNotificationSummary> {
        val weekLoadTime = Instant.now(clock)
        val notificationJsons = networkDataSource.getNotifications(week)
        val cachedNotifications = cacheDataSource.getNotificationSummariesForWeek(
            week = week,
            types = NotificationType.entries,
            dateRange = null,
            returnCacheThatNeedsRefreshing = true
        ).orEmpty()
        val notificationsToCache = ArrayList<CachedNotification>(
            (notificationJsons.size - cachedNotifications.size).coerceAtLeast(0)
        )

        for (notification in notificationJsons) {
            if (cachedNotifications.find { it.id == notification.id } == null) {
                notificationsToCache.add(
                    CachedNotification(
                        id = notification.id,
                        type = notification.type,
                        time = notification.time,
                        title = notification.body.findTitle(),
                        subtitle = notification.body.findSubtitle(),
                        body = notification.body.trim(),
                        link = notification.link,
                        read = Duration.between(notification.time, weekLoadTime) > UNREAD_THRESHOLD
                    )
                )
            }
        }

        if (cacheAsync) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(coroutineDispatchers.Default) {
                cacheDataSource.cacheWeek(
                    week,
                    notificationsToCache,
                    weekLoadTime
                )
            }
        } else {
            cacheDataSource.cacheWeek(week, notificationsToCache, weekLoadTime)
        }

        return buildList(cachedNotifications.size + notificationsToCache.size) {
            addAll(cachedNotifications)
            addAll(
                notificationsToCache.asSequence().map {
                    CachedNotificationSummary(
                        id = it.id,
                        type = it.type,
                        time = it.time,
                        title = it.title,
                        subtitle = it.subtitle,
                        read = it.read
                    )
                })
            sortByDescending { it.time }
        }
    }

    private companion object {
        const val TAG = "DonkiNotificationsRepository"

        // If notification is doesn't exist in db and is less than 12 hours old then mark it as unread
        val UNREAD_THRESHOLD: Duration = Duration.ofHours(12)
    }
}
