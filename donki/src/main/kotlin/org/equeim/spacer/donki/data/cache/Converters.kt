// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.cache

import androidx.room.TypeConverter
import org.equeim.spacer.donki.data.model.EventType
import java.time.Instant

internal object Converters {
    @TypeConverter
    fun instantToEpoch(instant: Instant): Long = instant.epochSecond

    @TypeConverter
    fun instantFromEpoch(long: Long): Instant = Instant.ofEpochSecond(long)

    @TypeConverter
    fun eventTypeToString(eventType: EventType): String = eventType.stringValue

    @TypeConverter
    fun eventTypeFromString(eventType: String): EventType = EventType.entries.first { it.stringValue == eventType }
}
