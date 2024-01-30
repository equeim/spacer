// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalLayoutApi::class)

package org.equeim.spacer.ui.screens.donki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.ui.components.ElevatedCardWithPadding
import org.equeim.spacer.ui.components.RootScreenTopAppBar
import org.equeim.spacer.ui.components.ToolbarIcon
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.DialogDestinationNavHost
import org.equeim.spacer.ui.screens.LocalNavController
import org.equeim.spacer.ui.screens.donki.details.DonkiEventDetailsScreen
import org.equeim.spacer.ui.screens.settings.SettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.FilterList
import org.equeim.spacer.ui.utils.collectWhenStarted
import org.equeim.spacer.ui.utils.plus
import org.equeim.spacer.utils.getActivityOrThrow

@Parcelize
object DonkiEventsScreen : Destination {
    @Composable
    override fun Content(navController: NavController<Destination>, parentNavHostEntry: NavHostEntry<Destination>?) =
        DonkiEventsScreen()
}

@Composable
private fun DonkiEventsScreen() {
    val model = viewModel<DonkiEventsScreenViewModel>()
    val holder = rememberDonkiEventsListStateHolder(
        model.pagingData.collectAsLazyPagingItems(),
        rememberLazyListState(),
        model.filters,
        model.filters::value::set
    )
    DonkiEventsScreen(holder)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun DonkiEventsScreen(holder: DonkiEventsListStateHolder) {
    val listIsEmpty by remember(holder) { derivedStateOf { holder.items.itemCount == 0 } }
    val initialLazyListState = rememberLazyListState()
    val lazyListState = if (listIsEmpty) initialLazyListState else holder.listState

    val snackbarHostState = remember { SnackbarHostState() }

    ShowSnackbarError(holder, snackbarHostState)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val showFiltersAsDialog =
        calculateWindowSizeClass(LocalContext.current.getActivityOrThrow()).widthSizeClass == WindowWidthSizeClass.Compact

    val dialogNavController = if (showFiltersAsDialog) {
        val navController = rememberNavController<Destination>(initialBackstack = emptyList())
        DialogDestinationNavHost(navController)
        navController
    } else {
        null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RootScreenTopAppBar(
                stringResource(R.string.space_weather_events),
                scrollBehavior,
                startActions = {
                    if (dialogNavController != null) {
                        ToolbarIcon(Icons.Filled.FilterList, R.string.filters) {
                            dialogNavController.navigate(DonkiEventFiltersDialog)
                        }
                    }
                },
                endActions = {
                    val navController = LocalNavController.current
                    ToolbarIcon(Icons.Filled.Settings, R.string.filters) {
                        navController.navigate(SettingsScreen)
                    }
                }
            )
        }
    ) { contentPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(pullToRefreshState, lifecycleOwner) {
            holder.showRefreshIndicator.collectWhenStarted(lifecycleOwner) {
                if (pullToRefreshState.isRefreshing != it) {
                    if (it) pullToRefreshState.startRefresh() else pullToRefreshState.endRefresh()
                }
            }
        }
        if (pullToRefreshState.isRefreshing) {
            SideEffect {
                holder.refreshIfNotAlreadyLoading()
            }
        }
        Box(Modifier.consumeWindowInsets(contentPadding)) {
            val fullscreenError = holder.fullscreenError
            Row(Modifier.fillMaxWidth()) {
                val mainContentModifier = Modifier
                    .fillMaxHeight()
                    .weight(1.0f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
                val mainContentPadding = contentPadding + Dimens.ScreenContentPadding()
                when {
                    fullscreenError != null -> DonkiEventsScreenContentErrorPlaceholder(
                        mainContentModifier,
                        mainContentPadding,
                        fullscreenError,
                    )

                    listIsEmpty -> DonkiEventsScreenContentLoadingPlaceholder(mainContentModifier, contentPadding)
                    else -> DonkiEventsScreenContentPaging(
                        mainContentModifier,
                        mainContentPadding,
                        lazyListState,
                        holder.items,
                    )
                }

                if (!showFiltersAsDialog) {
                    val filters = holder.filters.collectAsState()
                    DonkiEventFiltersSideSheet(
                        contentPadding = contentPadding,
                        filters = filters::value,
                        updateFilters = holder.updateFilters
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                PullToRefreshContainer(pullToRefreshState, Modifier.align(Alignment.TopCenter))
                if (!listIsEmpty) {
                    if (holder.items.loadState.source.prepend is LoadState.Loading) {
                        LinearProgressIndicator(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                        )
                    }
                    if (holder.items.loadState.source.append is LoadState.Loading) {
                        LinearProgressIndicator(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowSnackbarError(
    holder: DonkiEventsListStateHolder,
    snackbarHostState: SnackbarHostState,
) {
    val snackbarError = holder.snackbarError
    if (snackbarError != null) {
        val context = LocalContext.current
        LaunchedEffect(snackbarHostState) {
            val result = snackbarHostState.showSnackbar(
                message = snackbarError,
                actionLabel = context.getString(R.string.retry),
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                holder.retry()
            }
        }
    }
}

@Composable
private fun DonkiEventsScreenContentErrorPlaceholder(modifier: Modifier, contentPadding: PaddingValues, error: String) {
    Box(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        Text(
            text = error,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun DonkiEventsScreenContentLoadingPlaceholder(modifier: Modifier, contentPadding: PaddingValues) {
    Box(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun DonkiEventsScreenContentPaging(
    modifier: Modifier,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    items: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>,
) {
    LazyColumn(
        modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBetweenCards)
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey(DonkiEventsScreenViewModel.ListItem::lazyListKey),
            contentType = items.itemContentType(DonkiEventsScreenViewModel.ListItem::lazyListContentType)
        ) { index ->
            when (val item = checkNotNull(items[index])) {
                is DonkiEventsScreenViewModel.DateSeparator -> {
                    Row(Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.secondary,
                            tonalElevation = 6.dp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(
                                    horizontal = Dimens.SpacingLarge,
                                    vertical = 4.dp
                                )
                            )
                        }
                    }
                }

                is DonkiEventsScreenViewModel.EventPresentation -> {
                    val navController = LocalNavController.current
                    ElevatedCardWithPadding(
                        { navController.navigate(DonkiEventDetailsScreen(item.id)) },
                        Modifier.fillMaxWidth(),
                        CardDefaults.elevatedCardElevation(2.dp)
                    ) {
                        Column {
                            Text(text = item.time)
                            if (item.detailsSummary != null) {
                                Row(
                                    Modifier.padding(top = Dimens.SpacingSmall),
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = item.type,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1.0f)
                                    )
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                        Text(
                                            text = item.detailsSummary,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.weight(0.3f),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = item.type,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = Dimens.SpacingSmall)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DonkiEventsScreenViewModel.ListItem.lazyListKey: Any
    get() = when (this) {
        is DonkiEventsScreenViewModel.EventPresentation -> id
        is DonkiEventsScreenViewModel.DateSeparator -> nextEventEpochSecond
    }

private val DonkiEventsScreenViewModel.ListItem.lazyListContentType: Any?
    get() = when (this) {
        is DonkiEventsScreenViewModel.EventPresentation -> if (detailsSummary != null) {
            Unit
        } else {
            null
        }

        is DonkiEventsScreenViewModel.DateSeparator -> DonkiEventsScreenViewModel.DateSeparator::class
    }