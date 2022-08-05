// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import androidx.compose.foundation.interaction.Interaction
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
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
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
import kotlin.time.Duration.Companion.milliseconds

@Parcelize
object DonkiEventsScreen : Destination {
    @Composable
    override fun Content() = DonkiEventsScreen()
}

@Composable
private fun DonkiEventsScreen() {
    val model = viewModel<DonkiEventsScreenViewModel>()
    val paging = model.pagingData.collectAsLazyPagingItems()
    DonkiEventsScreen(paging)
}

@Composable
private fun DonkiEventsScreen(paging: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>) {
    val listIsEmpty by remember(paging) { derivedStateOf { paging.itemCount == 0 } }
    val initialLazyListState = rememberLazyListState()
    val actualLazyListState = rememberLazyListState()
    val lazyListState = if (listIsEmpty) initialLazyListState else actualLazyListState

    val scaffoldState = rememberScaffoldState()

    SnackbarError(paging, scaffoldState)
    ScrollToTopAfterSourceRefresh(lazyListState, paging)

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
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            val showRefreshIndicator by remember(paging) { derivedStateOf { showRefreshIndicator(paging) } }
            val swipeRefreshState = rememberSwipeRefreshState(showRefreshIndicator)
            SwipeRefresh(
                swipeRefreshState,
                onRefresh = paging::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                val fullscreenError by remember(paging) { derivedStateOf { getFullscreenError(paging) } }
                when {
                    fullscreenError != null -> DonkiEventsScreenContentErrorPlaceholder()
                    listIsEmpty -> DonkiEventsScreenContentLoadingPlaceholder()
                    else -> DonkiEventsScreenContentPaging(
                        lazyListState,
                        paging,
                        contentPadding.hasBottomPadding
                    )
                }
            }
            if (!listIsEmpty) {
                if (paging.loadState.source.prepend is LoadState.Loading) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart))
                }
                if (paging.loadState.source.append is LoadState.Loading) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart))
                }
            }
        }
    }
}

@Composable
private fun SnackbarError(paging: LazyPagingItems<*>, scaffoldState: ScaffoldState) {
    val snackbarError by remember(paging) { derivedStateOf { getSnackbarError(paging) } }
    if (snackbarError != null) {
        val context = LocalContext.current
        LaunchedEffect(scaffoldState.snackbarHostState) {
            val result = scaffoldState.snackbarHostState.showSnackbar(
                message = context.getString(R.string.error),
                actionLabel = context.getString(R.string.retry),
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                paging.retry()
            }
        }
    }
}

private val SCROLL_TO_TOP_DELAY = 200.milliseconds
@Composable
private fun ScrollToTopAfterSourceRefresh(lazyListState: LazyListState, paging: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>) {
    val isAtTheTop by remember(lazyListState) { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0 } }
    val sourceIsRefreshing by remember(paging) { derivedStateOf { paging.loadState.source.refresh is LoadState.Loading } }
    LaunchedEffect(lazyListState, paging) {
        val sourceFinishedRefreshing = snapshotFlow { sourceIsRefreshing }.filter { !it }
        merge(sourceFinishedRefreshing, lazyListState.interactionSource.interactions).collectLatest {
            if (it !is Interaction && isAtTheTop) {
                delay(SCROLL_TO_TOP_DELAY)
                lazyListState.animateScrollToItem(0)
            }
        }
    }
}

private fun showRefreshIndicator(paging: LazyPagingItems<*>): Boolean {
    return with (paging.loadState) {
        isLoading(source.refresh, mediator?.refresh) ||
                (paging.itemCount == 0 && isLoading(source.append, source.prepend))
    }
}

private fun getFullscreenError(paging: LazyPagingItems<*>): LoadState.Error? {
    return if (paging.itemCount == 0) {
        with(paging.loadState) {
            anyError(source.refresh, mediator?.refresh, source.append, source.prepend)
        }
    } else {
        null
    }
}

private fun getSnackbarError(paging: LazyPagingItems<*>): LoadState.Error? {
    return with (paging.loadState) {
        anyError(source.refresh, mediator?.refresh, source.append, source.prepend)
    }
}

private fun isLoading(vararg states: LoadState?): Boolean =
    states.asSequence().filterIsInstance<LoadState.Loading>().any()

private fun anyError(vararg states: LoadState?): LoadState.Error? =
    states.asSequence().filterIsInstance<LoadState.Error>().firstOrNull()

@Composable
private fun DonkiEventsScreenContentErrorPlaceholder() {
    /**
     * We need [verticalScroll] for [SwipeRefresh] to work
     */
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())) {
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
    paging: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>,
    screenHasBottomPadding: Boolean
) {
    val listPadding = PaddingValues(Dimens.ScreenContentPadding).addBottomInsetUnless(screenHasBottomPadding)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBetweenCards)
    ) {
        items(
            paging,
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
                                modifier = Modifier.padding(horizontal = Dimens.SpacingLarge, vertical = 4.dp)
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
