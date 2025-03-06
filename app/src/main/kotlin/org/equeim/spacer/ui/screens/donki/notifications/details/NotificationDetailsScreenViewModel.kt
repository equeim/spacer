// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications.details

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.AppSettings
import org.equeim.spacer.DonkiSystemNotificationsManager
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.cache.CachedNotification
import org.equeim.spacer.donki.data.notifications.findLinkedEvents
import org.equeim.spacer.donki.data.notifications.findWebLinks
import org.equeim.spacer.getDonkiNotificationsRepositoryInstance
import org.equeim.spacer.ui.screens.donki.LinkedEventPresentation
import org.equeim.spacer.ui.screens.donki.donkiErrorToString
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.utils.createEventDateTimeFormatter
import org.equeim.spacer.ui.utils.defaultLocale
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import org.equeim.spacer.ui.utils.defaultTimeZoneFlow
import org.equeim.spacer.ui.utils.determineEventTimeZone
import java.util.concurrent.ConcurrentHashMap

class NotificationDetailsScreenViewModel(
    private val notificationId: NotificationId,
    application: Application
) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val repository = getDonkiNotificationsRepositoryInstance(application)
    private val systemNotificationsManager = DonkiSystemNotificationsManager(application)

    private val _contentState = MutableStateFlow<ContentState>(ContentState.Empty)
    val contentState: StateFlow<ContentState> by ::_contentState

    init {
        viewModelScope.launch { loadNotification() }
    }

    private suspend fun loadNotification() {
        Log.d(TAG, "Loading notification with id $notificationId")
        val notification = try {
            repository.getCachedNotificationByIdAndMarkAsRead(notificationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load notification", e)
            _contentState.value = ContentState.ErrorPlaceholder(e.donkiErrorToString(getApplication()))
            return
        }
        systemNotificationsManager.removeNotification(notificationId)
        notification.mapToPresentation().collect {
            _contentState.value = it
        }
    }

    private fun CachedNotification.mapToPresentation(): Flow<ContentState.NotificationData> {
        val application = getApplication<Application>()
        val defaultLocaleFlow =
            application.defaultLocaleFlow().stateIn(viewModelScope, SharingStarted.Eagerly, application.defaultLocale)
        val eventTypesStringsCacheFlow = defaultLocaleFlow.map { ConcurrentHashMap<EventType, String>() }
        val formatterFlow = combine(
            defaultLocaleFlow,
            application.defaultTimeZoneFlow(),
            settings.displayEventsTimeInUTC.flow()
        ) { locale, defaultZone, displayEventsTimeInUTC ->
            Log.d(TAG, "locale = $locale, defaultZone = $defaultZone, displayEventsTimeInUTC = $displayEventsTimeInUTC")
            createEventDateTimeFormatter(locale, determineEventTimeZone(defaultZone, displayEventsTimeInUTC))
        }
        return combine(
            eventTypesStringsCacheFlow,
            formatterFlow
        ) { eventTypesStringsCache, dateTimeFormatter ->
            withContext(Dispatchers.Default) {
                ContentState.NotificationData(
                    title = title ?: application.getString(type.displayStringResId),
                    dateTime = dateTimeFormatter.format(time),
                    body = BodyWithLinks(body, body.findWebLinks()),
                    link = link,
                    linkedEvents = body.findLinkedEvents().map { (id, parsed) ->
                        LinkedEventPresentation(
                            id = id,
                            dateTime = dateTimeFormatter.format(parsed.time),
                            type = eventTypesStringsCache.computeIfAbsent(parsed.type) { application.getString(parsed.type.displayStringResId) }
                        )
                    }
                )
            }
        }
    }

    sealed interface ContentState {
        data object Empty : ContentState
        data class NotificationData(
            val title: String,
            val dateTime: String,
            val body: BodyWithLinks,
            val link: String,
            val linkedEvents: List<LinkedEventPresentation>,
        ) : ContentState

        data class ErrorPlaceholder(val error: String) : ContentState
    }

    data class BodyWithLinks(val body: String, val links: List<Pair<String, IntRange>>)

    private companion object {
        const val TAG = "NotificationDetailsScreenViewModel"
    }
}
