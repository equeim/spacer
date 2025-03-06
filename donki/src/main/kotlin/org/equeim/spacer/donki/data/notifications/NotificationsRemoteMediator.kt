// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.common.BaseRemoteMediator
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDataSourceCache

internal class NotificationsRemoteMediator(
    private val repository: DonkiNotificationsRepository,
    private val cacheDataSource: NotificationsDataSourceCache,
    private val filters: StateFlow<DonkiNotificationsRepository.Filters>,
) : BaseRemoteMediator<CachedNotificationSummary, List<Week>>() {

    override val TAG: String get() = "NotificationsRemoteMediator"

    override suspend fun refresh(data: List<Week>) {
        coroutineScope {
            for (week in data) {
                launch { repository.updateNotificationsForWeek(week) }
            }
        }
    }

    override suspend fun getRefreshData(initialRefresh: Boolean): List<Week>? {
        Log.d(TAG, "getRefreshData() called with: initialRefresh = $initialRefresh")
        repository.waitUntilBackgroundUpdateIsCompleted()
        val weeks = cacheDataSource.getWeeksThatNeedRefresh(filters.value.dateRange).first()
        var filtered = weeks.asSequence()
        if (initialRefresh) {
            filtered = filtered.filter { !it.cachedRecently }
        }
        return filtered.map { it.week }.toList().takeIf { it.isNotEmpty() }.also {
            Log.d(TAG, "getRefreshData: returning $it")
        }
    }
}
