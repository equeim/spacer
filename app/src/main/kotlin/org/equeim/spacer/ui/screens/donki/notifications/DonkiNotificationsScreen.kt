// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import org.equeim.spacer.donki.data.common.NeedToRefreshState
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.components.ElevatedCardWithPadding
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.components.ToolbarIcon
import org.equeim.spacer.ui.components.ToolbarIconWithBadge
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
import org.equeim.spacer.ui.theme.DoneAll
import org.equeim.spacer.ui.theme.FilterList
import org.equeim.spacer.ui.utils.rememberIntegerFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@Parcelize
data object DonkiNotificationsScreen : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) =
        DonkiNotificationsScreen(navController, navHostEntries)
}

@Composable
private fun DonkiNotificationsScreen(
    navController: NavController<Destination>,
    navHostEntries: List<NavHostEntry<Destination>>
) {
    val model = viewModel<DonkiNotificationsScreenViewModel>()
    val filters = model.filtersUiState.collectAsStateWithLifecycle()
    model.filtersUiState.collectAsState()
    val holder = rememberBaseEventsListStateHolder(
        items = model.pagingData.collectAsLazyPagingItems(),
        listState = rememberLazyListState(),
        filters = filters,
        getNeedToRefreshState = model::getNeedToRefreshState,
        allEventTypesAreDisabledErrorString = R.string.all_notification_types_are_disabled,
        noEventsInDateRangeErrorString = R.string.no_notifications_in_date_range,
    )
    DonkiNotificationsScreen(
        holder = holder,
        filtersUiState = filters,
        updateFilters = model::updateFilters,
        eventsTimeZone = model.notificationsTimeZone.collectAsStateWithLifecycle(),
        numberOfUnreadNotifications = model.numberOfUnreadNotifications.collectAsStateWithLifecycle(),
        markAllNotificationsAsRead = model::markAllNotificationsAsRead,
        navigateToDetailsScreen = { navController.navigate(NotificationDetailsScreen(it)) },
        popBackStack = navController::pop,
        navHostEntries = { navHostEntries }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonkiNotificationsScreen(
    holder: BaseEventsListStateHolder,
    filtersUiState: State<FiltersUiState<NotificationType>>,
    updateFilters: (FiltersUiState<NotificationType>) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    numberOfUnreadNotifications: State<Int>,
    markAllNotificationsAsRead: () -> Unit,
    navigateToDetailsScreen: (NotificationId) -> Unit,
    popBackStack: () -> Unit,
    navHostEntries: () -> List<NavHostEntry<Destination>>,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val dialogNavController = rememberNavController<Destination>(initialBackstack = emptyList())
    DialogDestinationNavHost(dialogNavController, navHostEntries)

    val showFiltersAsDialog = shouldShowFiltersAsDialog()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubScreenTopAppBar(stringResource(R.string.notifications), popBackStack) {
                val haveUnreadNotifications = remember { derivedStateOf { numberOfUnreadNotifications.value != 0 } }
                AnimatedVisibility(haveUnreadNotifications.value) {
                    val formatter = rememberIntegerFormatter()
                    ToolbarIconWithBadge(
                        icon = Icons.Filled.DoneAll,
                        textId = R.string.mark_all_as_read,
                        badgeText = { formatter.format(numberOfUnreadNotifications.value.toLong()) },
                        onClick = markAllNotificationsAsRead
                    )
                }
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
                                    .align(Alignment.CenterVertically)
                            ) {
                                drawCircle(badgeColor)
                            }
                        }
                    }
                    Text(
                        text = item.title,
                        modifier = Modifier.padding(top = Dimens.SpacingSmall),
                        style = MaterialTheme.typography.titleMedium
                    )
                    item.subtitle?.let {
                        Text(
                            text = item.subtitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Dimens.SpacingSmall)
                        )
                    }
                }
            }
        }
    }
}

private val ListItem.lazyListKey: Any
    get() = when (this) {
        is DateSeparator -> date
        else -> (this as DonkiNotificationsScreenViewModel.NotificationPresentation).id.stringValue
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
                    DateSeparator(LocalDate.now().toString()),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("0"),
                        time = LocalDateTime.now().toString(),
                        title = "Flare intensity has crossed the threshold of M5.0",
                        subtitle = "Significant flare detected by GOES.  Flare intensity has crossed the threshold of M5.0.",
                        read = false
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("1"),
                        time = LocalDateTime.now().toString(),
                        title = "Weekly Space Weather Summary Report for October 02, 2024 - October 08, 2024",
                        subtitle = "Solar activity was at low levels during this reporting period. There were no significant flares or CMEs.",
                        read = true
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("2"),
                        time = LocalDateTime.now().toString(),
                        title = stringResource(NotificationType.MagnetopauseCrossing.displayStringResId),
                        subtitle = null,
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
                getNeedToRefreshState = { flowOf(NeedToRefreshState.DontNeedToRefresh) },
                allEventTypesAreDisabledErrorString = R.string.all_notification_types_are_disabled,
                noEventsInDateRangeErrorString = R.string.no_notifications_in_date_range,
            ),
            filtersUiState = filters,
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            numberOfUnreadNotifications = remember { mutableIntStateOf(42) },
            markAllNotificationsAsRead = { },
            navigateToDetailsScreen = { },
            popBackStack = {},
            navHostEntries = { emptyList() }
        )
    }
}
