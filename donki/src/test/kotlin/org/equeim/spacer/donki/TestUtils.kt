// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import io.mockk.MockKMatcherScope
import org.equeim.spacer.donki.data.Week
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

fun instantOf(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, zoneOffset: ZoneOffset = ZoneOffset.UTC): Instant =
    LocalDateTime.of(year, month, dayOfMonth, hour, minute).toInstant(zoneOffset)

internal fun MockKMatcherScope.anyWeek(): Week = Week(any())

fun timeZoneParameters(): List<ZoneId> = setOf(
    ZoneId.ofOffset("UTC", ZoneOffset.UTC),
    ZoneId.systemDefault(),
    ZoneId.of("Asia/Yakutsk"),
    ZoneId.of("America/Los_Angeles")
).toList()
