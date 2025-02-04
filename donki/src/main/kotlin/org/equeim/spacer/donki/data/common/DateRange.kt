// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@Immutable
@Parcelize
data class DateRange(
    val firstDayInstant: Instant,
    val instantAfterLastDay: Instant,
): Parcelable {
    val lastDayInstant: Instant get() = instantAfterLastDay - Duration.ofDays(1)

    internal val lastWeek: Week
        get() = instantAfterLastDay.atOffset(ZoneOffset.UTC).let {
            Week.fromLocalDate(
                if (it.toLocalTime() == LocalTime.MIDNIGHT) {
                    it.toLocalDate().minusDays(1)
                } else {
                    it.toLocalDate()
                }
            )
        }

    internal fun coerceToWeek(week: Week): DateRange {
        val weekFirstDayInstant = week.getFirstDayInstant()
        val weekInstantAfterLastDay = week.getInstantAfterLastDay()
        return DateRange(
            firstDayInstant = firstDayInstant.coerceIn(
                weekFirstDayInstant,
                weekInstantAfterLastDay.minusNanos(1)
            ),
            instantAfterLastDay = instantAfterLastDay.coerceIn(
                weekFirstDayInstant,
                weekInstantAfterLastDay
            )
        )
    }
}

internal fun DateRange.intersect(other: DateRange): Boolean =
    firstDayInstant < other.instantAfterLastDay && other.firstDayInstant < instantAfterLastDay
