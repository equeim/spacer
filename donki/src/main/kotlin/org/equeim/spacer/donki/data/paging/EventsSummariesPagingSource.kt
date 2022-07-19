package org.equeim.spacer.donki.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import java.time.Clock

private const val TAG = "EventsSummariesPagingSource"

private val EVENT_TYPES = EventType.All

internal class EventsSummariesPagingSource(
    private val repository: DonkiRepositoryInternal,
    private val invalidationEvents: Flow<Unit>,
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
        return withContext(coroutineDispatchers.Default) {
            Log.d(TAG, m("load: requested weeks are ${params.key}"))
            val currentWeek = Week.getCurrentWeek(clock)
            Log.d(TAG, m("load: current week is $currentWeek"))
            val weeks = params.key?.let { requestedWeek ->
                if (requestedWeek > currentWeek) {
                    Log.e(TAG, m("load: requested week is in the future"))
                    return@withContext LoadResult.Invalid()
                }
                listOf(requestedWeek)
            } ?: Week.getInitialLoadWeeks(currentWeek)
            Log.d(TAG, m("load: loading weeks = $weeks"))
            try {
                val events = coroutineScope {
                    weeks.map { week ->
                        async {
                            repository.getEventSummariesForWeek(
                                week = week,
                                eventTypes = EVENT_TYPES,
                                refreshCacheIfNeeded = params !is LoadParams.Refresh
                            )
                        }
                    }.awaitAll().flatten()
                }
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
                    weeks.first().prev(currentWeek),
                    weeks.last().next()
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
