// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.common.BaseRemoteMediator
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.cache.EventsDataSourceCache
import org.equeim.spacer.donki.data.events.network.json.EventSummary

internal class EventsSummariesRemoteMediator(
    private val repository: DonkiEventsRepository,
    private val cacheDataSource: EventsDataSourceCache,
    private val filters: StateFlow<DonkiEventsRepository.Filters>,
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
        val weeks = cacheDataSource.getWeeksThatNeedRefresh(filters.types, filters.dateRange).first()
        var filtered = weeks.asSequence()
        if (initialRefresh) {
            filtered = filtered.filter { !it.cachedRecently }
        }
        return filtered.map { it.week to it.eventType }.toList().takeIf { it.isNotEmpty() }
    }
}
