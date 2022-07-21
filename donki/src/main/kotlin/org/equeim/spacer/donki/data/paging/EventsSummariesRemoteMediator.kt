package org.equeim.spacer.donki.data.paging

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.cache.DonkiDataSourceCache
import org.equeim.spacer.donki.data.forTypes
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "EventsSummariesRemoteMediator"

private val EVENT_TYPES = EventType.All

@OptIn(ExperimentalPagingApi::class)
internal class EventsSummariesRemoteMediator(
    private val repository: DonkiRepositoryInternal,
    private val cacheDataSource: DonkiDataSourceCache,
    private val clock: Clock = Clock.systemDefaultZone()
) : RemoteMediator<Week, EventSummary>() {
    private val _refreshed = MutableSharedFlow<Unit>()
    val refreshed: Flow<Unit> by ::_refreshed

    private val pendingInitialRefreshWeeks = AtomicReference<List<Pair<Week, EventType>>>(null)

    override suspend fun initialize(): InitializeAction {
        Log.d(TAG, "initialize() called")
        val initialLoadWeeks = Week.getInitialLoadWeeks(clock)
        Log.d(TAG, "initialize: initial load weeks are $initialLoadWeeks")
        val weeks = try {
            getRefreshWeeks(initialRefresh = true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "initialize: failed to check what weeks need to be refreshed", e)
            emptyList()
        }
        pendingInitialRefreshWeeks.set(weeks)
        return if (weeks.isNotEmpty()) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }.also {
            Log.d(TAG, "initialize: returning $it")
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Week, EventSummary>
    ): MediatorResult {
        Log.d(TAG, "load() called with: loadType = $loadType, state = $state")
        if (loadType != LoadType.REFRESH) {
            Log.d(TAG, "load: not refreshing, ignore")
            return MediatorResult.Success(endOfPaginationReached = loadType == LoadType.PREPEND)
        }
        val weeks = pendingInitialRefreshWeeks.getAndSet(null) ?: try {
            getRefreshWeeks(initialRefresh = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "load: failed to check what weeks need to be refreshed", e)
            return MediatorResult.Error(e)
        }
        if (weeks.isEmpty()) {
            return MediatorResult.Success(endOfPaginationReached = false)
        }
        Log.d(TAG, "load: loading weeks $weeks")
        return try {
            coroutineScope {
                for ((week, type) in weeks) {
                    launch { repository.updateEventsForWeek(week, type) }
                }
            }
            _refreshed.emit(Unit)
            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "load: failed to load events", e)
            MediatorResult.Error(e)
        }.also { Log.d(TAG, "load: returning $it") }
    }

    private suspend fun getRefreshWeeks(initialRefresh: Boolean): List<Pair<Week, EventType>> {
        Log.d(TAG, "getRefreshWeeks() called with: initialRefresh = $initialRefresh")
        val initialLoadWeeks = Week.getInitialLoadWeeks(clock)
        Log.d(TAG, "getRefreshWeeks: initial load weeks are $initialLoadWeeks")
        /**
         * Can't use [Sequence.filter] because it isn't inline and we can't suspend
         */
        val weeks = mutableListOf<Pair<Week, EventType>>()
        initialLoadWeeks
            .forTypes(EVENT_TYPES)
            .filterTo(weeks) { (week, type) ->
                cacheDataSource.isWeekCachedAndNeedsRefresh(week, type, refreshIfRecentlyLoaded = !initialRefresh).also {
                    if (it) {
                        Log.d(
                            TAG,
                            "getRefreshWeeks: week $week with event type $type is cached but needs to be refreshed"
                        )
                    }
                }
            }
        if (weeks.isEmpty()) {
            Log.d(TAG, "getRefreshWeeks: don't need to refresh cache for initial load weeks")
        }
        return weeks
    }
}
