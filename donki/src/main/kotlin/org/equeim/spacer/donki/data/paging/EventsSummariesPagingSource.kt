// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.EventSummary
import java.time.Clock

private const val TAG = "EventsSummariesPagingSource"

internal class EventsSummariesPagingSource(
    private val repository: DonkiRepositoryInternal,
    private val invalidationEvents: Flow<Any>,
    private val filters: DonkiRepository.EventFilters,
    private val coroutineDispatchers: CoroutineDispatchers = CoroutineDispatchers(),
    private val clock: Clock = Clock.systemDefaultZone()
) : PagingSource<Week, EventSummary>() {
    private fun m(msg: String): String =
        "[0x${Integer.toHexString(System.identityHashCode(this))}] $msg"

    init {
        Log.d(TAG, m("created"))
        val coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        coroutineScope.launch {
            invalidationEvents.collect { invalidate() }
        }
        registerInvalidatedCallback {
            Log.d(TAG, m("invalidated"))
            coroutineScope.cancel()
        }
    }

    /**
     * We are refreshing from the top of the list so don't bother with implementing this
     * Initial loading key in [load] will be used
     */
    override fun getRefreshKey(state: PagingState<Week, EventSummary>): Week? = null

    override suspend fun load(params: LoadParams<Week>): LoadResult<Week, EventSummary> {
        Log.d(TAG, m("load() called with: params = $params"))
        Log.d(TAG, m("load: requested weeks are ${params.key}"))
        if (filters.types.isEmpty()) {
            Log.e(TAG, "load: filters' types are empty")
            return LoadResult.Page(emptyList(), null, null)
        }
        return withContext(coroutineDispatchers.Default) {
            val currentWeek = Week.getCurrentWeek(clock)
            Log.d(TAG, m("load: current week is $currentWeek"))

            val requestedWeek = params.key
            val week = when {
                requestedWeek != null -> {
                    if (requestedWeek > currentWeek) {
                        Log.e(TAG, m("load: requested week is in the future"))
                        return@withContext LoadResult.Invalid()
                    }
                    requestedWeek
                }
                filters.dateRange != null -> filters.dateRange.lastWeek
                else -> currentWeek
            }
            Log.d(TAG, m("load: loading week = $week"))
            try {
                val events = repository.getEventSummariesForWeek(
                    week = week,
                    eventTypes = filters.types.toList(),
                    dateRange = filters.dateRange?.coerceToWeek(week),
                    refreshCacheIfNeeded = params !is LoadParams.Refresh
                )
                if (lastLoadReturnedEmptyPage && events.isEmpty()) {
                    Log.d(
                        TAG,
                        m("load: previous load returned empty page and this one is empty too, wait 1 second before returning")
                    )
                    delay(EMPTY_PAGE_THROTTLE_DELAY_MS)
                }
                lastLoadReturnedEmptyPage = events.isEmpty()
                LoadResult.Page(
                    events,
                    week.prev(currentWeek, filters.dateRange),
                    week.next(filters.dateRange)
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, m("load: failed to get events summaries"), e)
                LoadResult.Error(e)
            }
        }.also {
            if (it is LoadResult.Page<*, *>) {
                Log.d(TAG, m("load: returning Page with ${it.data.size} events, prevKey = ${it.prevKey}, nextKey = ${it.nextKey}"))
            } else {
                Log.d(TAG, m("load: returning $it"))
            }
        }
    }

    private companion object {
        @Volatile
        var lastLoadReturnedEmptyPage = false
        const val EMPTY_PAGE_THROTTLE_DELAY_MS = 1000L
    }
}
