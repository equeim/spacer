// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.room3.ColumnTypeConverter
import org.equeim.spacer.donki.data.notifications.NotificationType

@Suppress("unused")
internal object NotificationTypeConverters {
    @ColumnTypeConverter
    fun toString(type: NotificationType): String = type.stringValue

    @ColumnTypeConverter
    fun fromString(string: String): NotificationType =
        NotificationType.entries.find { it.stringValue == string }
            ?: throw IllegalArgumentException("Failed to convert notification type $string")
}
