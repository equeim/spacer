// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
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
import org.equeim.spacer.donki.data.events.cache.EventsCacheDatabase
import org.equeim.spacer.donki.data.events.cache.EventsDataSourceCache
import org.equeim.spacer.donki.data.events.network.EventsDataSourceNetwork
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import java.io.Closeable
import java.time.Clock
import java.time.Instant

private const val TAG = "DonkiEventsRepository"

class DonkiEventsRepository internal constructor(
    customNasaApiKey: Flow<String?>,
    okHttpClient: OkHttpClient,
    baseUrl: HttpUrl,
    context: Context,
    db: EventsCacheDatabase?,
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

    private val networkDataSource = EventsDataSourceNetwork(customNasaApiKey, okHttpClient, baseUrl)
    private val cacheDataSource = EventsDataSourceCache(context, db, coroutineDispatchers, clock)
    private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatchers.Default)

    override fun close() {
        coroutineScope.cancel()
        cacheDataSource.close()
    }

    /**
     * Returned flow catches exceptions
     */
    fun getNeedToRefreshState(filters: Filters): Flow<NeedToRefreshState> {
        Log.d(TAG, "getNeedToRefreshState() called with: filters = $filters")
        return cacheDataSource.getWeeksThatNeedRefresh(filters.types, filters.dateRange)
            .catch {
                Log.e(TAG, "getWeeksThatNeedRefresh failed", it)
                emit(emptyList())
            }
            .map { weeks ->
                when {
                    weeks.isEmpty() -> NeedToRefreshState.DontNeedToRefresh
                    weeks.all { it.cachedRecently } -> NeedToRefreshState.HaveWeeksThatNeedRefreshButAllCachedRecently
                    else -> NeedToRefreshState.HaveWeeksThatNeedRefreshNow
                }
            }.onEach {
                Log.d(TAG, "getNeedToRefreshState: emitting $it")
            }
    }

    /**
     * @throws DonkiNetworkDataSourceException on network error
     * @throws DonkiCacheDataSourceException on database error
     */
    internal suspend fun getEventSummariesForWeek(
        week: Week,
        eventTypes: List<EventType>,
        dateRange: DateRange?,
    ): List<EventSummary> {
        Log.d(
            TAG,
            "getEventSummariesForWeek() called with: week = $week, eventTypes = $eventTypes, timeRange = $dateRange"
        )
        val allEvents = ArrayList<EventSummary>()
        val mutex = Mutex()
        coroutineScope {
            for (eventType in eventTypes) {
                launch {
                    val cachedEvents =
                        cacheDataSource.getEventSummariesForWeek(
                            week,
                            eventType,
                            dateRange,
                        )
                    if (cachedEvents != null) {
                        mutex.withLock { allEvents.addAll(cachedEvents) }
                    } else {
                        val weekLoadTime = Instant.now(clock)
                        val events = networkDataSource.getEvents(week, eventType)
                        coroutineScope.launch {
                            cacheDataSource.cacheWeek(
                                week,
                                eventType,
                                events,
                                weekLoadTime
                            )
                        }
                        val summaries = if (dateRange == null) {
                            events.asSequence()
                        } else {
                            events.asSequence()
                                .dropWhile { (event, _) -> event.time < dateRange.firstDayInstant }
                                .takeWhile { (event, _) -> event.time < dateRange.instantAfterLastDay }
                        }.map { it.first.toEventSummary() }
                        mutex.withLock {
                            allEvents.ensureCapacity(allEvents.size + events.size)
                            allEvents.addAll(summaries)
                        }
                    }
                }
            }
        }
        allEvents.sortByDescending { it.time }
        return allEvents
    }

    /**
     * @throws DonkiNetworkDataSourceException on network error
     * @throws DonkiCacheDataSourceException on database error
     */
    internal suspend fun updateEventsForWeek(
        week: Week,
        eventType: EventType
    ): List<Pair<Event, JsonObject>> {
        Log.d(TAG, "updateEventsForWeek() called with: week = $week, eventType = $eventType")
        val loadTime = Instant.now(clock)
        val events = networkDataSource.getEvents(week, eventType)
        cacheDataSource.cacheWeek(week, eventType, events, loadTime)
        return events
    }

    @OptIn(ExperimentalPagingApi::class)
    fun getEventSummariesPager(filters: StateFlow<Filters>): Pair<Pager<*, EventSummary>, Closeable> {
        val mediator = createRemoteMediator(filters)
        val pagingSourceFactory = BasePagingSource.Factory(
            invalidationEvents = merge(
                mediator.refreshed,
                cacheDataSource.databaseRecreated,
                filters.drop(1)
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

    internal fun createRemoteMediator(filters: StateFlow<Filters>): EventsSummariesRemoteMediator =
        EventsSummariesRemoteMediator(this, cacheDataSource, filters)

    internal fun createPagingSource(filters: Filters): EventsSummariesPagingSource =
        EventsSummariesPagingSource(
            repository = this,
            filters = filters,
            coroutineDispatchers = coroutineDispatchers,
            clock = clock
        )

    /**
     * @throws DonkiNetworkDataSourceException on network error
     * @throws DonkiCacheDataSourceException on database error
     * @throws RuntimeException if server did not return the event
     */
    suspend fun getEventById(
        id: EventId,
        forceRefresh: Boolean,
    ): EventById {
        Log.d(TAG, "getEventById() called with: id = $id, forceRefresh = $forceRefresh")
        return try {
            val (eventType, time) = id.parse()
            val week = Week.fromInstant(time)
            if (!forceRefresh) {
                cacheDataSource.getEventById(id, eventType, week)?.let { return it }
            }
            val event =
                updateEventsForWeek(week, eventType).find { it.first.id == id }
                    ?: throw RuntimeException(
                        "Did not find event $id in server response"
                    )
            EventById(event.first, needsRefreshingNow = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {

            throw e
        }
    }

    /**
     * Catches exceptions
     */
    suspend fun isEventNeedsRefreshingNow(event: Event): Boolean {
        Log.d(TAG, "isEventNeedsRefreshingNow() called with: event = $event")
        return cacheDataSource.isWeekNotCachedOrNeedsRefreshingNow(
            Week.fromInstant(event.time),
            event.type
        ).also {
            Log.d(TAG, "isEventNeedsRefreshingNow() returned: $it")
        }
    }

    data class EventById(
        val event: Event,
        val needsRefreshingNow: Boolean,
    )

    @Immutable
    data class Filters(
        val types: List<EventType>,
        val dateRange: DateRange?,
    )
}
