// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.ui.MainActivityViewModel
import org.equeim.spacer.ui.components.ElevatedCardWithPadding
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
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.notifications.details.NotificationDetailsScreen
import org.equeim.spacer.ui.screens.donki.rememberBaseEventsListStateHolder
import org.equeim.spacer.ui.screens.donki.shouldShowFiltersAsBottomSheet
import org.equeim.spacer.ui.screens.settings.DonkiNotificationsSettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.DoneAll
import org.equeim.spacer.ui.theme.FilterList
import org.equeim.spacer.ui.theme.ScreenPreview
import org.equeim.spacer.ui.utils.removeStart
import org.equeim.spacer.utils.safeLaunch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonkiNotificationsScreen(
    navController: NavController<Destination>,
    bottomAppBarScrollBehavior: BottomAppBarScrollBehavior,
    scrollToTopEvents: Flow<Unit>
) {
    val model = viewModel<DonkiNotificationsScreenViewModel>()
    val filters = model.filtersUiState.collectAsStateWithLifecycle()
    val holder = rememberBaseEventsListStateHolder(
        items = model.pagingData.collectAsLazyPagingItems(),
        listState = rememberLazyListState(),
        scrollToTopEvents = scrollToTopEvents,
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
        haveUnreadNotifications = model.haveUnreadNotifications.collectAsStateWithLifecycle(),
        markAllNotificationsAsRead = model::markAllNotificationsAsRead,
        shouldAskAboutEnablingNotifications = model::shouldAskAboutEnablingNotifications,
        askedAboutEnablingNotification = {
            model.askedAboutEnabledNotifications(it)
            if (it) navController.navigate(DonkiNotificationsSettingsScreen)
        },
        navigateToDetailsScreen = { navController.navigate(NotificationDetailsScreen(it)) },
        navigateToNotificationsSettings = { navController.navigate(DonkiNotificationsSettingsScreen) },
        bottomAppBarScrollBehavior = bottomAppBarScrollBehavior
    )

    val activityViewModel =
        viewModel<MainActivityViewModel>(viewModelStoreOwner = checkNotNull(LocalActivity.current) as ComponentActivity)
    DisposableEffect(activityViewModel) {
        activityViewModel.isOnDonkiNotificationsScreen = true
        onDispose { activityViewModel.isOnDonkiNotificationsScreen = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonkiNotificationsScreen(
    holder: BaseEventsListStateHolder,
    filtersUiState: State<FiltersUiState<NotificationType>>,
    updateFilters: (FiltersUiState<NotificationType>) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    haveUnreadNotifications: State<Boolean>,
    markAllNotificationsAsRead: () -> Unit,
    shouldAskAboutEnablingNotifications: suspend () -> Boolean,
    askedAboutEnablingNotification: (Boolean) -> Unit,
    navigateToDetailsScreen: (NotificationId) -> Unit,
    navigateToNotificationsSettings: () -> Unit,
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
                title = stringResource(R.string.notifications),
                scrollBehavior = topAppBarScrollBehavior,
                endActions = {
                    AnimatedVisibility(haveUnreadNotifications.value) {
                        IconButtonWithTooltip(
                            icon = Icons.Filled.DoneAll,
                            textId = R.string.mark_all_as_read,
                            onClick = markAllNotificationsAsRead
                        )
                    }
                    IconButtonWithTooltip(
                        icon = Icons.Filled.Settings,
                        textId = R.string.notifications_settings,
                        onClick = navigateToNotificationsSettings
                    )
                })
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
                        allEventTypes = NotificationType.entries,
                        eventTypeDisplayStringId = { it.displayStringResId },
                        eventsTimeZone = eventsTimeZone,
                        showDateRangeDialog = { showDateRangeDialog = true }
                    )
                }
            },

            listItemKeyProvider = ListItem::lazyListKey,
            listContentTypeProvider = ListItem::lazyListContentType,
        ) { item ->
            item as DonkiNotificationsScreenViewModel.NotificationPresentation

            ElevatedCardWithPadding(
                onClick = { navigateToDetailsScreen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(2.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(100.dp)
                )
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


        val requestPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
                askedAboutEnablingNotification
            )
        } else {
            null
        }
        val message = stringResource(R.string.system_notifications_question)
        val actionLabel = stringResource(R.string.yes)
        LaunchedEffect(message, actionLabel) {
            if (shouldAskAboutEnablingNotifications()) {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && result == SnackbarResult.ActionPerformed) {
                    checkNotNull(requestPermissionLauncher).safeLaunch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    askedAboutEnablingNotification(result == SnackbarResult.ActionPerformed)
                }
            }
        }
    }

    if (showFiltersBottomSheet) {
        EventFiltersBottomSheet(
            filtersUiState = filtersUiState,
            updateFilters = updateFilters,
            allEventTypes = NotificationType.entries,
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

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun DonkiNotificationsScreenPreview() {
    ScreenPreview {
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
                        read = false,
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("1"),
                        time = LocalDateTime.now().toString(),
                        title = "Weekly Space Weather Summary Report for October 02, 2024 - October 08, 2024",
                        subtitle = "Solar activity was at low levels during this reporting period. There were no significant flares or CMEs.",
                        read = true,
                    ),
                    DonkiNotificationsScreenViewModel.NotificationPresentation(
                        id = NotificationId("2"),
                        time = LocalDateTime.now().toString(),
                        title = stringResource(NotificationType.MagnetopauseCrossing.displayStringResId),
                        subtitle = null,
                        read = true,
                    )
                ),
            )
        ).collectAsLazyPagingItems()
        DonkiNotificationsScreen(
            holder = rememberBaseEventsListStateHolder(
                items = items,
                listState = rememberLazyListState(),
                scrollToTopEvents = remember { emptyFlow() },
                filters = filters,
                getNeedToRefreshState = { flowOf(NeedToRefreshState.DontNeedToRefresh) },
                allEventTypesAreDisabledErrorString = R.string.all_notification_types_are_disabled,
                noEventsInDateRangeErrorString = R.string.no_notifications_in_date_range,
            ),
            filtersUiState = filters,
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            haveUnreadNotifications = remember { mutableStateOf(true) },
            markAllNotificationsAsRead = {},
            shouldAskAboutEnablingNotifications = { true },
            askedAboutEnablingNotification = { },
            navigateToDetailsScreen = {},
            navigateToNotificationsSettings = {},
            bottomAppBarScrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
        )
    }
}
