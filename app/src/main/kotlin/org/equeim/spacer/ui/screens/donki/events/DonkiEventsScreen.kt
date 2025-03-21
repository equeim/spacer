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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.coroutines.flow.flowOf
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.common.NeedToRefreshState
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.ui.components.CARD_CONTENT_PADDING
import org.equeim.spacer.ui.components.RootScreenTopAppBar
import org.equeim.spacer.ui.components.ToolbarIcon
import org.equeim.spacer.ui.components.ToolbarIconWithBadge
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.DialogDestinationNavHost
import org.equeim.spacer.ui.screens.donki.BaseEventsList
import org.equeim.spacer.ui.screens.donki.BaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.DateSeparator
import org.equeim.spacer.ui.screens.donki.FiltersUiState
import org.equeim.spacer.ui.screens.donki.ListItem
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreen
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreen
import org.equeim.spacer.ui.screens.donki.rememberBaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.shouldShowFiltersAsDialog
import org.equeim.spacer.ui.screens.settings.SettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.FilterList
import org.equeim.spacer.ui.theme.NotificationsNone
import org.equeim.spacer.ui.theme.ScreenPreview
import org.equeim.spacer.ui.utils.rememberIntegerFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Parcelize
data object DonkiEventsScreen : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) = DonkiEventsScreen(navController, navHostEntries)
}

@Composable
private fun DonkiEventsScreen(
    navController: NavController<Destination>,
    navHostEntries: List<NavHostEntry<Destination>>
) {
    val model = viewModel<DonkiEventsScreenViewModel>()
    val filters = model.filtersUiState.collectAsStateWithLifecycle()
    val holder = rememberBaseEventsListStateHolder(
        items = model.pagingData.collectAsLazyPagingItems(),
        listState = rememberLazyListState(),
        filters = filters,
        getNeedToRefreshState = model::getNeedToRefreshState,
        allEventTypesAreDisabledErrorString = R.string.all_event_types_are_disabled,
        noEventsInDateRangeErrorString = R.string.no_events_in_date_range,
    )
    val eventsTimeZone = model.eventsTimeZone.collectAsStateWithLifecycle()
    val numberOfUnreadNotifications =
        model.numberOfUnreadNotifications.collectAsStateWithLifecycle()
    DonkiEventsScreen(
        holder = holder,
        filtersUiState = filters,
        updateFilters = model::updateFilters,
        eventsTimeZone = eventsTimeZone,
        numberOfUnreadNotifications = numberOfUnreadNotifications,
        navHostEntries = { navHostEntries },
        navigateToDetailsScreen = { navController.navigate(DonkiEventDetailsScreen(it)) },
        navigateToNotificationsScreen = { navController.navigate(DonkiNotificationsScreen) },
        navigateToSettingsScreen = { navController.navigate(SettingsScreen) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonkiEventsScreen(
    holder: BaseEventsListStateHolder,
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    numberOfUnreadNotifications: State<Int>,
    navHostEntries: () -> List<NavHostEntry<Destination>>,
    navigateToDetailsScreen: (EventId) -> Unit,
    navigateToNotificationsScreen: () -> Unit,
    navigateToSettingsScreen: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val snackbarHostState = remember { SnackbarHostState() }

    val dialogNavController = rememberNavController<Destination>(initialBackstack = emptyList())
    DialogDestinationNavHost(dialogNavController, navHostEntries)

    val showFiltersAsDialog = shouldShowFiltersAsDialog()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RootScreenTopAppBar(
                stringResource(R.string.space_weather_events),
                scrollBehavior,
                startActions = {
                    if (showFiltersAsDialog.value) {
                        ToolbarIcon(Icons.Filled.FilterList, R.string.filters) {
                            dialogNavController.navigate(EventFiltersDialog)
                        }
                    }
                },
                endActions = {
                    if (numberOfUnreadNotifications.value > 0) {
                        val formatter = rememberIntegerFormatter()
                        ToolbarIconWithBadge(
                            icon = Icons.Filled.NotificationsNone,
                            textId = R.string.notifications,
                            badgeText = { formatter.format(numberOfUnreadNotifications.value.toLong()) },
                            onClick = navigateToNotificationsScreen
                        )
                    } else {
                        ToolbarIcon(
                            icon = Icons.Filled.NotificationsNone,
                            textId = R.string.notifications,
                            onClick = navigateToNotificationsScreen
                        )
                    }
                    ToolbarIcon(Icons.Filled.Settings, R.string.filters, navigateToSettingsScreen)
                }
            )
        }
    ) { contentPadding ->
        BaseEventsList(
            holder = holder,

            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
            topAppBarScrollBehavior = scrollBehavior,

            filtersUiState = filtersUiState,
            updateFilters = updateFilters,
            allEventTypes = EventType.entries,
            eventTypeDisplayStringId = { it.displayStringResId },
            eventsTimeZone = eventsTimeZone,

            dialogNavController = dialogNavController,
            filtersDialogDestination = EventFiltersDialog,
            dateRangePickerDialogDestination = EventsDateRangePickerDialog,
            showFiltersAsDialog = showFiltersAsDialog,
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
                filters = filters,
                getNeedToRefreshState = { flowOf(NeedToRefreshState.DontNeedToRefresh) },
                allEventTypesAreDisabledErrorString = R.string.all_event_types_are_disabled,
                noEventsInDateRangeErrorString = R.string.no_events_in_date_range,
            ),
            filtersUiState = filters,
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            numberOfUnreadNotifications = remember { mutableStateOf(69) },
            navHostEntries = { emptyList() },
            navigateToDetailsScreen = {},
            navigateToNotificationsScreen = {},
            navigateToSettingsScreen = {}
        )
    }
}
