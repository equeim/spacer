// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer

import android.content.Context
import okhttp3.OkHttpClient
import org.equeim.spacer.donki.data.common.createDonkiOkHttpClient
import org.equeim.spacer.donki.data.events.DonkiEventsRepository
import org.equeim.spacer.donki.data.notifications.DonkiNotificationsRepository

private val okHttpClient: OkHttpClient by lazy { createDonkiOkHttpClient() }

@Volatile
private var eventsRepository: DonkiEventsRepository? = null

fun getDonkiEventsRepositoryInstance(
    context: Context
): DonkiEventsRepository {
    val appContext = context.applicationContext!!
    return eventsRepository ?: synchronized(DonkiEventsRepository::class.java) {
        DonkiEventsRepository(AppSettings(appContext).customNasaApiKeyOrNull(), okHttpClient, appContext).also {
            eventsRepository = it
        }
    }
}

@Volatile
private var notificationsRepository: DonkiNotificationsRepository? = null

fun getDonkiNotificationsRepositoryInstance(
    context: Context
): DonkiNotificationsRepository {
    val appContext = context.applicationContext!!
    return notificationsRepository ?: synchronized(DonkiNotificationsRepository::class.java) {
        DonkiNotificationsRepository(
            AppSettings(appContext).customNasaApiKeyOrNull(),
            okHttpClient,
            appContext
        ).also {
            notificationsRepository = it
        }
    }
}
