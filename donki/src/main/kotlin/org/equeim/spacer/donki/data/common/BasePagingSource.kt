// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingSourceFactory
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.spacer.donki.CoroutineDispatchers
import java.io.Closeable
import java.lang.ref.WeakReference
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

internal abstract class BasePagingSource<Item : Any>(
    protected val dateRange: DateRange?,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val clock: Clock,
    @Suppress("PropertyName")
    protected val TAG: String,
) : PagingSource<Week, Item>() {
    private var lastLoadReturnedEmptyPage = false

    protected abstract suspend fun getItemsForWeek(
        week: Week,
        dateRange: DateRange?,
        refreshCacheIfNeeded: Boolean
    ): List<Item>

    /**
     * We are refreshing from the top of the list so don't bother with implementing this
     * Initial loading key in [load] will be used
     */
    override fun getRefreshKey(state: PagingState<Week, Item>): Week? = null

    override suspend fun load(params: LoadParams<Week>): LoadResult<Week, Item> {
        Log.d(TAG, m("load() called with: params = $params"))
        Log.d(TAG, m("load: requested weeks are ${params.key}"))
        return withContext(coroutineDispatchers.Default) {
            val currentWeek = Week.getCurrentWeek(clock)
            Log.d(TAG, m("load: current week is $currentWeek"))

            val requestedWeek = params.key
            val week = when {
                requestedWeek != null -> {
                    if (requestedWeek > currentWeek) {
                        Log.e(TAG, m("load: requested week is in the future"))
                        return@withContext LoadResult.Invalid()
                    }
                    requestedWeek
                }

                dateRange != null -> dateRange.lastWeek
                else -> currentWeek
            }
            Log.d(TAG, m("load: loading week = $week"))
            try {
                val events = getItemsForWeek(
                    week = week,
                    dateRange = dateRange?.coerceToWeek(week),
                    refreshCacheIfNeeded = params !is LoadParams.Refresh
                )
                if (lastLoadReturnedEmptyPage && events.isEmpty()) {
                    Log.d(
                        TAG,
                        m("load: previous load returned empty page and this one is empty too, wait 1 second before returning")
                    )
                    delay(EMPTY_PAGE_THROTTLE_DELAY)
                }
                lastLoadReturnedEmptyPage = events.isEmpty()
                LoadResult.Page(
                    data = events,
                    prevKey = week.futureWeek(currentWeek, dateRange),
                    nextKey = week.pastWeek(dateRange)
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, m("load: failed to get events summaries"), e)
                LoadResult.Error(e)
            }
        }.also {
            if (it is LoadResult.Page<*, *>) {
                Log.d(
                    TAG,
                    m("load: returning Page with ${it.data.size} events, prevKey = ${it.prevKey}, nextKey = ${it.nextKey}")
                )
            } else {
                Log.d(TAG, m("load: returning $it"))
            }
        }
    }

    private val identity: String by lazy(LazyThreadSafetyMode.NONE) { Integer.toHexString(System.identityHashCode(this)) }

    protected fun m(msg: String): String =
        "[0x$identity] $msg"

    private companion object {
        val EMPTY_PAGE_THROTTLE_DELAY = 1.seconds
    }

    class Factory<Item : Any>(invalidationEvents: Flow<*>, coroutineDispatchers: CoroutineDispatchers, private val createPagingSource: () -> BasePagingSource<Item>) : PagingSourceFactory<Week, Item>, Closeable {
        private val coroutineScope = CoroutineScope(coroutineDispatchers.Default)
        @Volatile
        private var lastPagingSource: WeakReference<BasePagingSource<Item>>? = null

        init {
            coroutineScope.launch {
                invalidationEvents.collect {
                    lastPagingSource?.get()?.apply {
                        m("Invalidating")
                        invalidate()
                    }
                }
            }
        }

        override fun invoke(): BasePagingSource< Item> {
            return createPagingSource().also { lastPagingSource = WeakReference(it) }
        }

        override fun close() {
            coroutineScope.cancel()
        }
    }
}
