// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings
import org.equeim.spacer.DonkiSystemNotificationsManager
import org.equeim.spacer.DonkiSystemNotificationsWorker
import org.equeim.spacer.getDonkiNotificationsRepositoryInstance
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val activityLifecycleState = MutableStateFlow(Lifecycle.State.INITIALIZED)
    val isOnDonkiNotificationsScreen = MutableStateFlow(false)

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        viewModelScope.launch { enqueueOrCancelSystemNotificationsWorker() }
        viewModelScope.launch { updateDonkiNotificationsOnLifecycleStart() }
    }

    suspend fun updateDonkiNotificationsOnLifecycleStart() {
        activityLifecycleState.filter { it == Lifecycle.State.STARTED }.collect {
            Log.d(TAG, "updateDonkiNotificationsOnLifecycleStart: activity $it")
            if (!isOnDonkiNotificationsScreen.value) {
                updateDonkiNotifications()
            } else {
                Log.d(TAG, "updateDonkiNotificationsOnLifecycleStart: on notifications screen, don't update notifications")
            }
        }
    }

    suspend fun updateDonkiNotifications() {
        Log.d(TAG, "updateDonkiNotifications() called")
        val repository = getDonkiNotificationsRepositoryInstance(getApplication())
        val result = try {
            repository.performBackgroundUpdate(includingCachedRecently = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "updateDonkiNotifications: update failed", e)
            return
        }
        if (result == null) {
            Log.d(TAG, "updateDonkiNotifications: already updating")
            return
        }
        Log.d(
            TAG,
            "updateDonkiNotifications: new unread notifications = ${result.newUnreadNotifications.map { it.id }}"
        )
        if (isOnDonkiNotificationsScreen.value) {
            Log.d(TAG, "updateDonkiNotifications: on notifications screen, don't show system notifications")
            return
        }
        if (result.newUnreadNotifications.isNotEmpty()) {
            DonkiSystemNotificationsManager(getApplication()).showNotifications(result.newUnreadNotifications)
        }
    }


    private suspend fun enqueueOrCancelSystemNotificationsWorker() {
        val settings = AppSettings(getApplication())
        val workManager = WorkManager.getInstance(getApplication())

        val updateIntervalFlow = combine(
            settings.backgroundNotificationsUpdateInterval.flow(),
            settings.backgroundNotificationsEnabledTypes.flow()
        ) { interval, types ->
            if (types.isNotEmpty()) interval else null
        }.distinctUntilChanged()

        updateIntervalFlow.collect { interval ->
            if (interval != null) {
                Log.d(
                    TAG,
                    "enqueueOrCancelSystemNotificationsWorker: enqueuing system notifications worker with interval ${interval.toKotlinDuration()}"
                )
                try {
                    workManager.enqueueUniquePeriodicWork(
                        uniqueWorkName = DonkiSystemNotificationsWorker.WORK_NAME,
                        existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                        request = PeriodicWorkRequestBuilder<DonkiSystemNotificationsWorker>(interval)
                            .setInitialDelay(interval)
                            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                            .build()
                    ).await()
                    workManager.getWorkInfosForUniqueWorkFlow(DonkiSystemNotificationsWorker.WORK_NAME)
                        .first()
                        .firstOrNull()
                        ?.nextScheduleTimeMillis
                        ?.takeIf { it != Long.MAX_VALUE }
                        ?.let {
                            Log.d(TAG, "enqueueOrCancelSystemNotificationsWorker: worker is scheduled to run in ${(it - System.currentTimeMillis()).milliseconds}")
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "enqueueOrCancelSystemNotificationsWorker: failed to enqueue system notifications worker",
                        e
                    )
                }
            } else {
                Log.d(TAG, "enqueueOrCancelSystemNotificationsWorker: cancelling system notifications worker")
                workManager.cancelUniqueWork(DonkiSystemNotificationsWorker.WORK_NAME)
            }
        }
    }

    private companion object {
        const val TAG = "MainActivityViewModel"
    }
}