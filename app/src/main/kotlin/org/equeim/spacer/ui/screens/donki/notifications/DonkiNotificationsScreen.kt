// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.olshevski.navigation.reimagined.pop
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.coroutines.flow.flowOf
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.components.ElevatedCardWithPadding
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.components.ToolbarIcon
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.DialogDestinationNavHost
import org.equeim.spacer.ui.screens.donki.BaseEventsList
import org.equeim.spacer.ui.screens.donki.BaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.DateSeparator
import org.equeim.spacer.ui.screens.donki.FiltersUiState
import org.equeim.spacer.ui.screens.donki.ListItem
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.notifications.details.NotificationDetailsScreen
import org.equeim.spacer.ui.screens.donki.rememberBaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.shouldShowFiltersAsDialog
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.FilterList
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@Parcelize
data object DonkiNotificationsScreen : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        parentNavHostEntry: NavHostEntry<Destination>?
    ) =
        DonkiNotificationsScreen(navController)
}

@Composable
private fun DonkiNotificationsScreen(navController: NavController<Destination>) {
    val model = viewModel<DonkiNotificationsScreenViewModel>()
    val filters = model.filtersUiState.collectAsStateWithLifecycle()
    val holder = rememberBaseEventsListStateHolder(
        items = model.pagingData.collectAsLazyPagingItems(),
        listState = rememberLazyListState(),
        filters = filters,
        allEventTypesAreDisabledErrorString = R.string.all_notification_types_are_disabled,
        noEventsInDateRangeErrorString = R.string.no_notifications_in_date_range,
        isLastWeekNeedsRefreshing = model::isLastWeekNeedsRefreshing,
    )
    val eventsTimeZone = model.notificationsTimeZone.collectAsStateWithLifecycle()
    DonkiNotificationsScreen(
        holder = holder,
        filtersUiState = filters,
        updateFilters = model::updateFilters,
        eventsTimeZone = eventsTimeZone,
        navigateToDetailsScreen = { navController.navigate(NotificationDetailsScreen(it)) },
        popBackStack = navController::pop
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonkiNotificationsScreen(
    holder: BaseEventsListStateHolder,
    filtersUiState: State<FiltersUiState<NotificationType>>,
    updateFilters: (FiltersUiState<NotificationType>) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    navigateToDetailsScreen: (NotificationId) -> Unit,
    popBackStack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val dialogNavController = rememberNavController<Destination>(initialBackstack = emptyList())
    DialogDestinationNavHost(dialogNavController)

    val showFiltersAsDialog = shouldShowFiltersAsDialog()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubScreenTopAppBar(stringResource(R.string.notifications), popBackStack) {
                if (showFiltersAsDialog.value) {
                    ToolbarIcon(Icons.Filled.FilterList, R.string.filters) {
                        dialogNavController.navigate(NotificationFiltersDialog)
                    }
                }
            }
        }
    ) { contentPadding ->
        BaseEventsList(
            holder = holder,

            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
            topAppBarScrollBehavior = null,

            filtersUiState = filtersUiState,
            updateFilters = updateFilters,
            allEventTypes = NotificationType.entries,
            eventTypeDisplayStringId = { it.displayStringResId },
            eventsTimeZone = eventsTimeZone,

            dialogNavController = dialogNavController,
            filtersDialogDestination = NotificationFiltersDialog,
            dateRangePickerDialogDestination = NotificationsDateRangePickerDialog,
            showFiltersAsDialog = showFiltersAsDialog,
            listItemKeyProvider = ListItem::lazyListKey,
            listContentTypeProvider = ListItem::lazyListContentType,
        ) { item ->
            item as DonkiNotificationsScreenViewModel.NotificationPresentation

            ElevatedCardWithPadding(
                onClick = { navigateToDetailsScreen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(2.dp)
            ) {
                Column {
                    Row {
                        Text(text = item.time, modifier = Modifier.weight(1.0f))
                        if (!item.read) {
                            val badgeColor = MaterialTheme.colorScheme.primary
                            Canvas(
                                Modifier
                                    .padding(start = Dimens.SpacingSmall)
                                    .size(6.dp)
                                    .align(Alignment.CenterVertically)) {
                                drawCircle(badgeColor)
                            }
                        }
                    }
                    Text(
                        text = item.title,
                        modifier = Modifier.padding(top = Dimens.SpacingSmall),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

private val ListItem.lazyListKey: Any
    get() = when (this) {
        is DateSeparator -> nextEventEpochSecond
        else -> (this as DonkiNotificationsScreenViewModel.NotificationPresentation).id
    }

private enum class ContentType {
    DateSeparator,
    Notification,
}

private val ListItem.lazyListContentType: Any
    get() = when (this) {
        is DateSeparator -> ContentType.DateSeparator
        else -> ContentType.Notification
    }

@PreviewScreenSizes
@Composable
fun DonkiNotificationsScreenPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        val filters = remember {
            mutableStateOf(
                FiltersUiState(
                    types = NotificationType.entries,
                    dateRange = null,
                    dateRangeEnabled = false
                )
            )
        }
        val items = flowOf(
            PagingData.from(
                listOf(
                    DateSeparator(
                        666,
                        LocalDate.now().toString()
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("0"),
                        time = LocalDateTime.now().toString(),
                        title = "Flare intensity has crossed the threshold of M5.0",
                        read = false
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("1"),
                        time = LocalDateTime.now().toString(),
                        title = "Weekly Space Weather Summary Report for October 02, 2024 - October 08, 2024",
                        read = true
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("1"),
                        time = LocalDateTime.now().toString(),
                        title = stringResource(NotificationType.MagnetopauseCrossing.displayStringResId),
                        read = true
                    )
                ),
            )
        ).collectAsLazyPagingItems()
        DonkiNotificationsScreen(
            holder = rememberBaseEventsListStateHolder(
                items = items,
                listState = rememberLazyListState(),
                filters = filters,
                allEventTypesAreDisabledErrorString = R.string.all_notification_types_are_disabled,
                noEventsInDateRangeErrorString = R.string.no_notifications_in_date_range,
                isLastWeekNeedsRefreshing = { false }
            ),
            filtersUiState = filters,
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            navigateToDetailsScreen = { },
            popBackStack = {}
        )
    }
}
