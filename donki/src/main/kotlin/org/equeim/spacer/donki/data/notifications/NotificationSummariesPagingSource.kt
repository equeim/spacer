// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import android.util.Log
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.BasePagingSource
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import java.time.Clock

internal class NotificationSummariesPagingSource(
    private val repository: DonkiNotificationsRepository,
    private val filters: DonkiNotificationsRepository.Filters,
    coroutineDispatchers: CoroutineDispatchers = CoroutineDispatchers(),
    clock: Clock = Clock.systemDefaultZone()
) : BasePagingSource<CachedNotificationSummary>(
    dateRange = filters.dateRange,
    coroutineDispatchers = coroutineDispatchers,
    clock = clock,
    TAG = "NotificationSummariesPagingSource"
) {
    init {
        Log.d(TAG, m("created"))
    }

    override suspend fun getItemsForWeek(
        week: Week,
        dateRange: DateRange?,
    ): List<CachedNotificationSummary> = repository.getNotificationSummariesForWeek(
        week = week,
        types = filters.types,
        dateRange = dateRange
    )

    override suspend fun load(params: LoadParams<Week>): LoadResult<Week, CachedNotificationSummary> =
        if (filters.types.isEmpty()) {
            Log.e(TAG, "load: all notification types are disabled")
            LoadResult.Page(emptyList(), null, null)
        } else {
            super.load(params)
        }
}
