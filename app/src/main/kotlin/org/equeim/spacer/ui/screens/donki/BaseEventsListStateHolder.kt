// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.common.NeedToRefreshState
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun rememberBaseEventsListStateHolder(
    items: LazyPagingItems<ListItem>,
    listState: LazyListState,
    scrollToTopEvents: Flow<Unit>,
    filters: State<FiltersUiState<*>>,
    getNeedToRefreshState: () -> Flow<NeedToRefreshState>,
    @StringRes allEventTypesAreDisabledErrorString: Int,
    @StringRes noEventsInDateRangeErrorString: Int,
): BaseEventsListStateHolder {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val registry = checkNotNull(LocalSaveableStateRegistry.current)
    return remember(items, listState, filters, context, lifecycleOwner, scope, registry) {
        BaseEventsListStateHolder(
            items,
            listState,
            scrollToTopEvents,
            filters,
            getNeedToRefreshState,
            allEventTypesAreDisabledErrorString,
            noEventsInDateRangeErrorString,
            context,
            lifecycleOwner,
            scope,
            registry
        )
    }
}

class BaseEventsListStateHolder(
    val items: LazyPagingItems<ListItem>,
    val listState: LazyListState,
    scrollToTopEvents: Flow<Unit>,
    filters: State<FiltersUiState<*>>,
    getNeedToRefreshState: () -> Flow<NeedToRefreshState>,
    @StringRes allEventTypesAreDisabledErrorString: Int,
    @StringRes noEventsInDateRangeErrorString: Int,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val coroutineScope: CoroutineScope,
    saveableStateRegistry: SaveableStateRegistry
) : RememberObserver {
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

    private var refreshingManually: Boolean =
        (saveableStateRegistry.consumeRestored(::refreshingManually.name) as Boolean?) ?: false
    private val registryEntry = saveableStateRegistry.registerProvider(::refreshingManually.name) { refreshingManually }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val needToRefreshState: StateFlow<NeedToRefreshState> =
        lifecycleOwner.lifecycle.currentStateFlow.map { it == Lifecycle.State.RESUMED }
            .distinctUntilChanged()
            .flatMapLatest { resumed ->
                if (resumed) getNeedToRefreshState() else flowOf(NeedToRefreshState.DontNeedToRefresh)
            }.stateIn(coroutineScope, SharingStarted.Eagerly, NeedToRefreshState.DontNeedToRefresh)

    val enableRefreshIndicator: StateFlow<Boolean> = needToRefreshState.map {
        it != NeedToRefreshState.DontNeedToRefresh
    }.stateIn(coroutineScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val showRefreshIndicator: StateFlow<Boolean> = loading.mapLatest { loading ->
        Log.d(TAG, "loadState = ${items.loadState}")
        Log.d(TAG, "loading = $loading")
        if (!loading) {
            refreshingManually = false
        }
        if (loading && !refreshingManually) {
            delay(REFRESH_INDICATOR_DELAY)
        }
        loading
    }.stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val fullscreenError: String? by derivedStateOf {
        if (items.itemCount == 0) {
            if (filters.value.types.isEmpty()) {
                context.getString(allEventTypesAreDisabledErrorString)
            } else {
                val loadError = with(items.loadState) {
                    firstErrorOrNull(
                        source.refresh,
                        mediator?.refresh,
                        source.append,
                        source.prepend
                    )
                }?.error?.donkiErrorToString(context)
                when {
                    loadError != null -> loadError
                    filters.value.dateRange != null -> context.getString(noEventsInDateRangeErrorString)

                    else -> null
                }
            }
        } else {
            null
        }
    }

    val snackbarError: String? by derivedStateOf {
        if (filters.value.types.isNotEmpty()) {
            with(items.loadState) {
                firstErrorOrNull(
                    source.refresh,
                    mediator?.refresh,
                    source.append,
                    source.prepend
                )?.error?.donkiErrorToString(context)
            }
        } else {
            null
        }
    }

    init {
        scrollToTopAfterSourceRefresh()

        scrollToTopEvents.onEach { listState.scrollToItem(0) }.launchIn(coroutineScope)

        snapshotFlow { filters }.drop(1).onEach {
            listState.scrollToItem(0)
        }.launchIn(coroutineScope)

        needToRefreshState
            .filter { it == NeedToRefreshState.HaveWeeksThatNeedRefreshNow }
            .onEach { refreshIfNotAlreadyLoading() }
            .launchIn(coroutineScope)
    }

    override fun onRemembered() = Unit

    override fun onForgotten() {
        registryEntry.unregister()
    }

    override fun onAbandoned() {
        registryEntry.unregister()
    }

    fun refreshIfNotAlreadyLoading() {
        Log.d(TAG, "refreshIfNotAlreadyLoading() called")
        if (!loading.value) {
            Log.d(TAG, "refreshIfNotAlreadyLoading: refreshing")
            items.refresh()
            refreshingManually = true
        } else {
            Log.d(TAG, "refreshIfNotAlreadyLoading: already loading")
        }
    }

    fun retry() {
        Log.d(TAG, "retry() called")
        items.retry()
        refreshingManually = true
    }

    private fun scrollToTopAfterSourceRefresh() {
        Log.d(TAG, "scrollToTopAfterSourceRefresh() called")
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

private const val TAG = "BaseEventsListStateHolder"

private val REFRESH_INDICATOR_DELAY = 300.milliseconds
private val SCROLL_TO_TOP_DELAY = 200.milliseconds

private fun isAnyLoading(vararg states: LoadState?): Boolean =
    states.asSequence().filterIsInstance<LoadState.Loading>().any()

private fun firstErrorOrNull(vararg states: LoadState?): LoadState.Error? =
    states.asSequence().filterIsInstance<LoadState.Error>().firstOrNull()

private val LazyListState.isAtTheTop: Boolean
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
