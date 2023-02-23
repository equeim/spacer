// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalLayoutApi::class)

package org.equeim.spacer.ui.screens.donki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.ui.LocalNavController
import org.equeim.spacer.ui.components.ElevatedCardWithPadding
import org.equeim.spacer.ui.components.RootScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.details.DonkiEventDetailsScreen
import org.equeim.spacer.ui.screens.settings.SettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.plus

@Parcelize
object DonkiEventsScreen : Destination {
    @Composable
    override fun Content() = DonkiEventsScreen()
}

@Composable
private fun DonkiEventsScreen() {
    val model = viewModel<DonkiEventsScreenViewModel>()
    val holder = rememberDonkiEventsListStateHolder(
        model.pagingData.collectAsLazyPagingItems(),
        rememberLazyListState()
    )
    DonkiEventsScreen(holder)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonkiEventsScreen(holder: DonkiEventsListStateHolder) {
    val listIsEmpty by remember(holder) { derivedStateOf { holder.items.itemCount == 0 } }
    val initialLazyListState = rememberLazyListState()
    val lazyListState = if (listIsEmpty) initialLazyListState else holder.listState

    val snackbarHostState = remember { SnackbarHostState() }

    ShowSnackbarError(holder, snackbarHostState)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RootScreenTopAppBar(
                stringResource(R.string.space_weather_events),
                scrollBehavior
            ) {
                val navController = LocalNavController.current
                IconButton(onClick = { navController.navigate(SettingsScreen) }) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                }
            }
        }
    ) { contentPadding ->
        val showRefreshIndicator by holder.showRefreshIndicator.collectAsState()
        val pullRefreshState = rememberPullRefreshState(
            refreshing = showRefreshIndicator,
            onRefresh = holder::refresh
        )
        Box(
            Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)) {
            val fullscreenError = holder.fullscreenError
            when {
                fullscreenError != null -> DonkiEventsScreenContentErrorPlaceholder(contentPadding)
                listIsEmpty -> DonkiEventsScreenContentLoadingPlaceholder(contentPadding)
                else -> DonkiEventsScreenContentPaging(
                    lazyListState,
                    holder.items,
                    contentPadding
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
            ) {
                PullRefreshIndicator(
                    showRefreshIndicator,
                    pullRefreshState,
                    Modifier.align(Alignment.TopCenter)
                )
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
    snackbarHostState: SnackbarHostState
) {
    val snackbarError = holder.snackbarError
    if (snackbarError != null) {
        val context = LocalContext.current
        LaunchedEffect(snackbarHostState) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.error),
                actionLabel = context.getString(R.string.retry),
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                holder.retry()
            }
        }
    }
}

@Composable
private fun DonkiEventsScreenContentErrorPlaceholder(contentPadding: PaddingValues) {
    /**
     * We need [verticalScroll] for [pullRefresh] to work
     */
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
    ) {
        Text(
            text = stringResource(R.string.error),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun BoxScope.DonkiEventsScreenContentLoadingPlaceholder(contentPadding: PaddingValues) {
    Text(
        text = stringResource(R.string.loading),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
    )
}

@Composable
private fun DonkiEventsScreenContentPaging(
    lazyListState: LazyListState,
    items: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>,
    contentPadding: PaddingValues
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding),
        state = lazyListState,
        contentPadding = contentPadding + PaddingValues(Dimens.ScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBetweenCards)
    ) {
        items(
            items,
            key = DonkiEventsScreenViewModel.ListItem::lazyListKey
        ) { item ->
            checkNotNull(item)
            when (item) {
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

private val DonkiEventsScreenViewModel.ListItem.lazyListKey: Any
    get() = when (this) {
        is DonkiEventsScreenViewModel.EventPresentation -> id
        is DonkiEventsScreenViewModel.DateSeparator -> nextEventEpochSecond
    }
