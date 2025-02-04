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
import okhttp3.OkHttpClient
import org.equeim.spacer.donki.data.DEFAULT_NASA_API_KEY
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.createDonkiRetrofit
import org.equeim.spacer.donki.data.common.toDonkiNetworkDataSourceException
import retrofit2.create
import kotlin.coroutines.cancellation.CancellationException

internal class NotificationsDataSourceNetwork(
    private val customNasaApiKey: Flow<String?>,
    okHttpClient: OkHttpClient,
    baseUrl: HttpUrl,
) {
    private val api = createDonkiRetrofit(okHttpClient, baseUrl).create<NotificationsApi>()

    /**
     * @throws DonkiNetworkDataSourceException
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getNotifications(week: Week): List<NotificationJson> =
        customNasaApiKey.map { it ?: DEFAULT_NASA_API_KEY }.mapLatest { apiKey ->
            try {
                Log.d(TAG, "getNotifications() called with: week = $week")
                api.getNotifications(week.firstDay, week.lastDay, apiKey).coerceInWeek(week).also {
                    Log.d(
                        TAG,
                        "getNotifications: returning ${it.size} notifications for week = $week"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e.toDonkiNetworkDataSourceException("getNotifications with: week = $week failed")
            }
        }.first()

    private companion object {
        const val TAG = "NotificationsDataSourceNetwork"

        fun List<NotificationJson>.coerceInWeek(week: Week): List<NotificationJson> {
            val startInstant = week.getFirstDayInstant()
            val endInstant = week.getInstantAfterLastDay()
            return this.filter {
                if (it.time >= startInstant && it.time < endInstant) {
                    true
                } else {
                    Log.e(TAG, "getNotifications: notification with id ${it.id} and time ${it.time} does not belong in week $week")
                    false
                }
            }
        }
    }
}
