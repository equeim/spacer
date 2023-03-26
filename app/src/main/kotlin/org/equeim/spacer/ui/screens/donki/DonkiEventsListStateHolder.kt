// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun rememberDonkiEventsListStateHolder(items: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>, listState: LazyListState): DonkiEventsListStateHolder {
    val scope = rememberCoroutineScope()
    val registry = checkNotNull(LocalSaveableStateRegistry.current)
    val holder = remember(items) { DonkiEventsListStateHolder(items, listState, scope, registry) }
    DisposableEffect(holder) {
        onDispose {
            holder.close()
        }
    }
    return holder
}

class DonkiEventsListStateHolder(
    val items: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>,
    val listState: LazyListState,
    coroutineScope: CoroutineScope,
    saveableStateRegistry: SaveableStateRegistry
): Closeable {
    private val loading: StateFlow<Boolean> = snapshotFlow {
        with(items.loadState) {
            isAnyLoading(source.refresh, mediator?.refresh) ||
                    (items.itemCount == 0 && isAnyLoading(
                        source.append,
                        source.prepend,
                        mediator?.append,
                        mediator?.prepend
                    ))
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private var refreshingManually: Boolean = (saveableStateRegistry.consumeRestored(::refreshingManually.name) as Boolean?) ?: false
    private val registryEntry = saveableStateRegistry.registerProvider(::refreshingManually.name) { refreshingManually }

    @OptIn(ExperimentalCoroutinesApi::class)
    val showRefreshIndicator: StateFlow<Boolean> = loading.mapLatest { loading ->
        if (!loading) {
            refreshingManually = false
        }
        if (loading && !refreshingManually) {
            delay(REFRESH_INDICATOR_DELAY)
        }
        loading
    }.stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val fullscreenError: LoadState.Error? by derivedStateOf {
        if (items.itemCount == 0) {
            with(items.loadState) {
                firstErrorOrNull(
                    source.refresh,
                    mediator?.refresh,
                    source.append,
                    source.prepend
                )
            }
        } else {
            null
        }
    }

    val snackbarError: LoadState.Error? by derivedStateOf {
        with(items.loadState) {
            firstErrorOrNull(source.refresh, mediator?.refresh, source.append, source.prepend)
        }
    }

    init {
        scrollToTopAfterSourceRefresh(coroutineScope)

        snapshotFlow { items.itemCount == 0 }.onEach {
            Log.d(TAG, "list is empty = $it")
        }.launchIn(coroutineScope)
    }

    override fun close() {
        registryEntry.unregister()
    }

    fun refresh() {
        items.refresh()
        refreshingManually = true
    }

    fun retry() {
        items.retry()
        refreshingManually = true
    }

    private fun scrollToTopAfterSourceRefresh(coroutineScope: CoroutineScope) {
        Log.d(TAG, "scrollToTopAfterSourceRefresh() called with: coroutineScope = $coroutineScope")
        val sourceFinishedRefreshing: Flow<Unit> = snapshotFlow { items.loadState.source.refresh }
            .onEach { Log.d(TAG, "scrollToTopAfterSourceRefresh: source refresh state = $it") }
            .dropWhile { it is LoadState.Loading }
            .filter { it is LoadState.NotLoading }
            .map {}
            .onEach { Log.d(TAG, "scrollToTopAfterSourceRefresh: source finished refreshing") }
        coroutineScope.launch {
            sourceFinishedRefreshing.collectLatest {
                if (!listState.isAtTheTop) {
                    Log.d(TAG, "scrollToTopAfterSourceRefresh: not at the top, do nothing")
                    return@collectLatest
                }
                coroutineScope {
                    val delayScope = this
                    launch {
                        Log.d(TAG, "scrollToTopAfterSourceRefresh: delay before scrolling")
                        delay(SCROLL_TO_TOP_DELAY)
                        Log.d(TAG, "scrollToTopAfterSourceRefresh: scrolling")
                        listState.animateScrollToItem(0)
                        delayScope.cancel()
                    }
                    launch {
                        val interaction = listState.interactionSource.interactions.first()
                        Log.d(
                            TAG,
                            "scrollToTopAfterSourceRefresh: interrupted by interaction $interaction"
                        )
                        delayScope.cancel()
                    }
                }
            }
        }
    }
}

private const val TAG = "DonkiEventsListStateHolder"

private val REFRESH_INDICATOR_DELAY = 300.milliseconds
private val SCROLL_TO_TOP_DELAY = 200.milliseconds

private fun isAnyLoading(vararg states: LoadState?): Boolean =
    states.asSequence().filterIsInstance<LoadState.Loading>().any()

private fun firstErrorOrNull(vararg states: LoadState?): LoadState.Error? =
    states.asSequence().filterIsInstance<LoadState.Error>().firstOrNull()

private val LazyListState.isAtTheTop: Boolean
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
