// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.room.TypeConverter
import org.equeim.spacer.donki.data.notifications.NotificationType

@Suppress("unused")
internal object NotificationTypeConverters {
    @TypeConverter
    fun toString(type: NotificationType): String = type.stringValue

    @TypeConverter
    fun fromString(string: String): NotificationType =
        NotificationType.entries.find { it.stringValue == string }
            ?: throw IllegalArgumentException("Failed to convert notification type $string")
}
