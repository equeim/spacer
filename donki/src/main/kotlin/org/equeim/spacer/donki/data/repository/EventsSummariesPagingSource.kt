package org.equeim.spacer.donki.data.repository

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.network.DonkiDataSourceNetwork
import java.time.*
import java.time.temporal.ChronoField

private const val TAG = "EventsSummariesPagingSource"

internal class EventsSummariesPagingSource(
    private val dataSource: DonkiDataSourceNetwork,
    private val coroutineDispatchers: CoroutineDispatchers = CoroutineDispatchers(),
    private val clock: Clock = Clock.systemDefaultZone()
) : PagingSource<EventsSummariesPagingSource.Week, EventSummary>() {
    init {
        Log.d(TAG, "EventsSummariesPagingSource() called")
        registerInvalidatedCallback { Log.d(TAG, "EventsSummariesPagingSource invalidated") }
    }

    /**
     * We are refreshing from the top of the list so don't bother with implementing this
     * Initial loading key in [load] will be used
     */
    override fun getRefreshKey(state: PagingState<Week, EventSummary>): Week? = null

    override suspend fun load(params: LoadParams<Week>): LoadResult<Week, EventSummary> {
        Log.d(TAG, "load() called with: params = $params")
        return withContext(coroutineDispatchers.Default) {
            Log.d(TAG, "load: requested weeks are ${params.key}")
            val (currentDay, currentWeek) = Week.getCurrentDayAndWeek(clock)
            Log.d(TAG, "load: current day is $currentDay")
            Log.d(TAG, "load: current week is $currentWeek")
            val weeks = params.key?.let { requestedWeek ->
                if (requestedWeek > currentWeek) {
                    Log.e(TAG, "load: requested week is in the future")
                    return@withContext LoadResult.Invalid()
                }
                listOf(requestedWeek)
            } ?: Week.getInitialLoadWeeks(currentWeek)
            Log.d(TAG, "load: loading weeks = $weeks")
            try {
                val allEvents = mutableListOf<EventSummary>()
                val mutex = Mutex()
                coroutineScope {
                    for (week in weeks) {
                        val startDate = week.getFirstDayInstant()
                        val endDate = week.getInstantForLastDayNotInFuture(currentDay)
                        for (eventType in EventType.values()) {
                            launch {
                                val events = dataSource.getEvents(eventType, startDate, endDate)
                                    .map { it.toEventSummary() }
                                mutex.withLock { allEvents.addAll(events) }
                            }
                        }
                    }
                }
                allEvents.sortByDescending { it.time }
                if (lastLoadReturnedEmptyPage && allEvents.isEmpty()) {
                    Log.d(
                        TAG,
                        "load: previous load returned empty page and this one is empty too, wait 1 second before returning"
                    )
                    delay(EMPTY_PAGE_THROTTLE_DELAY_MS)
                }
                lastLoadReturnedEmptyPage = allEvents.isEmpty()
                LoadResult.Page(
                    allEvents,
                    weeks.first().prev(currentWeek),
                    weeks.last().next()
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "load: failed to get events summaries", e)
                LoadResult.Error(e)
            }
        }.also {
            Log.d(TAG, "load: returning $it")
        }
    }

    @JvmInline
    value class Week @VisibleForTesting constructor(
        private val firstDay: LocalDate
    ) : Comparable<Week> {
        init {
            require(firstDay.dayOfWeek == DayOfWeek.MONDAY) { "First day must be Monday" }
        }

        override fun compareTo(other: Week) = firstDay.compareTo(other.firstDay)

        fun getFirstDayInstant(): Instant = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)

        fun getInstantForLastDayNotInFuture(currentDay: LocalDate): Instant {
            val lastDay = firstDay.with(ChronoField.DAY_OF_WEEK, 7)
            return if (lastDay > currentDay) {
                currentDay
            } else {
                lastDay
            }.atStartOfDay().toInstant(ZoneOffset.UTC)
        }

        // Back to the future
        fun prev(currentWeek: Week): Week? {
            return if (this == currentWeek) {
                null
            } else {
                Week(firstDay.plusWeeks(1))
            }
        }

        // Forward to the past
        fun next(): Week {
            return Week(firstDay.minusWeeks(1))
        }

        companion object {
            fun getCurrentDayAndWeek(clock: Clock): Pair<LocalDate, Week> {
                val currentDay = Instant.now(clock).atOffset(ZoneOffset.UTC).toLocalDate()
                val currentWeek = Week(currentDay.with(currentDay.with(ChronoField.DAY_OF_WEEK, 1)))
                return currentDay to currentWeek
            }

            private const val INITIAL_LOAD_WEEKS_COUNT = 3
            fun getInitialLoadWeeks(currentWeek: Week): List<Week> =
                (0 until INITIAL_LOAD_WEEKS_COUNT - 1).runningFold(currentWeek) { week, _ -> week.next() }
        }
    }

    private companion object {
        @Volatile
        var lastLoadReturnedEmptyPage = false
        const val EMPTY_PAGE_THROTTLE_DELAY_MS = 1000L
    }
}
