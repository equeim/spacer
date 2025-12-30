// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.equeim.spacer.R
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseEventsList(
    holder: BaseEventsListStateHolder,

    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    mainContentNestedScrollConnections: List<NestedScrollConnection>,

    filtersSideSheet: @Composable () -> Unit,

    listItemKeyProvider: (ListItem) -> Any,
    listContentTypeProvider: (ListItem) -> Any?,
    listItemSlot: @Composable (ListItem) -> Unit
) {
    val listIsEmpty by remember(holder) { derivedStateOf { holder.items.itemCount == 0 } }
    val initialLazyListState = rememberLazyListState()
    val lazyListState = if (listIsEmpty) initialLazyListState else holder.listState

    ShowSnackbarError(holder, snackbarHostState)

    val pullToRefreshState = rememberPullToRefreshState()
    val enableRefreshIndicator: Boolean by holder.enableRefreshIndicator.collectAsStateWithLifecycle()
    val showPullToRefreshIndicator: Boolean by holder.showRefreshIndicator.collectAsStateWithLifecycle()
    Box(Modifier.consumeWindowInsets(contentPadding)) {
        val fullscreenError = holder.fullscreenError
        Row(Modifier.fillMaxWidth()) {
            val mainContentModifier = Modifier
                .fillMaxHeight()
                .weight(1.0f)
                .run {
                    mainContentNestedScrollConnections.fold(this) { modifier, connection ->
                        modifier.nestedScroll(connection)
                    }
                }
                .pullToRefresh(
                    isRefreshing = showPullToRefreshIndicator,
                    state = pullToRefreshState,
                    enabled = enableRefreshIndicator,
                    onRefresh = holder::refreshIfNotAlreadyLoading
                )
            val mainContentPadding = contentPadding + Dimens.ScreenContentPadding()
            when {
                fullscreenError != null -> ErrorPlaceholder(
                    modifier = mainContentModifier,
                    contentPadding = mainContentPadding,
                    error = fullscreenError,
                )

                listIsEmpty -> LoadingPlaceholder(
                    modifier = mainContentModifier,
                    contentPadding = contentPadding
                )

                else -> EventsList(
                    modifier = mainContentModifier,
                    contentPadding = mainContentPadding,
                    lazyListState = lazyListState,
                    items = holder.items,
                    itemKeyProvider = listItemKeyProvider,
                    contentTypeProvider = listContentTypeProvider,
                    itemSlot = listItemSlot
                )
            }

            filtersSideSheet()
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = showPullToRefreshIndicator,
                modifier = Modifier.align(Alignment.TopCenter)
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
private fun ShowSnackbarError(
    holder: BaseEventsListStateHolder,
    snackbarHostState: SnackbarHostState,
) {
    val snackbarError = holder.snackbarError
    if (snackbarError != null) {
        val actionLabel = stringResource(R.string.retry)
        LaunchedEffect(snackbarHostState, snackbarError, actionLabel) {
            val result = snackbarHostState.showSnackbar(
                message = snackbarError,
                actionLabel = actionLabel,
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
private fun ErrorPlaceholder(
    modifier: Modifier,
    contentPadding: PaddingValues,
    error: String
) {
    Box(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        Text(
            text = error,
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun LoadingPlaceholder(
    modifier: Modifier,
    contentPadding: PaddingValues
) {
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
private fun EventsList(
    modifier: Modifier,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    items: LazyPagingItems<ListItem>,
    itemKeyProvider: (ListItem) -> Any,
    contentTypeProvider: (ListItem) -> Any?,
    itemSlot: @Composable (ListItem) -> Unit,
) {
    LazyColumn(
        modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBetweenCards)
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey(itemKeyProvider),
            contentType = items.itemContentType(contentTypeProvider)
        ) { index ->
            when (val item = checkNotNull(items[index])) {
                is DateSeparator -> {
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

                else -> itemSlot(item)
            }
        }
    }
}

interface ListItem

data class DateSeparator(val date: String) : ListItem
