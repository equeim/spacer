// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.network

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import okhttp3.HttpUrl
import org.equeim.spacer.donki.data.DEFAULT_NASA_API_KEY
import org.equeim.spacer.donki.data.common.DONKI_BASE_URL
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.createDonkiRetrofit
import org.equeim.spacer.donki.data.common.toDonkiException
import retrofit2.create
import kotlin.coroutines.cancellation.CancellationException

internal class NotificationsDataSourceNetwork(
    private val customNasaApiKey: Flow<String?>,
    baseUrl: HttpUrl = DONKI_BASE_URL
) {
    private val api = createDonkiRetrofit(baseUrl, TAG).create<NotificationsApi>()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getNotifications(week: Week): List<NotificationJson> =
        customNasaApiKey.map { it ?: DEFAULT_NASA_API_KEY }.mapLatest { apiKey ->
            try {
                Log.d(TAG, "getNotifications() called with: week = $week")
                api.getNotifications(week.firstDay, week.lastDay, apiKey).also {
                    Log.d(
                        TAG,
                        "getNotifications: returning ${it.size} notifications for week = $week"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.toDonkiException() ?: e
                Log.e(TAG, "getNotifications: failed to get notifications for week = $week", error)
                throw error
            }
        }.first()

    private companion object {
        const val TAG = "NotificationsDataSourceNetwork"
    }
}
