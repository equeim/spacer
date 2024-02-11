// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.screens.donki.displayStringResId
import org.equeim.spacer.ui.utils.createEventDateTimeFormatter
import org.equeim.spacer.ui.utils.createEventTimeFormatter
import org.equeim.spacer.ui.utils.defaultLocale
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import org.equeim.spacer.ui.utils.defaultTimeZoneFlow
import org.equeim.spacer.ui.utils.determineEventTimeZone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "DonkiEventDetailsScreenViewModel"
private val REFRESH_INDICATOR_DELAY = 300.milliseconds
private val LOADING_PLACEHOLDER_DELAY = 300.milliseconds

class DonkiEventDetailsScreenViewModel(private val eventId: EventId, application: Application) :
    AndroidViewModel(application) {

    private val settings = AppSettings(application)
    private val repository = DonkiRepository(application)

    private enum class LoadingType {
        Automatic, Manual
    }

    private val loadingType = MutableStateFlow<LoadingType?>(null)
    private val loadRequests = Channel<LoadingType>()

    private val _contentState = MutableStateFlow<ContentState>(ContentState.Empty)
    val contentState: StateFlow<ContentState> by ::_contentState

    @OptIn(ExperimentalCoroutinesApi::class)
    val showRefreshIndicator: StateFlow<Boolean> =
        loadingType.mapLatest { type ->
            when (type) {
                LoadingType.Automatic -> {
                    delay(REFRESH_INDICATOR_DELAY)
                    true
                }

                LoadingType.Manual -> true
                null -> false
            }
        }
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "Show refresh indicator: $it") }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        addCloseable(repository)

        val defaultLocaleFlow = application.defaultLocaleFlow().stateIn(viewModelScope, SharingStarted.Eagerly, application.defaultLocale)
        val eventTypesStringsCacheFlow = defaultLocaleFlow.map { ConcurrentHashMap<EventType, String>() }
        val formattersFlow = combine(
            defaultLocaleFlow,
            application.defaultTimeZoneFlow(),
            settings.displayEventsTimeInUTC.flow()
        ) { locale, defaultZone, displayEventsTimeInUTC ->
            Log.d(TAG, "locale = $locale, defaultZone = $defaultZone, displayEventsTimeInUTC = $displayEventsTimeInUTC")
            Formatters(locale, determineEventTimeZone(defaultZone, displayEventsTimeInUTC))
        }
        viewModelScope.launch {
            combine(
                loadRequests.receiveAsFlow(),
                eventTypesStringsCacheFlow,
                formattersFlow,
                ::Triple
            ).collectLatest { (loadType, eventTypesStringsCache, formatters) ->
                load(loadType, eventTypesStringsCache, formatters)
            }
        }
        viewModelScope.launch {
            loadRequests.send(LoadingType.Automatic)
            delay(LOADING_PLACEHOLDER_DELAY)
            _contentState.compareAndSet(ContentState.Empty, ContentState.LoadingPlaceholder)
        }
    }

    private class Formatters(locale: Locale, eventTimeZone: ZoneId) {
        val eventDateTimeFormatter = createEventDateTimeFormatter(locale, eventTimeZone)
        val eventTimeFormatter = createEventTimeFormatter(locale, eventTimeZone)
    }

    private suspend fun load(
        type: LoadingType,
        eventTypesStringsCache: ConcurrentHashMap<EventType, String>,
        formatters: Formatters,
    ) {
        Log.d(TAG, "load() called with: type = $type")
        loadingType.value = type
        _contentState.value.let {
            if (it is ContentState.ErrorPlaceholder) {
                _contentState.compareAndSet(it, ContentState.LoadingPlaceholder)
            }
        }
        try {
            when (type) {
                LoadingType.Automatic -> {
                    val event = repository.getEventById(eventId, forceRefresh = false)
                    _contentState.value = event.toContentState(eventTypesStringsCache, formatters)
                    if (event.needsRefreshing) {
                        _contentState.value =
                            repository.getEventById(eventId, forceRefresh = true)
                                .toContentState(eventTypesStringsCache, formatters)
                    }
                }

                LoadingType.Manual -> {
                    _contentState.value =
                        repository.getEventById(eventId, forceRefresh = true).toContentState(eventTypesStringsCache, formatters)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                _contentState.value = ContentState.ErrorPlaceholder(e.toString())
            }
        } finally {
            loadingType.value = null
        }
    }

    private suspend fun DonkiRepository.EventById.toContentState(eventTypesStringsCache: ConcurrentHashMap<EventType, String>, formatters: Formatters): ContentState.EventData =
        withContext(Dispatchers.Default) {
            ContentState.EventData(
                type = event.type.getDisplayString(eventTypesStringsCache),
                dateTime = formatters.eventDateTimeFormatter.format(event.time),
                event = event,
                linkedEvents = event.linkedEvents.mapNotNull { it.toPresentation(eventTypesStringsCache, formatters.eventDateTimeFormatter) },
                eventTimeFormatter = formatters.eventTimeFormatter
            )
        }

    fun refreshIfNotAlreadyLoading() {
        if (loadingType.value == null) {
            viewModelScope.launch {
                loadRequests.send(LoadingType.Manual)
            }
        }
    }

    private fun EventId.toPresentation(eventTypesStringsCache: ConcurrentHashMap<EventType, String>, eventDateTimeFormatter: DateTimeFormatter): LinkedEventPresentation? {
        val (type, time) = runCatching { parse() }.getOrElse {
            Log.e(TAG, it.message, it)
            null
        } ?: return null
        return LinkedEventPresentation(
            this,
            eventDateTimeFormatter.format(time),
            type.getDisplayString(eventTypesStringsCache)
        )
    }

    private fun EventType.getDisplayString(eventTypesStringsCache: ConcurrentHashMap<EventType, String>): String {
        return eventTypesStringsCache.computeIfAbsent(this) {
            getString(
                displayStringResId
            )
        }
    }

    private fun getString(@StringRes resId: Int) = getApplication<Application>().getString(resId)

    sealed interface ContentState {
        data object Empty : ContentState
        data object LoadingPlaceholder : ContentState
        data class EventData(
            val type: String,
            val dateTime: String,
            val event: Event,
            val linkedEvents: List<LinkedEventPresentation>,
            val eventTimeFormatter: DateTimeFormatter,
        ) : ContentState

        @JvmInline
        value class ErrorPlaceholder(val error: String) : ContentState
    }

    data class LinkedEventPresentation(
        val id: EventId,
        val dateTime: String,
        val type: String,
    )
}
