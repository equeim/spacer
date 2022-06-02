package org.equeim.spacer.ui.screen.donki

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.repository.DonkiRepository
import org.equeim.spacer.donki.domain.DonkiGetEventsSummariesUseCase
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DonkiEventsScreenViewModel"

class DonkiEventsScreenViewModel(application: Application) : AndroidViewModel(application) {
    init {
        Log.d(TAG, "DonkiEventsScreenViewModel() called")
        LocalViewModelStoreOwner
    }

    private val repository = DonkiRepository(application)
    private val eventsUseCase = DonkiGetEventsSummariesUseCase(repository)
    private val settings = AppSettings(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> by ::_uiState

    private val eventTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val eventDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    private val timeZoneFormatter =
        DateTimeFormatterBuilder().appendZoneText(TextStyle.SHORT).toFormatter()

    private val eventTitles = ConcurrentHashMap<EventType, String>()

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            settings.displayEventsTimeInUTC.flow()
                .mapLatest { displayEventsTimeInUTC ->
                    // Wait until uiState has subscribers before reloading
                    _uiState.subscriptionCount.first { it > 0 }
                    displayEventsTimeInUTC
                }
                // In case displayEventsTimeInUTC changed back to the same value before uiState acquired subscribers
                .distinctUntilChanged()
                .collectLatest { displayEventsTimeInUTC ->
                    showEventsForLastWeek(displayEventsTimeInUTC)
                }
        }
    }

    private suspend fun showEventsForLastWeek(displayEventsTimeInUTC: Boolean) {
        Log.d(
            TAG,
            "showEventsForLastWeek() called with: displayEventsTimeInUTC = $displayEventsTimeInUTC"
        )
        _uiState.value = UiState.Loading
        withContext(Dispatchers.Default) {
            try {
                val timeZone = if (displayEventsTimeInUTC) {
                    ZoneId.ofOffset("UTC", ZoneOffset.UTC)
                } else {
                    ZoneId.systemDefault()
                }
                val timeZoneName =
                    timeZoneFormatter.format(ZonedDateTime.now().withZoneSameInstant(timeZone))
                val groupedEvents =
                    eventsUseCase.getEventsSummariesGroupedByDateForLastWeek(timeZone)
                val presentations = groupedEvents.map { (date, events) ->
                    EventsGroup(eventDateFormatter.format(date), events.map { it.toPresentation() })
                }
                _uiState.value = UiState.Loaded(presentations, timeZoneName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "getEventsForLastWeek: failed to get events", e)
                _uiState.value = UiState.Error
            }
        }
    }

    private fun DonkiGetEventsSummariesUseCase.EventSummaryWithZonedTime.toPresentation(): EventPresentation {
        return EventPresentation(
            event.id,
            title = event.getTitle(),
            time = eventTimeFormatter.format(zonedTime)
        )
    }

    private fun EventSummary.getTitle(): String {
        return eventTitles.computeIfAbsent(type) { getString(type.getTitleResId()) }
    }

    private fun getString(@StringRes resId: Int) = getApplication<Application>().getString(resId)

    data class EventPresentation(
        val id: EventId,
        val title: String,
        val time: String
    )

    data class EventsGroup(
        val date: String,
        val events: List<EventPresentation>
    )

    @Immutable
    sealed interface UiState {
        object Loading : UiState
        data class Loaded(
            val eventGroups: List<EventsGroup>,
            val timeZoneName: String
        ) : UiState

        object Error : UiState
    }
}
