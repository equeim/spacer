// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.common.NeedToRefreshState
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.ui.components.CARD_CONTENT_PADDING
import org.equeim.spacer.ui.components.IconButtonWithTooltip
import org.equeim.spacer.ui.components.RootScreenTopAppBar
import org.equeim.spacer.ui.components.ScrollableFloatingActionButtonWithTooltip
import org.equeim.spacer.ui.components.rememberFloatingActionButtonScrollBehavior
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.BaseEventsList
import org.equeim.spacer.ui.screens.donki.BaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.DateRangePickerDialog
import org.equeim.spacer.ui.screens.donki.DateSeparator
import org.equeim.spacer.ui.screens.donki.EventFiltersBottomSheet
import org.equeim.spacer.ui.screens.donki.EventFiltersSideSheet
import org.equeim.spacer.ui.screens.donki.FiltersUiState
import org.equeim.spacer.ui.screens.donki.ListItem
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreen
import org.equeim.spacer.ui.screens.donki.rememberBaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.shouldShowFiltersAsBottomSheet
import org.equeim.spacer.ui.screens.settings.SettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.FilterList
import org.equeim.spacer.ui.theme.ScreenPreview
import org.equeim.spacer.ui.utils.removeStart
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonkiEventsScreen(
    navController: NavController<Destination>,
    bottomAppBarScrollBehavior: BottomAppBarScrollBehavior,
    scrollToTopEvents: Flow<Unit>
) {
    val model = viewModel<DonkiEventsScreenViewModel>()
    val filters = model.filtersUiState.collectAsStateWithLifecycle()
    val holder = rememberBaseEventsListStateHolder(
        items = model.pagingData.collectAsLazyPagingItems(),
        listState = rememberLazyListState(),
        scrollToTopEvents = scrollToTopEvents,
        filters = filters,
        getNeedToRefreshState = model::getNeedToRefreshState,
        allEventTypesAreDisabledErrorString = R.string.all_event_types_are_disabled,
        noEventsInDateRangeErrorString = R.string.no_events_in_date_range,
    )
    val eventsTimeZone = model.eventsTimeZone.collectAsStateWithLifecycle()
    DonkiEventsScreen(
        holder = holder,
        filtersUiState = filters,
        updateFilters = model::updateFilters,
        eventsTimeZone = eventsTimeZone,
        navigateToDetailsScreen = { navController.navigate(DonkiEventDetailsScreen(it)) },
        navigateToSettingsScreen = { navController.navigate(SettingsScreen) },
        bottomAppBarScrollBehavior = bottomAppBarScrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonkiEventsScreen(
    holder: BaseEventsListStateHolder,
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    navigateToDetailsScreen: (EventId) -> Unit,
    navigateToSettingsScreen: () -> Unit,
    bottomAppBarScrollBehavior: BottomAppBarScrollBehavior
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    val showFiltersAsBottomSheet = shouldShowFiltersAsBottomSheet()
    var showFiltersBottomSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(null) {
        snapshotFlow { showFiltersAsBottomSheet.value }.collect {
            if (!it && showFiltersBottomSheet) {
                showFiltersBottomSheet = false
            }
        }
    }
    var showDateRangeDialog by rememberSaveable { mutableStateOf(false) }

    val fabScrollBehavior = rememberFloatingActionButtonScrollBehavior()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RootScreenTopAppBar(
                title = stringResource(R.string.space_weather_events),
                scrollBehavior = topAppBarScrollBehavior,
                endActions = {
                    IconButtonWithTooltip(
                        icon = Icons.Filled.Settings,
                        textId = R.string.filters,
                        onClick = navigateToSettingsScreen
                    )
                }
            )
        },
        floatingActionButton = {
            if (showFiltersAsBottomSheet.value) {
                ScrollableFloatingActionButtonWithTooltip(
                    onClick = { showFiltersBottomSheet = true },
                    icon = Icons.Filled.FilterList,
                    tooltipText = R.string.filters,
                    scrollBehavior = fabScrollBehavior
                )
            }
        }
    ) { contentPadding ->
        BaseEventsList(
            holder = holder,

            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
            mainContentNestedScrollConnections = listOf(
                topAppBarScrollBehavior.nestedScrollConnection,
                bottomAppBarScrollBehavior.nestedScrollConnection,
                fabScrollBehavior.nestedScrollConnection
            ),

            filtersSideSheet = {
                if (!showFiltersAsBottomSheet.value) {
                    EventFiltersSideSheet(
                        contentPadding = contentPadding.removeStart(),
                        filtersUiState = filtersUiState,
                        updateFilters = updateFilters,
                        allEventTypes = EventType.entries,
                        eventTypeDisplayStringId = { it.displayStringResId },
                        eventsTimeZone = eventsTimeZone,
                        showDateRangeDialog = { showDateRangeDialog = true }
                    )
                }
            },

            listItemKeyProvider = ListItem::lazyListKey,
            listContentTypeProvider = ListItem::lazyListContentType,
        ) { item ->
            item as DonkiEventsScreenViewModel.EventPresentation

            ElevatedCard(
                onClick = { navigateToDetailsScreen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(2.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        Dimens.SpacingSmall,
                        Alignment.CenterVertically
                    ),
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(CARD_CONTENT_PADDING)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (item.detailsSummary != null) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            Text(
                                text = item.detailsSummary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFiltersBottomSheet) {
        EventFiltersBottomSheet(
            filtersUiState = filtersUiState,
            updateFilters = updateFilters,
            allEventTypes = EventType.entries,
            eventTypeDisplayStringId = { it.displayStringResId },
            eventsTimeZone = eventsTimeZone,
            onDismissRequest = { showFiltersBottomSheet = false },
            showDateRangeDialog = { showDateRangeDialog = true }
        )
    }
    if (showDateRangeDialog) {
        eventsTimeZone.value?.let { zone ->
            DateRangePickerDialog(
                initialDateRange = filtersUiState.value.dateRange,
                updateDateRange = { updateFilters(filtersUiState.value.copy(dateRange = it)) },
                eventsTimeZone = zone,
                onDismissRequest = { showDateRangeDialog = false }
            )
        }
    }
}

private val ListItem.lazyListKey: Any
    get() = when (this) {
        is DateSeparator -> date
        else -> (this as DonkiEventsScreenViewModel.EventPresentation).id.stringValue
    }

private enum class ContentType {
    DateSeparator,
    Event,
    EventWithDetails
}

private val ListItem.lazyListContentType: ContentType
    get() = when (this) {
        is DateSeparator -> ContentType.DateSeparator
        else -> if ((this as DonkiEventsScreenViewModel.EventPresentation).detailsSummary != null) {
            ContentType.EventWithDetails
        } else {
            ContentType.Event
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun DonkiEventsScreenPreview() {
    ScreenPreview {
        val filters = remember {
            mutableStateOf(
                FiltersUiState(
                    types = EventType.entries,
                    dateRange = null,
                    dateRangeEnabled = false
                )
            )
        }
        val items = flowOf(
            PagingData.from(
                listOf(
                    DateSeparator(LocalDate.now().toString()),
                    DonkiEventsScreenViewModel.EventPresentation(
                        id = EventId("1"),
                        title = stringResource(
                            R.string.event_title_in_list,
                            LocalTime.now().withNano(0).toString(),
                            stringResource(EventType.SolarEnergeticParticle.displayStringResId)
                        ),
                        detailsSummary = null,
                    ),
                    DonkiEventsScreenViewModel.EventPresentation(
                        id = EventId("2"),
                        title = stringResource(
                            R.string.event_title_in_list,
                            LocalTime.now().withNano(0).toString(),
                            stringResource(EventType.CoronalMassEjection.displayStringResId)
                        ),
                        detailsSummary = stringResource(R.string.earch_impact_predicted_glancing),
                    )
                ),
            )
        ).collectAsLazyPagingItems()
        DonkiEventsScreen(
            holder = rememberBaseEventsListStateHolder(
                items = items,
                listState = rememberLazyListState(),
                scrollToTopEvents = remember { emptyFlow() },
                filters = filters,
                getNeedToRefreshState = { flowOf(NeedToRefreshState.DontNeedToRefresh) },
                allEventTypesAreDisabledErrorString = R.string.all_event_types_are_disabled,
                noEventsInDateRangeErrorString = R.string.no_events_in_date_range,
            ),
            filtersUiState = filters,
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            navigateToDetailsScreen = {},
            navigateToSettingsScreen = {},
            bottomAppBarScrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
        )
    }
}
