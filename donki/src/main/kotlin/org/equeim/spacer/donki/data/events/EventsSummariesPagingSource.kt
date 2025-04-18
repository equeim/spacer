// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import android.util.Log
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.BasePagingSource
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import java.time.Clock

internal class EventsSummariesPagingSource(
    private val repository: DonkiEventsRepository,
    private val filters: DonkiEventsRepository.Filters,
    coroutineDispatchers: CoroutineDispatchers,
    clock: Clock
) : BasePagingSource<EventSummary>(
    dateRange = filters.dateRange,
    coroutineDispatchers = coroutineDispatchers,
    clock = clock,
    TAG = "EventsSummariesPagingSource"
) {
    init {
        Log.d(TAG, m("created"))
    }

    override suspend fun getItemsForWeek(
        week: Week,
        dateRange: DateRange?,
    ): List<EventSummary> = repository.getEventSummariesForWeek(
        week = week,
        eventTypes = filters.types,
        dateRange = dateRange,
    )

    override suspend fun load(params: LoadParams<Week>): LoadResult<Week, EventSummary> =
        if (filters.types.isEmpty()) {
            Log.e(TAG, "load: all event types are disabled")
            LoadResult.Page(emptyList(), null, null)
        } else {
            super.load(params)
        }
}
