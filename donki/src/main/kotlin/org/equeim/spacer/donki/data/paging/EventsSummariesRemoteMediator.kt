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
import org.equeim.spacer.donki.data.cache.DonkiDataSourceCache
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.Week
import java.time.Clock

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

    override suspend fun initialize(): InitializeAction {
        Log.d(TAG, "initialize() called")
        val initialLoadWeeks = Week.getInitialLoadWeeks(clock)
        Log.d(TAG, "initialize: initial load weeks are $initialLoadWeeks")
        val refresh =
            initialLoadWeeks
                .forTypes(EVENT_TYPES)
                .any { (week, type) ->
                    cacheDataSource.isWeekCachedAndOutOfDate(week, type).also {
                        if (it) {
                            Log.d(
                                TAG,
                                "initialize: week $week with event type $type is cached but out of date"
                            )
                        }
                    }
                }
        return if (refresh) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            Log.d(TAG, "initialize: don't need to update cache for initial load weeks")
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
        val weeks = mutableListOf<Pair<Week, EventType>>()
        Week.getInitialLoadWeeks(clock).forTypes(EVENT_TYPES)
                .filterTo(weeks) { (week, type) -> cacheDataSource.isWeekCachedAndOutOfDate(week, type) }
        if (weeks.isEmpty()) {
            Log.d(TAG, "load: don't need to update cache for initial load weeks")
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
}

private fun List<Week>.forTypes(eventTypes: List<EventType>): Sequence<Pair<Week, EventType>> =
    asSequence()
        .flatMap { week -> eventTypes.asSequence().map { type -> week to type } }
