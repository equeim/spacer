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
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "DonkiEventDetailsScreenViewModel"
private val REFRESH_INDICATOR_DELAY = 300.milliseconds

class DonkiEventDetailsScreenViewModel(private val eventId: EventId, application: Application) :
    AndroidViewModel(application) {

    private val repository = DonkiRepository(application)

    private var loadingJob = AtomicReference<Job>(null)

    private enum class LoadingType {
        Automatic, Manual
    }

    private val loadingType = MutableStateFlow<LoadingType?>(null)

    private val _contentState = MutableStateFlow<ContentState>(ContentState.LoadingPlaceholder)
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

    private class LocaleDependentState {
        val eventTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
        val eventTypesStrings = ConcurrentHashMap<EventType, String>()
    }

    @Volatile
    private lateinit var localeDependentState: LocaleDependentState

    @Volatile
    private lateinit var timeZone: ZoneId

    init {
        addCloseable(repository)
        val defaultLocaleFlow = application.defaultLocaleFlow().onEach {
            localeDependentState = LocaleDependentState()
        }
        val timeZoneFlow = AppSettings(application).displayEventsTimeInUTC.flow()
            .onEach { displayEventsTimeInUTC ->
                timeZone = if (displayEventsTimeInUTC) {
                    ZoneId.ofOffset("UTC", ZoneOffset.UTC)
                } else {
                    ZoneId.systemDefault()
                }
            }
        combine(defaultLocaleFlow, timeZoneFlow) { _, _ -> }
            .onEach { load(LoadingType.Automatic) }
            .launchIn(viewModelScope)
    }

    private suspend fun load(type: LoadingType) {
        Log.d(TAG, "load() called with: type = $type")
        loadingJob.getAndSet(null)?.let {
            Log.d(TAG, "load: cancelling loading job $it with type ${loadingType.value}")
            loadingType.value = null
            it.cancelAndJoin()
        }
        Log.d(TAG, "load: starting loading job")
        viewModelScope.launch {
            _contentState.compareAndSet(
                ContentState.ErrorPlaceholder,
                ContentState.LoadingPlaceholder
            )
            when (type) {
                LoadingType.Automatic -> {
                    val event = repository.getEventById(eventId, forceRefresh = false)
                    _contentState.value = event.toContentState()
                    if (event.needsRefreshing) {
                        _contentState.value =
                            repository.getEventById(eventId, forceRefresh = true).toContentState()
                    }
                }
                LoadingType.Manual -> {
                    _contentState.value =
                        repository.getEventById(eventId, forceRefresh = true).toContentState()
                }
            }
        }.also { job ->
            Log.d(TAG, "load: started loading job $job")
            loadingJob.set(job)
            loadingType.value = type
            job.invokeOnCompletion {
                Log.d(TAG, "load: completed loading job $job")
                if (loadingJob.compareAndSet(job, null)) {
                    loadingType.value = null
                }
            }
        }
    }

    private suspend fun DonkiRepository.EventById.toContentState(): ContentState.EventData =
        withContext(Dispatchers.Default) {
            ContentState.EventData(
                type = event.type.getDisplayString(),
                dateTime = localeDependentState.eventTimeFormatter.format(event.time.atZone(timeZone)),
                event = event,
                linkedEvents = event.linkedEvents.mapNotNull { it.toPresentation(timeZone) },
            )
        }

    fun refresh() {
        viewModelScope.launch {
            load(LoadingType.Manual)
        }
    }

    @Composable
    fun formatTime(instant: Instant): String {
        LocalDefaultLocale.current
        return localeDependentState.eventTimeFormatter.format(instant.atZone(timeZone))
    }

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
        return localeDependentState.eventTypesStrings.computeIfAbsent(this) {
            getString(
                displayStringResId
            )
        }
    }

    private fun getString(@StringRes resId: Int) = getApplication<Application>().getString(resId)

    @Immutable
    sealed interface ContentState {
        object LoadingPlaceholder : ContentState
        data class EventData(
            val type: String,
            val dateTime: String,
            val event: Event,
            val linkedEvents: List<LinkedEventPresentation>
        ) : ContentState

        object ErrorPlaceholder : ContentState
    }

    data class LinkedEventPresentation(
        val id: EventId,
        val dateTime: String,
        val type: String
    )
}
