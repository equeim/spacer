// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.AppSettings
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.screens.donki.displayStringResId
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DonkiEventDetailsScreenViewModel"

class DonkiEventDetailsScreenViewModel(private val eventId: EventId, application: Application) :
    AndroidViewModel(application) {

    private val repository = DonkiRepository(application)

    var contentUiState: ContentUiState by mutableStateOf(ContentUiState.Loading)
        private set
    var refreshing: Boolean by mutableStateOf(false)
        private set

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
        if (contentUiState is ContentUiState.Error) {
            contentUiState = ContentUiState.Loading
        }
        val eventById = repository.getEventById(eventId, forceRefresh = forceRefresh)
        if (!forceRefresh && eventById.needsRefreshing) {
            refresh()
        }
        contentUiState = withContext(Dispatchers.Default) { eventById.toContentUiState() }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing = true
            try {
                load(forceRefresh = true)
            } finally {
                refreshing = false
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
