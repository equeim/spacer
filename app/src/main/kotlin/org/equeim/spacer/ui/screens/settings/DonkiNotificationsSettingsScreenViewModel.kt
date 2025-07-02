// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings
import org.equeim.spacer.DonkiSystemNotificationsManager
import org.equeim.spacer.DonkiSystemNotificationsWorker
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.notifications.NotificationType
import java.time.Duration

class DonkiNotificationsSettingsScreenViewModel(application: Application) :
    AndroidViewModel(application) {

    private val settings = AppSettings(application)

    private val _loadedSettings = mutableStateOf(false)
    val loadedSettings: State<Boolean> by ::_loadedSettings

    lateinit var backgroundNotificationsEnabledTypes: StateFlow<Set<NotificationType>>
        private set

    lateinit var backgroundNotificationsUpdateInterval: StateFlow<Duration>
        private set

    private val _issues = mutableStateOf<List<Issue>>(emptyList())
    val issues: State<List<Issue>> by ::_issues

    @Immutable
    data class Issue(
        @param:StringRes val text: Int,
        @param:StringRes val actionText: Int,
        val actionIntent: Intent
    ) {
        override fun equals(other: Any?): Boolean = if (other is Issue) {
            text == other.text
        } else {
            false
        }

        override fun hashCode(): Int = text.hashCode()
    }

    init {
        diagnoseIssues()
        viewModelScope.launch {
            coroutineScope {
                launch {
                    backgroundNotificationsEnabledTypes =
                        settings.backgroundNotificationsEnabledTypes.flow().stateIn(viewModelScope)
                }
                launch {
                    backgroundNotificationsUpdateInterval =
                        settings.backgroundNotificationsUpdateInterval.flow().stateIn(viewModelScope)
                }
            }
            _loadedSettings.value = true
        }
    }

    fun setBackgroundNotificationsEnabledTypes(types: Set<NotificationType>) {
        Log.d(TAG, "setBackgroundNotificationsEnabledTypes() called with: types = $types")
        settings.backgroundNotificationsEnabledTypes.set(types)
        // If user touches notifications settings then we don't need to ask them about enabling notifications anymore
        settings.askedAboutEnablingNotifications.set(true)
    }

    fun setBackgroundNotificationsUpdateInterval(interval: Duration) {
        Log.d(TAG, "setBackgroundNotificationsUpdateInterval() called with: interval = $interval")
        settings.backgroundNotificationsUpdateInterval.set(interval)
    }

    fun onActivityResumed() {
        Log.d(TAG, "onActivityResumed() called")
        diagnoseIssues()
    }

    private fun diagnoseIssues() {
        _issues.value = buildList {
            val context = getApplication<Application>()
            for (issue in DonkiSystemNotificationsManager.diagnoseNotificationsIssues(context)) {
                add(
                    when (issue) {
                        DonkiSystemNotificationsManager.NotificationsIssue.NoPermission -> Issue(
                            text = R.string.no_notifications_permission,
                            actionText = R.string.grant_permission,
                            actionIntent = createAppNotificationSettingsIntent(context)
                        )

                        DonkiSystemNotificationsManager.NotificationsIssue.NotificationsDisabled -> Issue(
                            text = R.string.notifications_disabled,
                            actionText = R.string.enable_notifications,
                            actionIntent = createAppNotificationSettingsIntent(context)
                        )

                        DonkiSystemNotificationsManager.NotificationsIssue.NotificationChannelDisabled -> Issue(
                            text = R.string.notification_channel_disabled,
                            actionText = R.string.enable_notification_channel,
                            actionIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putPackageAsExtra(context)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, DonkiSystemNotificationsManager.CHANNEL_ID)
                        )
                    }
                )
            }
            for (issue in DonkiSystemNotificationsWorker.diagnoseBackgroundUpdateIssues(context)) {
                add(
                    when (issue) {
                        DonkiSystemNotificationsWorker.BackgroundUpdateIssue.NotIgnoringBatteryOptimizations -> Issue(
                            text = R.string.not_ignoring_battery_optimization,
                            actionText = R.string.disable_battery_optimization,
                            actionIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )

                        DonkiSystemNotificationsWorker.BackgroundUpdateIssue.RestrictedBackgroundDataUsage -> Issue(
                            text = R.string.restricted_background_data_usage,
                            actionText = R.string.allow_background_data_usage,
                            actionIntent = Intent(
                                Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                                createUriForPackage(context)
                            )
                        )
                    }
                )
            }
        }
    }

    private companion object {
        const val TAG = "NotificationsSettingsScreenViewModel"

        fun Intent.putPackageAsExtra(context: Context): Intent =
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        fun createUriForPackage(context: Context): Uri =
            Uri.Builder().scheme("package").opaquePart(context.packageName).build()

        fun createAppNotificationSettingsIntent(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putPackageAsExtra(context)
    }
}
