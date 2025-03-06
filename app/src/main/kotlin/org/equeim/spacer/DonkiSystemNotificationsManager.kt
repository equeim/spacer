// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import org.equeim.spacer.donki.data.common.DonkiException
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.cache.CachedNotification
import org.equeim.spacer.ui.MainActivity
import org.equeim.spacer.ui.screens.donki.donkiErrorToString
import kotlin.random.Random

class DonkiSystemNotificationsManager(private val context: Context) {
    private val notificationManager = context.getSystemService<NotificationManager>()
    private val settings = AppSettings(context)

    init {
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null")
        } else {
            createChannel(notificationManager)
        }
    }

    fun diagnoseNotificationsIssues(): List<NotificationsIssue> =
        diagnoseNotificationsIssues(context, notificationManager)

    suspend fun showNotifications(donkiNotifications: List<CachedNotification>) {
        Log.d(
            TAG,
            "showNotifications() called with: donkiNotifications = ${donkiNotifications.map { it.id }}"
        )
        if (notificationManager == null) {
            Log.e(TAG, "showNotifications: NotificationManager is null")
            return
        }
        if (diagnoseNotificationsIssues().isNotEmpty()) {
            Log.e(TAG, "showNotifications: can't show notifications, do nothing")
            return
        }
        val enabledNotificationTypes = settings.backgroundNotificationsEnabledTypes.get()
        if (enabledNotificationTypes.isEmpty()) {
            Log.d(TAG, "showNotifications: all notification types are disabled, do nothing")
            return
        }
        Log.d(TAG, "showNotifications: enabledNotificationTypes = $enabledNotificationTypes")
        try {
            for (donkiNotification in donkiNotifications.sortedBy { it.time }) {
                if (donkiNotification.type !in enabledNotificationTypes) {
                    Log.d(
                        TAG,
                        "showNotifications: skipping notification ${donkiNotification.id}, its type is disabled"
                    )
                    continue
                }
                Log.d(TAG, "showNotifications: showing notification for $donkiNotification")
                notificationManager.notify(
                    donkiNotification.id.stringValue,
                    0,
                    Notification.Builder(context, CHANNEL_ID)
                        // TODO: icon
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(donkiNotification.title)
                        .setContentText(donkiNotification.subtitle)
                        .setContentIntent(
                            PendingIntent.getActivities(
                                context,
                                Random.Default.nextInt(),
                                arrayOf(
                                    // Make sure that OS doesn't remember Intent with notification deep link
                                    // as a base Intent for the task (when our task didn't exist when clicking on notification)
                                    // by first starting activity via launch Intent
                                    // Deep link Intent will be received through onNewIntent
                                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                                    MainActivity.createIntentForNotification(
                                        donkiNotification.id,
                                        context
                                    ),
                                ),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                        .setWhen(donkiNotification.time.toEpochMilli())
                        .setShowWhen(true)
                        .setAutoCancel(true)
                        .build()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "showNotifications: failed to show notifications", e)
        }
    }

    fun removeNotification(id: NotificationId) {
        Log.d(TAG, "removeNotification() called with: id = $id")
        if (notificationManager == null) {
            Log.e(TAG, "removeNotification: NotificationManager is null")
            return
        }
        notificationManager.cancel(id.stringValue, 0)
        Log.d(TAG, "removeNotification: removed notification")
    }

    fun showFailedUpdateNotification(error: DonkiException) {
        Log.d(TAG, "showFailedUpdateNotification() called with: error = $error")
        if (notificationManager == null) {
            Log.e(TAG, "showFailedUpdateNotification: NotificationManager is null")
            return
        }
        if (diagnoseNotificationsIssues().isNotEmpty()) {
            Log.e(TAG, "showFailedUpdateNotification: can't show notifications, do nothing")
            return
        }
        notificationManager.notify(
            0,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.warning_24px)
                .setContentTitle(context.getText(R.string.notifications_update_error))
                .setContentText(error.donkiErrorToString(context))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        Random.Default.nextInt(),
                        context.packageManager.getLaunchIntentForPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .build()
        )
    }

    enum class NotificationsIssue {
        NoPermission,
        NotificationsDisabled,
        NotificationChannelDisabled
    }

    companion object {
        private const val TAG = "DonkiSystemNotificationsManager"
        const val CHANNEL_ID = "donkiNotifications"

        private fun diagnoseNotificationsIssues(
            context: Context,
            notificationManager: NotificationManager?
        ): List<NotificationsIssue> = buildList {
            if (notificationManager == null) {
                Log.e(TAG, "diagnoseNotificationsIssues: NotificationManager is null")
                add(NotificationsIssue.NotificationsDisabled)
                return@buildList
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "diagnoseNotificationsIssues: notifications permission is not granted")
                    add(NotificationsIssue.NoPermission)
                }
            }
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !contains(NotificationsIssue.NoPermission)) && !notificationManager.areNotificationsEnabled()) {
                Log.e(TAG, "diagnoseNotificationsIssues: notifications are disabled")
                add(NotificationsIssue.NotificationsDisabled)
            }
            createChannel(notificationManager)
            val importance = notificationManager.getNotificationChannel(CHANNEL_ID)?.importance
                ?: NotificationManager.IMPORTANCE_NONE
            if (importance == NotificationManager.IMPORTANCE_NONE) {
                Log.e(TAG, "diagnoseNotificationsIssues: notification channel is disabled")
                add(NotificationsIssue.NotificationChannelDisabled)
            }
            Log.d(TAG, "diagnoseNotificationsIssues() returned: $this")
        }

        private fun createChannel(notificationManager: NotificationManager) {
            try {
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel != null) {
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "createChannel: failed to get existing notification channel", e)
            }
            Log.d(TAG, "createChannel: creating channel")
            try {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "DONKI notifications",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "createChannel: failed to create notification channel", e)
            }
        }

        fun diagnoseNotificationsIssues(context: Context): List<NotificationsIssue> =
            diagnoseNotificationsIssues(context, context.getSystemService<NotificationManager>())
    }
}
