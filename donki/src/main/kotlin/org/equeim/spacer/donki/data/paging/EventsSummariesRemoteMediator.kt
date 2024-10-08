// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.cache.DonkiDataSourceCache
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "EventsSummariesRemoteMediator"

@OptIn(ExperimentalPagingApi::class)
internal class EventsSummariesRemoteMediator(
    private val repository: DonkiRepositoryInternal,
    private val cacheDataSource: DonkiDataSourceCache,
    private val filters: StateFlow<DonkiRepository.EventFilters>,
    private val clock: Clock = Clock.systemDefaultZone(),
) : RemoteMediator<Week, EventSummary>() {
    private val _refreshed = MutableSharedFlow<Unit>()
    val refreshed: Flow<Unit> by ::_refreshed

    private val pendingInitialRefreshWeeks = AtomicReference<List<Pair<Week, EventType>>>(null)

    override suspend fun initialize(): InitializeAction {
        Log.d(TAG, "initialize() called")
        val weeks = try {
            getRefreshWeeks(initialRefresh = true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "initialize: failed to check what weeks need to be refreshed", e)
            emptyList()
        }
        return if (weeks.isNotEmpty()) {
            pendingInitialRefreshWeeks.set(weeks)
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }.also {
            Log.d(TAG, "initialize: returning $it")
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Week, EventSummary>,
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
        val filters = this.filters.value
        val initialLoadWeek = filters.dateRange?.lastWeek ?: Week.getCurrentWeek(clock)
        Log.d(TAG, "getRefreshWeeks: initial load week is $initialLoadWeek")
        /**
         * Can't use [Sequence.filter] because it isn't inline and we can't suspend
         */
        val weeks = ArrayList<Pair<Week, EventType>>(filters.types.size)
        for (type in filters.types) {
            val needsRefresh = cacheDataSource.isWeekCachedAndNeedsRefresh(initialLoadWeek, type, refreshIfRecentlyLoaded = !initialRefresh)
            if (needsRefresh) {
                Log.d(
                    TAG,
                    "getRefreshWeeks: week $initialLoadWeek with event type $type is cached but needs to be refreshed"
                )
                weeks.add(initialLoadWeek to type)
            }
        }
        if (weeks.isEmpty()) {
            Log.d(TAG, "getRefreshWeeks: don't need to refresh cache for initial load weeks")
        }
        return weeks
    }
}
