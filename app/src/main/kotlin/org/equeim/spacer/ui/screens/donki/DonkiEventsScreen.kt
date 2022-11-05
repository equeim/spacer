// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.equeim.spacer.ui.components.Card
import org.equeim.spacer.ui.components.RootScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.details.DonkiEventDetailsScreen
import org.equeim.spacer.ui.screens.settings.SettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.addBottomInsetUnless
import org.equeim.spacer.ui.utils.hasBottomPadding
import org.equeim.spacer.ui.utils.toAppBarElevation

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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DonkiEventsScreen(holder: DonkiEventsListStateHolder) {
    val listIsEmpty by remember(holder) { derivedStateOf { holder.items.itemCount == 0 } }
    val initialLazyListState = rememberLazyListState()
    val lazyListState = if (listIsEmpty) initialLazyListState else holder.listState

    val scaffoldState = rememberScaffoldState()

    ShowSnackbarError(holder, scaffoldState)

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            RootScreenTopAppBar(
                stringResource(R.string.space_weather_events),
                elevation = lazyListState.toAppBarElevation()
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
                .padding(contentPadding)
                .pullRefresh(pullRefreshState)
        ) {
            val fullscreenError = holder.fullscreenError
            when {
                fullscreenError != null -> DonkiEventsScreenContentErrorPlaceholder(fullscreenError)
                listIsEmpty -> DonkiEventsScreenContentLoadingPlaceholder()
                else -> DonkiEventsScreenContentPaging(
                    lazyListState,
                    holder.items,
                    contentPadding.hasBottomPadding
                )
            }
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

@Composable
private fun ShowSnackbarError(holder: DonkiEventsListStateHolder, scaffoldState: ScaffoldState) {
    val snackbarError = holder.snackbarError
    if (snackbarError != null) {
        val context = LocalContext.current
        LaunchedEffect(scaffoldState.snackbarHostState) {
            val result = scaffoldState.snackbarHostState.showSnackbar(
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
private fun DonkiEventsScreenContentErrorPlaceholder(@Suppress("UNUSED_PARAMETER") error: LoadState.Error) {
    /**
     * We need [verticalScroll] for [pullRefresh] to work
     */
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.error),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.h6
        )
    }
}

@Composable
private fun BoxScope.DonkiEventsScreenContentLoadingPlaceholder() {
    Text(
        text = stringResource(R.string.loading),
        style = MaterialTheme.typography.h6,
        modifier = Modifier.align(Alignment.Center)
    )
}

@Composable
private fun DonkiEventsScreenContentPaging(
    lazyListState: LazyListState,
    items: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>,
    screenHasBottomPadding: Boolean
) {
    val listPadding =
        PaddingValues(Dimens.ScreenContentPadding).addBottomInsetUnless(screenHasBottomPadding)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = listPadding,
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
                            color = MaterialTheme.colors.secondary,
                            elevation = 6.dp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.h6,
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
                    Card(
                        { navController.navigate(DonkiEventDetailsScreen(item.id)) },
                        Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(text = item.time)
                            Text(
                                text = item.type,
                                style = MaterialTheme.typography.h6,
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
