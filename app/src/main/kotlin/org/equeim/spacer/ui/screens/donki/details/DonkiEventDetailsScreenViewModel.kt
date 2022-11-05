// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.screens.donki.displayStringResId
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "DonkiEventDetailsScreenViewModel"
private val REFRESH_INDICATOR_DELAY = 300.milliseconds

class DonkiEventDetailsScreenViewModel(private val eventId: EventId, application: Application) :
    AndroidViewModel(application) {

    private val repository = DonkiRepository(application)

    private val _contentUiState = MutableStateFlow<ContentUiState>(ContentUiState.Loading)
    val contentUiState: StateFlow<ContentUiState> by ::_contentUiState

    private enum class RefreshingState {
        NotRefreshing,
        RefreshingAutomatically,
        RefreshingManually
    }
    private val refreshing = MutableStateFlow(RefreshingState.NotRefreshing)

    private enum class RefreshIndicatorState {
        Show,
        ShowDelayed,
        Hide
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val showRefreshIndicator: StateFlow<Boolean> =
        combine(contentUiState, refreshing) { contentUiState, refreshing ->
            when {
                refreshing == RefreshingState.RefreshingManually -> RefreshIndicatorState.Show
                contentUiState is ContentUiState.Loading
                        || refreshing == RefreshingState.RefreshingAutomatically -> RefreshIndicatorState.ShowDelayed
                else -> RefreshIndicatorState.Hide
            }
        }
            .onEach { Log.d(TAG, "Refresh indicator state: $it") }
            .distinctUntilChanged()
            .mapLatest {
                if (it == RefreshIndicatorState.ShowDelayed) delay(REFRESH_INDICATOR_DELAY)
                it != RefreshIndicatorState.Hide
            }
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "Show refresh indicator: $it") }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private class LocaleDependentState {
        val eventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
        val eventTypesStrings = ConcurrentHashMap<EventType, String>()
    }
    @Volatile
    private lateinit var localeDependentState: LocaleDependentState

    @Volatile
    private lateinit var timeZone: ZoneId

    init {
        val defaultLocaleFlow = application.defaultLocaleFlow().onEach {
            localeDependentState = LocaleDependentState()
        }
        val timeZoneFlow = AppSettings(application).displayEventsTimeInUTC.flow().onEach { displayEventsTimeInUTC ->
            timeZone = if (displayEventsTimeInUTC) {
                ZoneId.ofOffset("UTC", ZoneOffset.UTC)
            } else {
                ZoneId.systemDefault()
            }
        }
        viewModelScope.launch {
            combine(defaultLocaleFlow, timeZoneFlow) { _, _ -> }
                .collect { load(forceRefresh = false) }
        }
        addCloseable(repository)
    }

    private suspend fun load(forceRefresh: Boolean) {
        _contentUiState.compareAndSet(ContentUiState.Error, ContentUiState.Loading)
        val eventById = repository.getEventById(eventId, forceRefresh = forceRefresh)
        val newUiState = withContext(Dispatchers.Default) { eventById.toContentUiState() }
        if (!forceRefresh && eventById.needsRefreshing) {
            refresh(fromUi = false)
        }
        _contentUiState.value = newUiState
    }

    fun refresh(fromUi: Boolean = true) {
        refreshing.value = if (fromUi) RefreshingState.RefreshingManually else RefreshingState.RefreshingAutomatically
        viewModelScope.launch {
            try {
                load(forceRefresh = true)
            } finally {
                refreshing.value = RefreshingState.NotRefreshing
            }
        }
    }

    @Composable
    fun formatTime(instant: Instant): String {
        LocalDefaultLocale.current
        return localeDependentState.eventTimeFormatter.format(instant.atZone(timeZone))
    }

    private fun DonkiRepository.EventById.toContentUiState() = ContentUiState.Loaded(
        type = event.type.getDisplayString(),
        dateTime = localeDependentState.eventTimeFormatter.format(event.time.atZone(timeZone)),
        event = event,
        linkedEvents = event.linkedEvents.mapNotNull { it.toPresentation(timeZone) },
    )

    private fun EventId.toPresentation(timeZone: ZoneId): LinkedEventPresentation? {
        val (type, time) = runCatching { parse() }.getOrElse {
            Log.e(TAG, it.message, it)
            null
        } ?: return null
        return LinkedEventPresentation(
            this,
            localeDependentState.eventTimeFormatter.format(time.atZone(timeZone)),
            type.getDisplayString()
        )
    }

    private fun EventType.getDisplayString(): String {
        return localeDependentState.eventTypesStrings.computeIfAbsent(this) { getString(displayStringResId) }
    }

    private fun getString(@StringRes resId: Int) = getApplication<Application>().getString(resId)

    @Immutable
    sealed interface ContentUiState {
        object Loading : ContentUiState
        data class Loaded(
            val type: String,
            val dateTime: String,
            val event: Event,
            val linkedEvents: List<LinkedEventPresentation>
        ) : ContentUiState
        object Error : ContentUiState
    }

    data class LinkedEventPresentation(
        val id: EventId,
        val dateTime: String,
        val type: String
    )
}
