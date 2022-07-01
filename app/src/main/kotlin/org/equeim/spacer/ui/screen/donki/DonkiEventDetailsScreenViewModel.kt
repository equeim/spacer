package org.equeim.spacer.ui.screen.donki

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
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

    var state: UiState by mutableStateOf(UiState.Loading)
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
    }

    private suspend fun load(forceRefresh: Boolean) {
        state = when (val state = state) {
            is UiState.Loaded -> state.copy(refreshing = true)
            else -> UiState.Loading
        }
        val eventById = repository.getEventById(eventId, forceRefresh = forceRefresh)
        if (!forceRefresh && eventById.needsRefreshing) {
            refresh()
        }
        state = withContext(Dispatchers.Default) { eventById.toState(setRefreshing = true) }
    }

    fun refresh() {
        viewModelScope.launch { load(forceRefresh = true) }
    }

    fun formatTime(instant: Instant): String {
        return localeDependentState.eventTimeFormatter.format(instant.atZone(timeZone))
    }

    private fun DonkiRepository.EventById.toState(setRefreshing: Boolean) = UiState.Loaded(
        type = event.type.getDisplayString(),
        dateTime = localeDependentState.eventTimeFormatter.format(event.time.atZone(timeZone)),
        event = event,
        linkedEvents = event.linkedEvents.mapNotNull { it.toPresentation(timeZone) },
        refreshing = if (setRefreshing) needsRefreshing else false
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
    sealed interface UiState {
        object Loading : UiState
        data class Loaded(
            val type: String,
            val dateTime: String,
            val event: Event,
            val linkedEvents: List<LinkedEventPresentation>,
            val refreshing: Boolean
        ) : UiState

        object Error : UiState
    }

    data class LinkedEventPresentation(
        val id: EventId,
        val dateTime: String,
        val type: String
    )
}
