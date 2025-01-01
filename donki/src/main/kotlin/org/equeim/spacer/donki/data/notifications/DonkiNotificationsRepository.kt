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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.BasePagingSource
import org.equeim.spacer.donki.data.common.DONKI_BASE_URL
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.NeedToRefreshState
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
    okHttpClient: OkHttpClient,
    baseUrl: HttpUrl,
    context: Context,
    db: NotificationsDatabase?,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val clock: Clock,
) : Closeable {
    constructor(
        customNasaApiKey: Flow<String?>,
        okHttpClient: OkHttpClient,
        context: Context
    ) : this(
        customNasaApiKey = customNasaApiKey,
        okHttpClient = okHttpClient,
        baseUrl = DONKI_BASE_URL,
        context = context,
        db = null,
        coroutineDispatchers = CoroutineDispatchers(),
        clock = Clock.systemDefaultZone()
    )

    private val networkDataSource = NotificationsDataSourceNetwork(
        customNasaApiKey = customNasaApiKey,
        okHttpClient = okHttpClient,
        baseUrl = baseUrl
    )
    private val cacheDataSource =
        NotificationsDataSourceCache(context, db, coroutineDispatchers, clock)

    private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatchers.Default)

    override fun close() {
        coroutineScope.cancel()
        cacheDataSource.close()
    }

    @Immutable
    data class Filters(
        val types: List<NotificationType>,
        val dateRange: DateRange?,
    )

    @OptIn(ExperimentalPagingApi::class)
    fun getNotificationSummariesPager(filters: StateFlow<Filters>): Pair<Pager<*, CachedNotificationSummary>, Closeable> {
        val mediator = createRemoteMediator(filters)
        val pagingSourceFactory = BasePagingSource.Factory(
            invalidationEvents = merge(
                mediator.refreshed,
                filters.drop(1),
                cacheDataSource.markedNotificationsAsRead.receiveAsFlow(),
            ),
            coroutineDispatchers = coroutineDispatchers,
            createPagingSource = { createPagingSource(filters.value) }
        )
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            initialKey = null,
            remoteMediator = mediator,
            pagingSourceFactory = pagingSourceFactory
        ) to pagingSourceFactory
    }

    @VisibleForTesting
    internal fun createRemoteMediator(filters: StateFlow<Filters>): NotificationsRemoteMediator =
        NotificationsRemoteMediator(this, cacheDataSource, filters)

    @VisibleForTesting
    internal fun createPagingSource(
        filters: Filters
    ): NotificationSummariesPagingSource =
        NotificationSummariesPagingSource(
            repository = this,
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

    fun getNeedToRefreshState(filters: Filters): Flow<NeedToRefreshState> {
        Log.d(TAG, "getNeedToRefreshState() called with: filters = $filters")
        return if (filters.types.isEmpty()) {
            flowOf(NeedToRefreshState.DontNeedToRefresh)
        } else {
            cacheDataSource.getWeeksThatNeedRefresh(
                filters.dateRange
            ).map { weeks ->
                when {
                    weeks.isEmpty() -> NeedToRefreshState.DontNeedToRefresh
                    weeks.all { it.cachedRecently } -> NeedToRefreshState.HaveWeeksThatNeedRefreshButAllCachedRecently
                    else -> NeedToRefreshState.HaveWeeksThatNeedRefreshNow
                }
            }.catch {
                Log.e(TAG, "haveWeeksThatNeedRefresh: NotificationsDataSourceCache error", it)
                emit(NeedToRefreshState.DontNeedToRefresh)
            }
        }.onEach {
            Log.d(TAG, "getNeedToRefreshState: emitting $it")
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
    ): List<CachedNotificationSummary> {
        Log.d(
            TAG,
            "getNotificationsForWeek() called with: week = $week, types = $types, dateRange = $dateRange"
        )

        val cachedNotifications = cacheDataSource.getNotificationSummariesForWeek(
            week = week,
            types = types,
            dateRange = dateRange
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
            dateRange = null
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
            coroutineScope.launch {
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

    fun getNumberOfUnreadNotifications(): Flow<Int> =
        cacheDataSource.getNumberOfUnreadNotifications()

    private companion object {
        const val TAG = "DonkiNotificationsRepository"

        // If notification is doesn't exist in db and is less than 12 hours old then mark it as unread
        val UNREAD_THRESHOLD: Duration = Duration.ofHours(12)
    }
}
