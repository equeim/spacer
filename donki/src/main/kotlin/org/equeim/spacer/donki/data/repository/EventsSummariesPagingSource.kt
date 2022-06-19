package org.equeim.spacer.donki.data.repository

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.network.DonkiDataSourceNetwork
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private const val TAG = "EventsSummariesPagingSource"

internal class EventsSummariesPagingSource(
    private val dataSource: DonkiDataSourceNetwork,
    private val coroutineDispatchers: CoroutineDispatchers = CoroutineDispatchers(),
    private val clock: Clock = Clock.systemDefaultZone()
) : PagingSource<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>() {
    init {
        Log.d(TAG, "EventsSummariesPagingSource() called")
        registerInvalidatedCallback { Log.d(TAG, "EventsSummariesPagingSource invalidated") }
    }

    override fun getRefreshKey(state: PagingState<EventsSummariesDateRange, EventSummary>): EventsSummariesDateRange? {
        Log.d(TAG, "getRefreshKey() called with: state = $state")
        val anchorPosition = state.anchorPosition ?: return null
        Log.d(TAG, "getRefreshKey: anchorPosition = $anchorPosition")
        val anchorItem = state.closestItemToPosition(anchorPosition) ?: return null
        val anchorDate = anchorItem.time.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
        val startDate = anchorDate.minusDays(PAGE_SIZE_IN_DAYS / 2)
        val endDate = anchorDate.plusDays(PAGE_SIZE_IN_DAYS / 2)
        return EventsSummariesDateRange(
            startDate,
            endDate,
            endDateWasCurrentDayAtCreationTime = false
        )
            .coerceToCurrentDay(getCurrentDay(), shiftOnlyEndDate = false)
            .also { Log.d(TAG, "getRefreshKey: returned range = $it") }
    }

    override suspend fun load(params: LoadParams<EventsSummariesDateRange>): LoadResult<EventsSummariesDateRange, EventSummary> {
        Log.d(TAG, "load() called with: params = $params")
        return withContext(coroutineDispatchers.Default) {
            val currentDay = getCurrentDay()
            Log.d(TAG, "load: current day is $currentDay")
            val range = params.key?.let { requestedKey ->
                val comparison = requestedKey.endDate.compareTo(currentDay)
                when {
                    comparison > 0 -> {
                        Log.e(TAG, "load: requested range $requestedKey is in future")
                        Log.d(TAG, "load: returning LoadResult.Invalid")
                        return@withContext LoadResult.Invalid()
                    }
                    comparison == 0 -> requestedKey.copy(endDateWasCurrentDayAtCreationTime = true)
                    else -> requestedKey
                }
            } ?: getInitialRange(currentDay)
            Log.d(TAG, "load: range = $range")
            try {
                val startDate = range.startDate.toInstant()
                val endDate = range.endDate.toInstant()
                val allEvents = mutableListOf<EventSummary>()
                val mutex = Mutex()
                coroutineScope {
                    for (eventType in EventType.values()) {
                        launch {
                            val events = dataSource.getEvents(eventType, startDate, endDate)
                                .map { it.toEventSummary() }
                            mutex.withLock { allEvents.addAll(events) }
                        }
                    }
                }
                allEvents.sortByDescending { it.time }
                if (lastLoadReturnedEmptyPage && allEvents.isEmpty()) {
                    Log.d(TAG, "load: previous load returned empty page and this one is empty too, wait 1 second before returning")
                    delay(EMPTY_PAGE_THROTTLE_DELAY_MS)
                }
                lastLoadReturnedEmptyPage = allEvents.isEmpty()
                Log.d(TAG, "load: returning LoadResult.Page")
                LoadResult.Page(allEvents, range.prev(currentDay), range.next())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "load: failed to get events summaries", e)
                Log.d(TAG, "load: returning LoadResult.Error")
                LoadResult.Error(e)
            }
        }
    }

    private fun getCurrentDay(): OffsetDateTime =
        Instant.now(clock).atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)

    private fun getInitialRange(currentDay: OffsetDateTime): EventsSummariesDateRange {
        val startDate = currentDay.minusDays(PAGE_SIZE_IN_DAYS)
        return EventsSummariesDateRange(
            startDate,
            currentDay,
            endDateWasCurrentDayAtCreationTime = true
        )
    }

    // Back to the future
    private fun EventsSummariesDateRange.prev(currentDay: OffsetDateTime): EventsSummariesDateRange? {
        return if (endDateWasCurrentDayAtCreationTime) {
            null
        } else {
            val startDate = endDate.plusDays(1)
            val endDate = startDate.plusDays(PAGE_SIZE_IN_DAYS)
            EventsSummariesDateRange(
                startDate,
                endDate,
                endDateWasCurrentDayAtCreationTime = false
            ).coerceToCurrentDay(currentDay, shiftOnlyEndDate = true)
        }
    }

    // Forward to the past
    private fun EventsSummariesDateRange.next(): EventsSummariesDateRange {
        val endDate = startDate.minusDays(1)
        val startDate = endDate.minusDays(PAGE_SIZE_IN_DAYS)
        return EventsSummariesDateRange(
            startDate,
            endDate,
            endDateWasCurrentDayAtCreationTime = false
        )
    }

    private fun EventsSummariesDateRange.coerceToCurrentDay(
        currentDay: OffsetDateTime,
        shiftOnlyEndDate: Boolean
    ): EventsSummariesDateRange {
        val comparison = this.endDate.compareTo(currentDay)
        return when {
            comparison > 0 -> {
                // Range is in the future
                if (shiftOnlyEndDate) {
                    getInitialRange(currentDay).copy(startDate = this.startDate)
                } else {
                    getInitialRange(currentDay)
                }
            }
            comparison == 0 -> {
                // Range is the same as last week
                this.copy(endDateWasCurrentDayAtCreationTime = true)
            }
            else -> this.copy(endDateWasCurrentDayAtCreationTime = false)
        }
    }

    data class EventsSummariesDateRange(
        val startDate: OffsetDateTime,
        val endDate: OffsetDateTime,
        val endDateWasCurrentDayAtCreationTime: Boolean
    )

    companion object {
        internal const val PAGE_SIZE_IN_DAYS = 6L

        @Volatile
        private var lastLoadReturnedEmptyPage = false
        private const val EMPTY_PAGE_THROTTLE_DELAY_MS = 1000L
    }
}
