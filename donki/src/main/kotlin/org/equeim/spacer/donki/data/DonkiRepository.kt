// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data

import android.content.Context
import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import org.equeim.spacer.donki.data.cache.DonkiDataSourceCache
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.network.DonkiDataSourceNetwork
import org.equeim.spacer.donki.data.paging.EventsSummariesPagingSource
import org.equeim.spacer.donki.data.paging.EventsSummariesRemoteMediator
import java.io.Closeable
import java.time.Clock
import java.time.Instant

private const val TAG = "DonkiRepository"

interface DonkiRepository : Closeable {
    class PagerFiltersTypesAreEmpty : RuntimeException()

    fun getEventSummariesPager(filters: StateFlow<EventFilters>): Pager<*, EventSummary>

    suspend fun getEventById(id: EventId, forceRefresh: Boolean): EventById

    data class EventById(
        val event: Event,
        val needsRefreshing: Boolean
    )

    data class EventFilters(
        val types: Set<EventType> = EventType.All.toSet()
    )
}

fun DonkiRepository(context: Context): DonkiRepository = DonkiRepositoryImpl(context)

internal interface DonkiRepositoryInternal : DonkiRepository {
    suspend fun getEventSummariesForWeek(
        week: Week,
        eventTypes: List<EventType>,
        refreshCacheIfNeeded: Boolean
    ): List<EventSummary>

    suspend fun updateEventsForWeek(
        week: Week,
        eventType: EventType
    ): List<Pair<Event, JsonObject>>
}

private class DonkiRepositoryImpl(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) : DonkiRepositoryInternal {
    private val networkDataSource = DonkiDataSourceNetwork()
    private val cacheDataSource = DonkiDataSourceCache(context)

    override fun close() {
        cacheDataSource.close()
    }

    override suspend fun getEventSummariesForWeek(
        week: Week,
        eventTypes: List<EventType>,
        refreshCacheIfNeeded: Boolean
    ): List<EventSummary> {
        Log.d(
            TAG,
            "getEventSummariesForWeek() called with: week = $week, eventTypes = $eventTypes, refreshCacheIfNeeded = $refreshCacheIfNeeded"
        )
        val allEvents = mutableListOf<EventSummary>()
        val mutex = Mutex()
        coroutineScope {
            for (eventType in eventTypes) {
                launch {
                    val cachedEvents =
                        cacheDataSource.getEventSummariesForWeek(week, eventType, returnCacheThatNeedsRefreshing = !refreshCacheIfNeeded)
                    if (cachedEvents != null) {
                        mutex.withLock { allEvents.addAll(cachedEvents) }
                    } else {
                        val weekLoadTime = Instant.now(clock)
                        val events = networkDataSource.getEvents(week, eventType)
                        cacheDataSource.cacheWeekAsync(week, eventType, events, weekLoadTime)
                        val summaries = events.map { it.first.toEventSummary() }
                        mutex.withLock { allEvents.addAll(summaries) }
                    }
                }
            }
        }
        allEvents.sortByDescending { it.time }
        return allEvents
    }

    override suspend fun updateEventsForWeek(week: Week, eventType: EventType): List<Pair<Event, JsonObject>> {
        Log.d(TAG, "updateEventsForWeek() called with: week = $week, eventType = $eventType")
        val loadTime = Instant.now(clock)
        val events = networkDataSource.getEvents(week, eventType)
        cacheDataSource.cacheWeek(week, eventType, events, loadTime)
        return events
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getEventSummariesPager(filters: StateFlow<DonkiRepository.EventFilters>): Pager<*, EventSummary> {
        val mediator = EventsSummariesRemoteMediator(this, cacheDataSource)
        return Pager(
            PagingConfig(pageSize = 20, enablePlaceholders = false),
            null,
            mediator
        ) {
            EventsSummariesPagingSource(
                this,
                merge(mediator.refreshed, cacheDataSource.databaseRecreated, filters.drop(1)),
                filters.value
            )
        }
    }

    override suspend fun getEventById(
        id: EventId,
        forceRefresh: Boolean
    ): DonkiRepository.EventById {
        Log.d(TAG, "getEventById() called with: id = $id, forceRefresh = $forceRefresh")
        return try {
            val (eventType, time) = id.parse()
            val week = Week.fromInstant(time)
            if (!forceRefresh) {
                cacheDataSource.getEventById(id, eventType, week)?.let { return it }
            }
            val event =
                updateEventsForWeek(week, eventType).find { it.first.id == id } ?: throw RuntimeException(
                    "Did not find event $id in server response"
                )
            DonkiRepository.EventById(event.first, needsRefreshing = false)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "getEventDetailsById: failed to get event $id", e)
            }
            throw e
        }
    }
}
