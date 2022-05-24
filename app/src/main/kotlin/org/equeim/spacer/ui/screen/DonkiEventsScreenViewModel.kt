package org.equeim.spacer.ui.screen

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.R
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
    private val eventsUseCase = DonkiGetEventsSummariesUseCase(DonkiRepository(application))

    var uiState: UiState by mutableStateOf(UiState.Loading)
        private set

    private val eventTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val eventDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    private val timeZoneFormatter =
        DateTimeFormatterBuilder().appendZoneText(TextStyle.SHORT).toFormatter()

    private val eventTitles = ConcurrentHashMap<EventType, String>()

    init {
        viewModelScope.launch { getEventsForLastWeek() }
    }

    private suspend fun getEventsForLastWeek() {
        withContext(Dispatchers.Main) {
            uiState = UiState.Loading
        }
        withContext(Dispatchers.Default) {
            try {
                val displayLocalTime = true
                val timeZone = if (displayLocalTime) {
                    ZoneId.systemDefault()
                } else {
                    ZoneId.ofOffset("UTC", ZoneOffset.UTC)
                }
                val timeZoneName =
                    timeZoneFormatter.format(ZonedDateTime.now().withZoneSameInstant(timeZone))
                val groupedEvents = eventsUseCase.getEventsSummariesGroupedByDateForLastWeek(timeZone)
                val presentations = groupedEvents.map { (date, events) ->
                    EventsGroup(eventDateFormatter.format(date), events.map { it.toPresentation() })
                }
                withContext(Dispatchers.Main) {
                    uiState = UiState.Loaded(presentations, timeZoneName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "getEventsForLastWeek: failed to get events", e)
                withContext(Dispatchers.Main) {
                    uiState = UiState.Error
                }
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
        return eventTitles.computeIfAbsent(type) {
            getString(when (type) {
                EventType.CoronalMassEjection -> R.string.coronal_mass_ejection
                EventType.GeomagneticStorm -> R.string.geomagnetic_storm
                EventType.InterplanetaryShock -> R.string.interplanetary_shock
                EventType.SolarFlare -> R.string.solar_flare
                EventType.SolarEnergeticParticle -> R.string.solar_energetic_particle
                EventType.MagnetopauseCrossing -> R.string.magnetopause_crossing
                EventType.RadiationBeltEnhancement -> R.string.radiation_belt_enhancement
                EventType.HighSpeedStream -> R.string.high_speed_stream
            })
        }
    }

    private fun getString(@StringRes resId: Int) = getApplication<Application>().getString(resId)

    data class EventPresentation(
        val id: String,
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
