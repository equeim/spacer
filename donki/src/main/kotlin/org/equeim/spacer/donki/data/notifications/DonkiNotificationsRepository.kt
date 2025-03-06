// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.BasePagingSource
import org.equeim.spacer.donki.data.common.DONKI_BASE_URL
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.DonkiCacheDataSourceException
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException
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
    private val context: Context,
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

    private val backgroundUpdateInProgress = MutableStateFlow(false)
    private val backgroundUpdateCompleted: Flow<Unit> get() = backgroundUpdateInProgress
        .dropWhile { inProgress -> !inProgress }
        .transform { inProgress -> if (!inProgress) emit(Unit) }

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
                backgroundUpdateCompleted,
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

    /**
     * @throws DonkiCacheDataSourceException on database error
     * @throws RuntimeException if the notification does not exist in database
     */
    suspend fun getCachedNotificationByIdAndMarkAsRead(id: NotificationId): CachedNotification {
        Log.d(TAG, "getNotificationById() called with: id = $id")
        return cacheDataSource.getCachedNotificationByIdAndMarkAsRead(id)
            ?: throw RuntimeException("getCachedNotificationById: notification $id does not exist in the database")
    }

    /**
     * Returned flow catches exceptions
     */
    fun getNeedToRefreshState(filters: Filters): Flow<NeedToRefreshState> {
        Log.d(TAG, "getNeedToRefreshState() called with: filters = $filters")
        return if (filters.types.isEmpty()) {
            flowOf(NeedToRefreshState.DontNeedToRefresh)
        } else {
            cacheDataSource.getWeeksThatNeedRefresh(
                filters.dateRange
            ).catch {
                Log.e(TAG, "getWeeksThatNeedRefresh failed", it)
                emit(emptyList())
            }.map { weeks ->
                when {
                    weeks.isEmpty() -> NeedToRefreshState.DontNeedToRefresh
                    weeks.all { it.cachedRecently } -> NeedToRefreshState.HaveWeeksThatNeedRefreshButAllCachedRecently
                    else -> NeedToRefreshState.HaveWeeksThatNeedRefreshNow
                }
            }
        }.onEach {
            Log.d(TAG, "getNeedToRefreshState: emitting $it")
        }
    }

    /**
     * @throws DonkiNetworkDataSourceException on network error
     * @throws DonkiCacheDataSourceException on database error
     */
    internal suspend fun updateNotificationsForWeek(week: Week) {
        Log.d(TAG, "updateNotificationsForWeek() called with: week = $week")
        waitUntilBackgroundUpdateIsCompleted()
        updateNotificationsForWeekImpl(week, cacheAsync = false)
    }

    /**
     * @throws DonkiNetworkDataSourceException on network error
     * @throws DonkiCacheDataSourceException on database error
     */
    internal suspend fun getNotificationSummariesForWeek(
        week: Week,
        types: List<NotificationType>,
        dateRange: DateRange?,
    ): List<CachedNotificationSummary> {
        Log.d(
            TAG,
            "getNotificationsForWeek() called with: week = $week, types = $types, dateRange = $dateRange"
        )

        waitUntilBackgroundUpdateIsCompleted()

        val cachedNotifications = cacheDataSource.getNotificationSummariesForWeek(
            week = week,
            types = types,
            dateRange = dateRange
        )
        if (cachedNotifications != null) {
            return cachedNotifications
        }
        val (notificationSummaries, _) = updateNotificationsForWeekImpl(week, cacheAsync = true)
        if (types == NotificationType.entries && dateRange == null) {
            return notificationSummaries
        }
        var filtered = notificationSummaries.asSequence()
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

    internal suspend fun waitUntilBackgroundUpdateIsCompleted() {
        if (backgroundUpdateInProgress.value) {
            Log.d(TAG, "waitUntilBackgroundUpdateIsCompleted: waiting until background update is completed")
            backgroundUpdateInProgress.first { inProgress -> !inProgress }
        }
    }

    data class SucessfulBackgroundUpdateResult(val newUnreadNotifications: List<CachedNotification>)

    /**
     * Returned job throws [DonkiCacheDataSourceException] and [DonkiNetworkDataSourceException]
     */
    suspend fun performBackgroundUpdate(includingCachedRecently: Boolean): SucessfulBackgroundUpdateResult? {
        Log.d(
            TAG,
            "performBackgroundUpdate() called with: includingCachedRecently = $includingCachedRecently"
        )
        if (!backgroundUpdateInProgress.compareAndSet(expect = false, update = true)) {
            Log.w(TAG, "performBackgroundUpdate: already updating, return null")
            return null
        }
        return try {
            performBackgroundUpdateImpl(includingCachedRecently)
        } finally {
            backgroundUpdateInProgress.value = false
        }
    }

    private suspend fun performBackgroundUpdateImpl(includingCachedRecently: Boolean): SucessfulBackgroundUpdateResult {
        val weeksThatNeedRefresh =
            cacheDataSource.getWeeksThatNeedRefresh(dateRange = null).first()
        val newUnreadNotifications = coroutineScope {
            weeksThatNeedRefresh
                .run {
                    if (includingCachedRecently) this else filter { !it.cachedRecently }
                }
                .map { (week, _) ->
                    async { updateNotificationsForWeekImpl(week, cacheAsync = false) }
                }
                .awaitAll()
                .flatMap { it.newUnreadNotifications }
        }
        return SucessfulBackgroundUpdateResult(newUnreadNotifications)
    }

    private data class UpdateWeekResult(
        val notificationSummaries: List<CachedNotificationSummary>,
        val newUnreadNotifications: List<CachedNotification>
    )

    private suspend fun updateNotificationsForWeekImpl(
        week: Week,
        cacheAsync: Boolean
    ): UpdateWeekResult {
        val weekLoadTime = Instant.now(clock)
        val notificationJsons = networkDataSource.getNotifications(week)
        val cachedNotifications = cacheDataSource.getNotificationSummariesForWeek(
            week = week,
            types = NotificationType.entries,
            dateRange = null
        ).orEmpty()

        val newNotificationJsons = notificationJsons.asSequence()
            .filter { notification -> cachedNotifications.find { it.id == notification.id } == null }
            .toList()

        val newNotifications = if (newNotificationJsons.isNotEmpty()) {
            val latestCachedWeekTimeAtStartOfFirstDay =
                cacheDataSource.getLatestCachedWeekTimeAtStartOfFirstDay()
            val shouldMarkAsUnread: (Instant) -> Boolean = when {
                latestCachedWeekTimeAtStartOfFirstDay == null -> { notificationTime ->
                    Duration.between(notificationTime, weekLoadTime) <= INITIAL_UNREAD_THRESHOLD
                }

                week.getFirstDayInstant() >= latestCachedWeekTimeAtStartOfFirstDay -> { _ -> true }
                else -> { _ -> false }
            }
            newNotificationJsons.map { notification ->
                CachedNotification(
                    id = notification.id,
                    type = notification.type,
                    time = notification.time,
                    title = notification.body.findTitle(),
                    subtitle = notification.body.findSubtitle(),
                    body = notification.body.trim(),
                    link = notification.link,
                    read = !shouldMarkAsUnread(notification.time)
                )
            }
        } else {
            emptyList()
        }

        if (cacheAsync) {
            coroutineScope.launch {
                cacheDataSource.cacheWeek(
                    week,
                    newNotifications,
                    weekLoadTime
                )
            }
        } else {
            cacheDataSource.cacheWeek(week, newNotifications, weekLoadTime)
        }

        val notificationSummaries = buildList(cachedNotifications.size + newNotifications.size) {
            addAll(cachedNotifications)
            addAll(
                newNotifications.asSequence().map {
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
        val newUnreadNotifications = newNotifications.filter { !it.read }
        return UpdateWeekResult(notificationSummaries, newUnreadNotifications)
    }

    /**
     * Returned flow catches exceptions
     */
    fun getNumberOfUnreadNotifications(): Flow<Int> =
        cacheDataSource.getNumberOfUnreadNotifications()

    /**
     * Catches exceptions
     */
    suspend fun markAllNotificationsAsRead() {
        Log.d(TAG, "markAllNotificationsAsRead() called")
        cacheDataSource.markAllNotificationsAsRead()
    }

    private companion object {
        const val TAG = "DonkiNotificationsRepository"

        // If db is empty and notification is less than 12 hours old then mark it as unread
        val INITIAL_UNREAD_THRESHOLD: Duration = Duration.ofHours(12)
    }
}
