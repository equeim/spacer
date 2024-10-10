// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.TerminalSeparatorType
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import org.equeim.spacer.AppSettings
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.DonkiEventsRepository
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjectionSummary
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStormSummary
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShockSummary
import org.equeim.spacer.donki.data.events.network.json.SolarFlareSummary
import org.equeim.spacer.ui.screens.donki.DateSeparator
import org.equeim.spacer.ui.screens.donki.FiltersUiState
import org.equeim.spacer.ui.screens.donki.ListItem
import org.equeim.spacer.ui.utils.createEventDateFormatter
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

private const val TAG = "DonkiEventsScreenViewModel"

class DonkiEventsScreenViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) :
    AndroidViewModel(application) {
    init {
        Log.d(TAG, "DonkiEventsScreenViewModel() called")
    }

    private val settings = AppSettings(application)
    private val repository = DonkiEventsRepository(settings.customNasaApiKeyOrNull(), application)

    private class Formatters(locale: Locale, val zone: ZoneId) {
        val eventDateFormatter = createEventDateFormatter(locale, zone)
        val eventTimeFormatter = createEventTimeFormatter(locale, zone)
    }

    val filtersUiState: StateFlow<FiltersUiState<EventType>> = savedStateHandle.getStateFlow(
        FILTERS_KEY,
        FiltersUiState(types = EventType.entries, dateRange = null, dateRangeEnabled = false)
    )

    fun updateFilters(filtersUiState: FiltersUiState<EventType>) {
        savedStateHandle[FILTERS_KEY] = filtersUiState
    }

    private val eventFilters: StateFlow<DonkiEventsRepository.Filters> =
        filtersUiState.map { it.toEventFilters() }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DonkiEventsRepository.Filters(types = EventType.entries, dateRange = null)
        )

    val pagingData: Flow<PagingData<ListItem>>

    val eventsTimeZone: StateFlow<ZoneId?>

    init {
        addCloseable(repository)

        val defaultLocaleFlow =
            application.defaultLocaleFlow()
                .stateIn(viewModelScope, SharingStarted.Eagerly, application.defaultLocale)

        eventsTimeZone = combine(
            defaultLocaleFlow,
            application.defaultTimeZoneFlow(),
            settings.displayEventsTimeInUTC.flow()
        ) { locale, defaultZone, displayEventsTimeInUTC ->
            Log.d(
                TAG,
                "locale = $locale, defaultZone = $defaultZone, displayEventsTimeInUTC = $displayEventsTimeInUTC"
            )
            determineEventTimeZone(defaultZone, displayEventsTimeInUTC)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val basePagingData =
            repository.getEventSummariesPager(eventFilters).flow.cachedIn(viewModelScope)
        val eventTypesStringsCacheFlow =
            defaultLocaleFlow.map { ConcurrentHashMap<EventType, String>() }
        val formattersFlow =
            combine(defaultLocaleFlow, eventsTimeZone.filterNotNull(), ::Formatters)
        val pagingDataCoroutineScope =
            CoroutineScope(SupervisorJob(viewModelScope.coroutineContext.job) + Dispatchers.Default)
        pagingData = combine(
            basePagingData,
            eventTypesStringsCacheFlow,
            formattersFlow,
        ) { pagingData, eventTypesStringsCache, formatters ->
            pagingData.toListItems(eventTypesStringsCache, formatters)
        }.cachedIn(pagingDataCoroutineScope)
    }

    private fun PagingData<EventSummary>.toListItems(
        eventTypesStringsCache: ConcurrentHashMap<EventType, String>,
        formatters: Formatters,
    ): PagingData<ListItem> {
        return map {
            EventSummaryWithDayOfMonth(it, it.time.atZone(formatters.zone).dayOfMonth)
        }
            .insertSeparators(TerminalSeparatorType.SOURCE_COMPLETE) { before, after ->
                when {
                    after == null -> null
                    before == null || before.dayOfMonth != after.dayOfMonth -> {
                        DateSeparator(
                            after.eventSummary.time.epochSecond,
                            formatters.eventDateFormatter.format(after.eventSummary.time)
                        )
                    }

                    else -> null
                }
            }
            .map {
                when (it) {
                    is EventSummaryWithDayOfMonth -> it.eventSummary.toPresentation(
                        eventTypesStringsCache,
                        formatters.eventTimeFormatter
                    )

                    else -> it as DateSeparator
                }
            }
    }

    private data class EventSummaryWithDayOfMonth(
        val eventSummary: EventSummary,
        val dayOfMonth: Int,
    )

    private fun EventSummary.toPresentation(
        eventTypesStringsCache: ConcurrentHashMap<EventType, String>,
        eventTimeFormatter: DateTimeFormatter,
    ): EventPresentation {
        return EventPresentation(
            id = id,
            type = getTypeDisplayString(eventTypesStringsCache),
            time = eventTimeFormatter.format(time),
            detailsSummary = getDetailsSummary()
        )
    }

    private fun EventSummary.getTypeDisplayString(eventTypesStringsCache: ConcurrentHashMap<EventType, String>): String {
        return eventTypesStringsCache.computeIfAbsent(type) { getString(type.displayStringResId) }
    }

    private fun EventSummary.getDetailsSummary(): String? {
        return when (this) {
            is GeomagneticStormSummary -> kpIndex?.let { getString(R.string.gst_kp_index, it) }
            is InterplanetaryShockSummary -> location
            is SolarFlareSummary -> classType
            is CoronalMassEjectionSummary -> if (isEarthShockPredicted) getString(R.string.cme_earth_impact) else null
            else -> null
        }
    }

    suspend fun isLastWeekNeedsRefreshing(): Boolean {
        return repository.isLastWeekNeedsRefreshing(eventFilters.value)
    }

    data class EventPresentation(
        val id: EventId,
        val type: String,
        val time: String,
        val detailsSummary: String?,
    ) : ListItem

    companion object {
        private const val FILTERS_KEY = "filters"

        val EventType.displayStringResId: Int
            @StringRes get() = when (this) {
                EventType.CoronalMassEjection -> R.string.coronal_mass_ejection
                EventType.GeomagneticStorm -> R.string.geomagnetic_storm
                EventType.InterplanetaryShock -> R.string.interplanetary_shock
                EventType.SolarFlare -> R.string.solar_flare
                EventType.SolarEnergeticParticle -> R.string.solar_energetic_particle
                EventType.MagnetopauseCrossing -> R.string.magnetopause_crossing
                EventType.RadiationBeltEnhancement -> R.string.radiation_belt_enhancement
                EventType.HighSpeedStream -> R.string.high_speed_stream
            }

        private fun FiltersUiState<EventType>.toEventFilters() =
            DonkiEventsRepository.Filters(types, if (dateRangeEnabled) dateRange else null)
    }
}