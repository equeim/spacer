// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import org.equeim.spacer.donki.data.common.BaseRemoteMediator
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDataSourceCache
import java.time.Clock

internal class NotificationsRemoteMediator(
    private val repository: DonkiNotificationsRepository,
    private val cacheDataSource: NotificationsDataSourceCache,
    private val filters: StateFlow<DonkiNotificationsRepository.Filters>,
    private val clock: Clock,
) : BaseRemoteMediator<CachedNotificationSummary, Week>() {

    override val TAG: String get() = "NotificationsRemoteMediator"

    override suspend fun refresh(data: Week) {
        repository.updateNotificationsForWeek(data)
    }

    override suspend fun getRefreshData(initialRefresh: Boolean): Week? {
        Log.d(TAG, "getRefreshData() called with: initialRefresh = $initialRefresh")
        val filters = this.filters.value
        val initialLoadWeek = filters.dateRange?.lastWeek ?: Week.getCurrentWeek(clock)
        Log.d(TAG, "getRefreshData: initial load week is $initialLoadWeek")
        return if (cacheDataSource.isWeekCachedAndNeedsRefresh(initialLoadWeek, refreshIfRecentlyLoaded = !initialRefresh)) {
            initialLoadWeek
        } else {
            null
        }
    }
}
