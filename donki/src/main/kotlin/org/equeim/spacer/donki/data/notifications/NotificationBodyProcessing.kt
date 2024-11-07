// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import androidx.core.util.PatternsCompat
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import java.util.SortedSet

private val titleRegex =
    Regex("## Message Type: (?:(?:Auto-generated )?Space Weather Notification - )?(.*)")

internal fun String.findTitle(): String? = titleRegex.find(this)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

private val subtitleRegex = Regex("## Summary:\\s+(.*)")

internal fun String.findSubtitle(): String? = subtitleRegex.find(this)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

@Suppress("RegExpUnnecessaryNonCapturingGroup")
private val eventIdRegex = Regex("[A-Z0-9:-]+-(?:${EventType.entries.joinToString("|") { it.stringValue }})-\\d+")

fun String.findWebLinks(): List<Pair<String, IntRange>> =
    PatternsCompat.WEB_URL.toRegex().findAll(this).map { it.value to it.range }.toList()

fun String.findLinkedEvents(): SortedSet<Pair<EventId, EventId.Parsed>> {
    return eventIdRegex.findAll(this).mapNotNull {
        runCatching {
            val id = EventId(it.value)
            id to id.parse()
        }.getOrNull()
    }.toSortedSet(compareByDescending { it.second.time })
}
