// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.common.NeedToRefreshState
import org.equeim.spacer.donki.data.notifications.DonkiNotificationsRepository
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.donki.data.notifications.NotificationType.CoronalMassEjection
import org.equeim.spacer.donki.data.notifications.NotificationType.GeomagneticStorm
import org.equeim.spacer.donki.data.notifications.NotificationType.HighSpeedStream
import org.equeim.spacer.donki.data.notifications.NotificationType.InterplanetaryShock
import org.equeim.spacer.donki.data.notifications.NotificationType.MagnetopauseCrossing
import org.equeim.spacer.donki.data.notifications.NotificationType.RadiationBeltEnhancement
import org.equeim.spacer.donki.data.notifications.NotificationType.Report
import org.equeim.spacer.donki.data.notifications.NotificationType.SolarEnergeticParticle
import org.equeim.spacer.donki.data.notifications.NotificationType.SolarFlare
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.getDonkiNotificationsRepositoryInstance
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

class DonkiNotificationsScreenViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    init {
        Log.d(TAG, "DonkiNotificationsScreenViewModel() called")
    }

    private val settings = AppSettings(application)
    private val repository = getDonkiNotificationsRepositoryInstance(application)

    private class Formatters(locale: Locale, val zone: ZoneId) {
        val notificationDateFormatter = createEventDateFormatter(locale, zone)
        val notificationTimeFormatter = createEventTimeFormatter(locale, zone)
    }

    val filtersUiState: StateFlow<FiltersUiState<NotificationType>> =
        savedStateHandle.getStateFlow(
            FILTERS_KEY, FiltersUiState(
                types = NotificationType.entries,
                dateRange = null,
                dateRangeEnabled = false
            )
        )

    fun updateFilters(filtersUiState: FiltersUiState<NotificationType>) {
        Log.d(TAG, "updateFilters() called with: filtersUiState = $filtersUiState")
        savedStateHandle[FILTERS_KEY] = filtersUiState
    }

    private val notificationFilters: StateFlow<DonkiNotificationsRepository.Filters> =
        filtersUiState.map { it.toEventFilters() }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly, DonkiNotificationsRepository.Filters(
                    types = NotificationType.entries,
                    dateRange = null
                )
            )

    val numberOfUnreadNotifications: StateFlow<Int> = repository.getNumberOfUnreadNotifications()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val pagingData: Flow<PagingData<ListItem>>

    val notificationsTimeZone: StateFlow<ZoneId?>

    init {
        val defaultLocaleFlow =
            application.defaultLocaleFlow()
                .stateIn(viewModelScope, SharingStarted.Eagerly, application.defaultLocale)

        notificationsTimeZone = combine(
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

        val (pager, closeable) = repository.getNotificationSummariesPager(notificationFilters)
        addCloseable(closeable)

        val basePagingData = pager.flow.cachedIn(viewModelScope)
        val notificationTypesStringsCacheFlow =
            defaultLocaleFlow.map { ConcurrentHashMap<NotificationType, String>() }
        val formattersFlow =
            combine(defaultLocaleFlow, notificationsTimeZone.filterNotNull(), ::Formatters)
        val pagingDataCoroutineScope =
            CoroutineScope(SupervisorJob(viewModelScope.coroutineContext.job) + Dispatchers.Default)
        pagingData = combine(
            basePagingData,
            notificationTypesStringsCacheFlow,
            formattersFlow,
        ) { pagingData, notificationTypesStringsCache, formatters ->
            pagingData.toListItems(notificationTypesStringsCache, formatters)
        }.cachedIn(pagingDataCoroutineScope)
    }

    private fun PagingData<CachedNotificationSummary>.toListItems(
        notificationTypesStringsCache: ConcurrentHashMap<NotificationType, String>,
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
                            formatters.notificationDateFormatter.format(after.eventSummary.time)
                        )
                    }

                    else -> null
                }
            }
            .map {
                when (it) {
                    is EventSummaryWithDayOfMonth -> it.eventSummary.toPresentation(
                        notificationTypesStringsCache,
                        formatters.notificationTimeFormatter
                    )

                    else -> it as DateSeparator
                }
            }
    }

    private data class EventSummaryWithDayOfMonth(
        val eventSummary: CachedNotificationSummary,
        val dayOfMonth: Int,
    )

    private fun CachedNotificationSummary.toPresentation(
        notificationTypesStringsCache: ConcurrentHashMap<NotificationType, String>,
        eventTimeFormatter: DateTimeFormatter,
    ): NotificationPresentation {
        return NotificationPresentation(
            id = id,
            time = eventTimeFormatter.format(time),
            title = title ?: getTypeDisplayString(notificationTypesStringsCache),
            subtitle = subtitle,
            read = read
        )
    }

    private fun CachedNotificationSummary.getTypeDisplayString(notificationTypesStringsCache: ConcurrentHashMap<NotificationType, String>): String {
        return notificationTypesStringsCache.computeIfAbsent(type) { getString(type.displayStringResId) }
    }

    /**
     * Returned flow catches exceptions
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getNeedToRefreshState(): Flow<NeedToRefreshState> =
        notificationFilters.flatMapLatest(repository::getNeedToRefreshState)

    /**
     * Catches exceptions
     */
    fun markAllNotificationsAsRead() {
        Log.d(TAG, "markAllNotificationsAsRead() called")
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    data class NotificationPresentation(
        val id: NotificationId,
        val time: String,
        val title: String,
        val subtitle: String?,
        val read: Boolean,
    ) : ListItem

    companion object {
        private const val TAG = "DonkiNotificationsScreenViewModel"
        private const val FILTERS_KEY = "filters"

        val NotificationType.displayStringResId: Int
            @StringRes get() = when (this) {
                Report -> R.string.notification_report
                CoronalMassEjection -> R.string.coronal_mass_ejection
                GeomagneticStorm -> R.string.geomagnetic_storm
                InterplanetaryShock -> R.string.interplanetary_shock
                SolarFlare -> R.string.solar_flare
                SolarEnergeticParticle -> R.string.solar_energetic_particle
                MagnetopauseCrossing -> R.string.magnetopause_crossing
                RadiationBeltEnhancement -> R.string.radiation_belt_enhancement
                HighSpeedStream -> R.string.high_speed_stream
            }

        private fun FiltersUiState<NotificationType>.toEventFilters() =
            DonkiNotificationsRepository.Filters(types, if (dateRangeEnabled) dateRange else null)
    }
}
