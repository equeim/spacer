// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@JvmInline
@Parcelize
@Serializable
value class EventId(val stringValue: String) : Parcelable {
    data class Parsed(
        val type: EventType,
        val time: Instant
    )

    fun parse(): Parsed = runCatching {
        val typeEnd = stringValue.lastIndexOf('-')
        val typeStart = stringValue.lastIndexOf('-', typeEnd - 1) + 1
        val typeString = stringValue.substring(typeStart, typeEnd)
        val type = EventType.entries.find { it.stringValue == typeString }
            ?: throw RuntimeException("Unknown event type string $typeString")
        val time = DateTimeFormatter.ISO_DATE_TIME.parse(stringValue.substring(0, typeStart - 1),
            LocalDateTime::from
        ).toInstant(ZoneOffset.UTC)
        Parsed(type, time)
    }.getOrElse {
        throw RuntimeException("Failed to parse event id $this", it)
    }
}