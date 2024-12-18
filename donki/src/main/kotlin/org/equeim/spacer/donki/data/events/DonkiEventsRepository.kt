// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
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
    constructor(customNasaApiKey: Flow<String?>, okHttpClient: OkHttpClient, context: Context) : this(
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

    internal suspend fun getEventSummariesForWeek(
        week: Week,
        eventTypes: List<EventType>,
        dateRange: DateRange?,
        refreshCacheIfNeeded: Boolean,
    ): List<EventSummary> {
        Log.d(
            TAG,
            "getEventSummariesForWeek() called with: week = $week, eventTypes = $eventTypes, timeRange = $dateRange, refreshCacheIfNeeded = $refreshCacheIfNeeded"
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
                            returnCacheThatNeedsRefreshing = !refreshCacheIfNeeded
                        )
                    if (cachedEvents != null) {
                        mutex.withLock { allEvents.addAll(cachedEvents) }
                    } else {
                        val weekLoadTime = Instant.now(clock)
                        val events = networkDataSource.getEvents(week, eventType)
                        coroutineScope.launch { cacheDataSource.cacheWeek(week, eventType, events, weekLoadTime) }
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
            invalidationEvents = merge(mediator.refreshed, cacheDataSource.databaseRecreated, filters.drop(1)),
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
        EventsSummariesRemoteMediator(this, filters, cacheDataSource::isWeekCachedAndNeedsRefresh, clock)

    internal fun createPagingSource(filters: Filters): EventsSummariesPagingSource =
        EventsSummariesPagingSource(
            repository = this,
            filters = filters,
            coroutineDispatchers = coroutineDispatchers,
            clock = clock
        )

    suspend fun isLastWeekNeedsRefreshing(filters: Filters): Boolean {
        Log.d(TAG, "isLastWeekNeedsRefreshing() called with: filters = $filters")
        val week = filters.dateRange?.lastWeek ?: Week.getCurrentWeek(clock)
        return try {
            filters.types.any {
                cacheDataSource.isWeekNotCachedOrNeedsRefresh(week, it)
            }.also {
                Log.d(TAG, "isLastWeekNeedsRefreshing() returned: $it")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "isLastWeekNeedsRefreshing: EventsDataSourceCache error", e)
            false
        }
    }

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
            EventById(event.first, needsRefreshing = false)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "getEventDetailsById: failed to get event $id", e)
            }
            throw e
        }
    }

    suspend fun isEventNeedsRefreshing(event: Event): Boolean {
        Log.d(TAG, "isEventNeedsRefreshing() called with: event = $event")
        return try {
            cacheDataSource.isWeekNotCachedOrNeedsRefresh(Week.fromInstant(event.time), event.type)
                .also {
                    Log.d(TAG, "isEventNeedsRefreshing() returned: $it")
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "isEventNeedsRefreshing: DonkiDataSourceCache error", e)
            false
        }
    }

    data class EventById(
        val event: Event,
        val needsRefreshing: Boolean,
    )

    @Immutable
    data class Filters(
        val types: List<EventType>,
        val dateRange: DateRange?,
    )
}
