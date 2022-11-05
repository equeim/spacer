// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data

import androidx.annotation.VisibleForTesting
import org.equeim.spacer.donki.data.model.EventType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.time.temporal.WeekFields

@JvmInline
internal value class Week @VisibleForTesting constructor(
    val firstDay: LocalDate
) : Comparable<Week> {
    val lastDay: LocalDate
        get() = firstDay.with(ChronoField.DAY_OF_WEEK, 7)

    val weekBasedYear: Int
        get() = firstDay[WeekFields.ISO.weekBasedYear()]

    val weekOfWeekBasedYear: Int
        get() = firstDay[WeekFields.ISO.weekOfWeekBasedYear()]

    override fun toString() = "Week(firstDay=$firstDay, lastDay=$lastDay, weekBasedYear=$weekBasedYear, weekOfWeekBasedYear=$weekOfWeekBasedYear)"

    fun getFirstDayInstant(): Instant = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)
    fun getInstantAfterLastDay(): Instant = firstDay
        .plusWeeks(1)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)

    // Back to the future
    fun prev(currentWeek: Week): Week? {
        return if (this == currentWeek) {
            null
        } else {
            Week(firstDay.plusWeeks(1))
        }
    }

    // Forward to the past
    fun next(): Week {
        return Week(firstDay.minusWeeks(1))
    }

    override fun compareTo(other: Week) = firstDay.compareTo(other.firstDay)

    companion object {
        fun getCurrentWeek(clock: Clock): Week = fromInstant(Instant.now(clock))

        private const val INITIAL_LOAD_WEEKS_COUNT = 3

        fun getInitialLoadWeeks(currentWeek: Week): List<Week> =
            (0 until INITIAL_LOAD_WEEKS_COUNT - 1).runningFold(currentWeek) { week, _ -> week.next() }

        fun getInitialLoadWeeks(clock: Clock): List<Week> =
            getInitialLoadWeeks(getCurrentWeek(clock))

        fun fromInstant(instant: Instant) = Week(
            instant
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .with(ChronoField.DAY_OF_WEEK, 1)
        )
    }
}

internal fun List<Week>.forTypes(eventTypes: List<EventType>): Sequence<Pair<Week, EventType>> =
    asSequence().flatMap { it.forTypes(eventTypes) }

internal fun Week.forTypes(eventTypes: List<EventType>): Sequence<Pair<Week, EventType>> =
    eventTypes.asSequence().map { type -> this to type }
