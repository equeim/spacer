// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.current
import org.equeim.spacer.ui.screens.donki.BaseEventFiltersDialogContent
import org.equeim.spacer.ui.screens.donki.BaseEventFiltersSideSheet
import org.equeim.spacer.ui.screens.donki.DateRangePickerDialogContent
import org.equeim.spacer.ui.screens.donki.FiltersUiState
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreenViewModel.Companion.displayStringResId
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale



@Parcelize
data object NotificationFiltersDialog : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) {
        val model: DonkiNotificationsScreenViewModel = viewModel(viewModelStoreOwner = parentNavHostEntries!!.current)
        val filters = model.filtersUiState.collectAsStateWithLifecycle()
        val eventsTimeZone = model.notificationsTimeZone.collectAsStateWithLifecycle()
        BaseEventFiltersDialogContent(
            filtersUiState = filters,
            updateFilters = model::updateFilters,
            allEventTypes = NotificationType.entries,
            eventTypeDisplayStringId = { it.displayStringResId },
            eventsTimeZone = eventsTimeZone,
            closeDialog = navController::pop,
            showDateRangeDialog = { navController.navigate(NotificationsDateRangePickerDialog) },
        )
    }
}

@Parcelize
data object NotificationsDateRangePickerDialog : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) {
        val model: DonkiNotificationsScreenViewModel = viewModel(viewModelStoreOwner = parentNavHostEntries!!.current)
        val initialFilters: FiltersUiState<NotificationType> by model.filtersUiState.collectAsStateWithLifecycle()
        val eventsTimeZone: ZoneId? by model.notificationsTimeZone.collectAsStateWithLifecycle()
        eventsTimeZone?.let {
            DateRangePickerDialogContent(
                initialFilters = initialFilters,
                updateFilters = model::updateFilters,
                eventsTimeZone = it,
                closeDialog = navController::pop
            )
        }
    }
}

@Preview
@Composable
private fun NotificationFiltersDateRangePickerDialogPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        DateRangePickerDialogContent<EventType>(
            initialFilters = FiltersUiState(EventType.entries, null, true),
            updateFilters = {},
            eventsTimeZone = ZoneId.systemDefault(),
            closeDialog = {}
        )
    }
}

@Preview
@Composable
private fun NotificationFiltersSideSheetPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        BaseEventFiltersSideSheet(
            filtersUiState = remember {
                mutableStateOf(
                    FiltersUiState(
                        types = EventType.entries - EventType.GeomagneticStorm,
                        dateRange = DateRange(
                            firstDayInstant = LocalDate.now().minusDays(5).atStartOfDay(ZoneId.systemDefault())
                                .toInstant(),
                            instantAfterLastDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        ),
                        dateRangeEnabled = true
                    )
                )
            },
            updateFilters = {},
            allEventTypes = EventType.entries,
            eventTypeDisplayStringId = { it.displayStringResId },
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            showDateRangeDialog = {}
        )
    }
}

@Preview
@Composable
private fun NotificationFiltersDialogPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        BaseEventFiltersDialogContent(
            filtersUiState = remember {
                mutableStateOf(
                    FiltersUiState(
                        types = EventType.entries - EventType.GeomagneticStorm,
                        dateRange = DateRange(
                            firstDayInstant = LocalDate.now().minusDays(5).atStartOfDay(ZoneId.systemDefault())
                                .toInstant(),
                            instantAfterLastDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        ),
                        dateRangeEnabled = true
                    )
                )
            },
            updateFilters = {},
            allEventTypes = EventType.entries,
            eventTypeDisplayStringId = { it.displayStringResId },
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            closeDialog = {},
            showDateRangeDialog = {},
        )
    }
}
