// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.common.BaseRemoteMediator
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.cache.EventsDataSourceCache
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import java.time.Clock

internal class EventsSummariesRemoteMediator(
    private val repository: DonkiEventsRepositoryInternal,
    private val cacheDataSource: EventsDataSourceCache,
    private val filters: StateFlow<DonkiEventsRepository.Filters>,
    private val clock: Clock = Clock.systemDefaultZone(),
) : BaseRemoteMediator<EventSummary, List<Pair<Week, EventType>>>() {

    override val TAG: String get() = "EventsSummariesRemoteMediator"

    override suspend fun refresh(data: List<Pair<Week, EventType>>) {
        coroutineScope {
            for ((week, type) in data) {
                launch { repository.updateEventsForWeek(week, type) }
            }
        }
    }

    override suspend fun getRefreshData(initialRefresh: Boolean): List<Pair<Week, EventType>>? {
        Log.d(TAG, "getRefreshData() called with: initialRefresh = $initialRefresh")
        val filters = this.filters.value
        val initialLoadWeek = filters.dateRange?.lastWeek ?: Week.getCurrentWeek(clock)
        Log.d(TAG, "getRefreshData: initial load week is $initialLoadWeek")
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
            Log.d(TAG, "getRefreshData: don't need to refresh cache for initial load weeks")
            return null
        }
        return weeks
    }
}
