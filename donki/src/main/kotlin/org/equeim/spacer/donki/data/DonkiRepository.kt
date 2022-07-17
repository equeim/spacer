package org.equeim.spacer.donki.data

import android.content.Context
import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    fun getEventSummariesPager(): Pager<*, EventSummary>
    suspend fun getEventById(id: EventId): Event
}

fun DonkiRepository(context: Context): DonkiRepository = DonkiRepositoryImpl(context)

internal interface DonkiRepositoryInternal : DonkiRepository {
    suspend fun getEventSummariesForWeek(
        week: Week,
        eventTypes: List<EventType>,
        allowOutOfDateCache: Boolean
    ): List<EventSummary>

    suspend fun updateEventsForWeek(
        week: Week,
        eventType: EventType
    )
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
        allowOutOfDateCache: Boolean
    ): List<EventSummary> {
        val allEvents = mutableListOf<EventSummary>()
        val mutex = Mutex()
        coroutineScope {
            for (eventType in eventTypes) {
                launch {
                    val cachedEvents =
                        cacheDataSource.getEventSummariesForWeek(week, eventType, allowOutOfDateCache)
                    if (cachedEvents != null) {
                        mutex.withLock { allEvents.addAll(cachedEvents) }
                    } else {
                        val weekLoadTime = Instant.now(clock)
                        val events = networkDataSource.getEvents(week, eventType)
                        cacheDataSource.cacheWeekAsync(week, eventType, events, weekLoadTime)
                        val summaries = events.map { it.toEventSummary() }
                        mutex.withLock { allEvents.addAll(summaries) }
                    }
                }
            }
        }
        allEvents.sortByDescending { it.time }
        return allEvents
    }

    override suspend fun updateEventsForWeek(week: Week, eventType: EventType) {
        val loadTime = Instant.now(clock)
        val events = networkDataSource.getEvents(week, eventType)
        cacheDataSource.cacheWeek(week, eventType, events, loadTime)
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getEventSummariesPager(): Pager<*, EventSummary> {
        val mediator = EventsSummariesRemoteMediator(this, cacheDataSource)
        return Pager(
            PagingConfig(pageSize = 20, enablePlaceholders = false),
            null,
            mediator
        ) {
            EventsSummariesPagingSource(
                this,
                mediator.invalidationEvents
            )
        }
    }

    override suspend fun getEventById(id: EventId): Event = try {
        networkDataSource.getEventById(id)
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Log.e(TAG, "getEventDetailsById: failed to get event details", e)
        }
        throw e
    }
}
