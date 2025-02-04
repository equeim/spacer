// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import androidx.annotation.VisibleForTesting
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoField

@JvmInline
internal value class Week @VisibleForTesting constructor(
    val firstDay: LocalDate,
) : Comparable<Week> {
    val lastDay: LocalDate
        get() = firstDay.with(ChronoField.DAY_OF_WEEK, 7)

    override fun toString() =
        "Week(firstDay=$firstDay, lastDay=$lastDay)"

    fun getFirstDayInstant(): Instant = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)
    fun getInstantAfterLastDay(): Instant = firstDay
        .plusWeeks(1)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)

    // Back to the future
    fun futureWeek(currentWeek: Week, dateRange: DateRange?): Week? {
        if (this == currentWeek) return null
        val futureWeek = Week(firstDay.plusWeeks(1))
        return if (dateRange == null || futureWeek.getFirstDayInstant() < dateRange.instantAfterLastDay) {
            futureWeek
        } else {
            null
        }
    }

    // Forward to the past
    fun pastWeek(dateRange: DateRange?): Week? {
        val pastWeek = Week(firstDay.minusWeeks(1))
        return if (dateRange == null || pastWeek.getInstantAfterLastDay() > dateRange.firstDayInstant) {
            pastWeek
        } else {
            null
        }
    }

    override fun compareTo(other: Week) = firstDay.compareTo(other.firstDay)

    companion object {
        fun getCurrentWeek(clock: Clock): Week = fromInstant(Instant.now(clock))
        fun fromLocalDate(localDate: LocalDate): Week = Week(localDate.with(ChronoField.DAY_OF_WEEK, 1))
        fun fromInstant(instant: Instant): Week = fromLocalDate(instant.atOffset(ZoneOffset.UTC).toLocalDate())
    }
}
