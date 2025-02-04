// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalPagingApi::class)
internal abstract class BaseRemoteMediator<Item : Any, RefreshData : Any> : RemoteMediator<Week, Item>() {
    @Suppress("PropertyName")
    protected abstract val TAG: String
    protected abstract suspend fun getRefreshData(initialRefresh: Boolean): RefreshData?
    protected abstract suspend fun refresh(data: RefreshData)

    private val _refreshed = MutableSharedFlow<Unit>()
    val refreshed: Flow<Unit> by ::_refreshed

    private val pendingInitialRefreshData = AtomicReference<RefreshData>(null)

    override suspend fun initialize(): InitializeAction {
        Log.d(TAG, "initialize() called")
        val data = try {
            getRefreshData(initialRefresh = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "initialize: failed to get refresh data", e)
            null
        }
        return if (data != null) {
            pendingInitialRefreshData.set(data)
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }.also {
            Log.d(TAG, "initialize: returning $it")
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Week, Item>,
    ): MediatorResult {
        Log.d(TAG, "load() called with: loadType = $loadType, state = $state")
        if (loadType != LoadType.REFRESH) {
            Log.d(TAG, "load: not refreshing, ignore")
            return MediatorResult.Success(endOfPaginationReached = loadType == LoadType.PREPEND)
        }
        val data = pendingInitialRefreshData.getAndSet(null) ?: try {
            getRefreshData(initialRefresh = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "load: failed to check what weeks need to be refreshed", e)
            return MediatorResult.Error(e)
        }
        if (data == null) {
            return MediatorResult.Success(endOfPaginationReached = false)
        }
        Log.d(TAG, "load: refreshing $data")
        return try {
            refresh(data)
            _refreshed.emit(Unit)
            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "load: failed to refresh", e)
            MediatorResult.Error(e)
        }.also { Log.d(TAG, "load: returning $it") }
    }
}
