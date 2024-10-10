// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.events.DonkiEventsRepository
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.ui.screens.donki.LinkedEventPresentation
import org.equeim.spacer.ui.screens.donki.donkiErrorToString
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.utils.createEventDateTimeFormatter
import org.equeim.spacer.ui.utils.createEventTimeFormatter
import org.equeim.spacer.ui.utils.defaultLocale
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import org.equeim.spacer.ui.utils.defaultTimeZoneFlow
import org.equeim.spacer.ui.utils.determineEventTimeZone
import org.equeim.spacer.ui.utils.getString
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
    private val repository = DonkiEventsRepository(settings.customNasaApiKeyOrNull(), application)

    private enum class LoadingType {
        Initial, Refresh
    }

    private val loadingType = MutableStateFlow<LoadingType?>(null)
    private val loadRequests = Channel<LoadingType>()

    private val _contentState = MutableStateFlow<ContentState>(ContentState.Empty)
    val contentState: StateFlow<ContentState> by ::_contentState

    private val _snackbarError = MutableStateFlow<String?>(null)
    val snackbarError: StateFlow<String?> by ::_snackbarError

    @OptIn(ExperimentalCoroutinesApi::class)
    val showRefreshIndicator: StateFlow<Boolean> =
        loadingType.mapLatest { type ->
            when (type) {
                LoadingType.Initial -> {
                    delay(REFRESH_INDICATOR_DELAY)
                    true
                }

                LoadingType.Refresh -> true
                null -> false
            }
        }
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "Show refresh indicator: $it") }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        addCloseable(repository)
        loadEvent()
        viewModelScope.launch {
            loadRequests.send(LoadingType.Initial)
            delay(LOADING_PLACEHOLDER_DELAY)
            _contentState.compareAndSet(ContentState.Empty, ContentState.LoadingPlaceholder)
        }
    }

    fun refreshIfNotAlreadyLoading() {
        Log.d(TAG, "refreshIfNotAlreadyLoading() called")
        viewModelScope.launch {
            if (loadingType.value == null) {
                Log.d(TAG, "refreshIfNotAlreadyLoading: refreshing")
                loadRequests.send(LoadingType.Refresh)
            } else {
                Log.d(TAG, "refreshIfNotAlreadyLoading: already loading")
            }
        }
    }

    fun onActivityResumed() {
        Log.d(TAG, "onActivityResumed() called")
        viewModelScope.launch {
            val event = (_contentState.value as? ContentState.EventData)?.event
            if (event != null && loadingType.value == null && repository.isEventNeedsRefreshing(event)) {
                Log.d(TAG, "onActivityResumed: refreshing")
                loadRequests.send(LoadingType.Refresh)
            }
        }
    }

    private fun loadEvent() {
        viewModelScope.launch {
            processLoadRequests().mapEventToPresentation().collect {
                _contentState.value = it
                _snackbarError.value = null
                Log.d(TAG, "Finished processing loaded event")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun processLoadRequests(): Flow<DonkiEventsRepository.EventById> =
        loadRequests.receiveAsFlow()
            .onEach {
                _contentState.value.let {
                    if (it is ContentState.ErrorPlaceholder) {
                        _contentState.compareAndSet(it, ContentState.LoadingPlaceholder)
                    }
                }
                _snackbarError.value = null
            }
            .mapLatest { type ->
                loadingType.value = type
                Log.d(TAG, "Loading event with id $eventId, load type is $type")
                repository.getEventById(
                    eventId,
                    forceRefresh = when (type) {
                        LoadingType.Initial -> false
                        LoadingType.Refresh -> true
                    }
                ).also {
                    Log.d(TAG, "Loaded event with id $eventId")
                    loadingType.value = null
                }
            }.onEach {
                if (it.needsRefreshing) {
                    Log.d(TAG, "Event needs refreshing, schedule load request")
                    loadRequests.send(LoadingType.Refresh)
                }
            }.retry {
                Log.e(TAG, "Failed to load event", it)
                loadingType.value = null
                setErrorToContentOrSnackbar(it.donkiErrorToString(getApplication()))
                true
            }

    private fun Flow<DonkiEventsRepository.EventById>.mapEventToPresentation(): Flow<ContentState.EventData> {
        val application = getApplication<Application>()
        val defaultLocaleFlow =
            application.defaultLocaleFlow().stateIn(viewModelScope, SharingStarted.Eagerly, application.defaultLocale)
        val eventTypesStringsCacheFlow = defaultLocaleFlow.map { ConcurrentHashMap<EventType, String>() }
        val formattersFlow = combine(
            defaultLocaleFlow,
            application.defaultTimeZoneFlow(),
            settings.displayEventsTimeInUTC.flow()
        ) { locale, defaultZone, displayEventsTimeInUTC ->
            Log.d(TAG, "locale = $locale, defaultZone = $defaultZone, displayEventsTimeInUTC = $displayEventsTimeInUTC")
            Formatters(locale, determineEventTimeZone(defaultZone, displayEventsTimeInUTC))
        }
        return combine(
            this,
            eventTypesStringsCacheFlow,
            formattersFlow
        ) { event, eventTypesStringsCache, formatters ->
            Log.d(TAG, "Converting loaded event to presentation")
            withContext(Dispatchers.Default) {
                event.toContentState(eventTypesStringsCache, formatters)
            }
        }.retry {
            Log.e(TAG, "Failed to convert loaded event to presentation", it)
            setErrorToContentOrSnackbar(it.toString())
            true
        }
    }

    private class Formatters(locale: Locale, eventTimeZone: ZoneId) {
        val eventDateTimeFormatter = createEventDateTimeFormatter(locale, eventTimeZone)
        val eventTimeFormatter = createEventTimeFormatter(locale, eventTimeZone)
    }

    private fun DonkiEventsRepository.EventById.toContentState(
        eventTypesStringsCache: ConcurrentHashMap<EventType, String>,
        formatters: Formatters,
    ) = ContentState.EventData(
        type = event.type.getDisplayString(eventTypesStringsCache),
        dateTime = formatters.eventDateTimeFormatter.format(event.time),
        event = event,
        linkedEvents = event.linkedEvents.mapNotNull {
            it.toPresentation(
                eventTypesStringsCache,
                formatters.eventDateTimeFormatter
            )
        },
        eventTimeFormatter = formatters.eventTimeFormatter
    )

    private fun EventId.toPresentation(
        eventTypesStringsCache: ConcurrentHashMap<EventType, String>,
        eventDateTimeFormatter: DateTimeFormatter,
    ): LinkedEventPresentation? {
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
        return eventTypesStringsCache.computeIfAbsent(this) { getString(displayStringResId) }
    }

    private fun setErrorToContentOrSnackbar(error: String) {
        if (_contentState.value is ContentState.EventData) {
            _snackbarError.value = error
        } else {
            _contentState.value = ContentState.ErrorPlaceholder(error)
        }
    }

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
}
