// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache

import androidx.room.TypeConverter
import org.equeim.spacer.donki.data.events.EventType

internal object EventTypeConverters {
    @TypeConverter
    fun eventTypeToString(eventType: EventType): String = eventType.stringValue

    @TypeConverter
    fun eventTypeFromString(eventType: String): EventType = EventType.entries.first { it.stringValue == eventType }
}
