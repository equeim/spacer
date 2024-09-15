// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.network

import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate

internal interface NotificationsApi {
    @GET("notifications")
    suspend fun getNotifications(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<NotificationJson>
}
