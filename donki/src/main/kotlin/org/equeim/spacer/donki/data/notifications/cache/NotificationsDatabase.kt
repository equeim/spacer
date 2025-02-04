// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.equeim.spacer.donki.data.common.InstantConverters

@Database(
    entities = [CachedNotification::class, CachedNotificationsWeek::class],
    exportSchema = false,
    version = 1
)
@TypeConverters(InstantConverters::class, NotificationTypeConverters::class)
internal abstract class NotificationsDatabase : RoomDatabase() {
    abstract fun cachedWeeks(): CachedNotificationsWeeksDao
    abstract fun cachedNotifications(): CachedNotificationsDao

    companion object {
        const val NAME = "notifications"
    }
}
