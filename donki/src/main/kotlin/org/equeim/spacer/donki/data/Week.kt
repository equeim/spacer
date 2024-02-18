// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data

import androidx.annotation.VisibleForTesting
import org.equeim.spacer.donki.data.model.EventType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.time.temporal.WeekFields

@JvmInline
internal value class Week @VisibleForTesting constructor(
    val firstDay: LocalDate,
) : Comparable<Week> {
    val lastDay: LocalDate
        get() = firstDay.with(ChronoField.DAY_OF_WEEK, 7)

    val weekBasedYear: Int
        get() = firstDay[WeekFields.ISO.weekBasedYear()]

    val weekOfWeekBasedYear: Int
        get() = firstDay[WeekFields.ISO.weekOfWeekBasedYear()]

    override fun toString() =
        "Week(firstDay=$firstDay, lastDay=$lastDay, weekBasedYear=$weekBasedYear, weekOfWeekBasedYear=$weekOfWeekBasedYear)"

    fun getFirstDayInstant(): Instant = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)
    fun getInstantAfterLastDay(): Instant = firstDay
        .plusWeeks(1)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)

    // Back to the future
    fun prev(currentWeek: Week, dateRange: DonkiRepository.DateRange?): Week? {
        if (this == currentWeek) return null
        val futureWeek = Week(firstDay.plusWeeks(1))
        return if (dateRange == null || futureWeek.getFirstDayInstant() < dateRange.instantAfterLastDay) {
            futureWeek
        } else {
            null
        }
    }

    // Forward to the past
    fun next(dateRange: DonkiRepository.DateRange?): Week? {
        val pastWeek = next()
        return if (dateRange == null || pastWeek.getInstantAfterLastDay() > dateRange.firstDayInstant) {
            pastWeek
        } else {
            null
        }
    }

    internal fun next(): Week = Week(firstDay.minusWeeks(1))

    override fun compareTo(other: Week) = firstDay.compareTo(other.firstDay)

    companion object {
        fun getCurrentWeek(clock: Clock): Week = fromInstant(Instant.now(clock))

        private const val INITIAL_LOAD_WEEKS_COUNT = 3

        fun getInitialLoadWeeks(currentWeek: Week): List<Week> =
            (0 until INITIAL_LOAD_WEEKS_COUNT - 1).runningFold(currentWeek) { week, _ -> week.next() }

        fun getInitialLoadWeeks(clock: Clock): List<Week> =
            getInitialLoadWeeks(getCurrentWeek(clock))

        fun getInitialLoadWeeksFromTimeRange(dateRange: DonkiRepository.DateRange): List<Week> {
            val firstWeek = fromInstant(dateRange.firstDayInstant)
            val lastWeek = dateRange.instantAfterLastDay.atOffset(ZoneOffset.UTC)?.let { endTimeExclusive ->
                var dayOfLastWeek = endTimeExclusive.toLocalDate()
                if (endTimeExclusive.toLocalTime() == LocalTime.MIDNIGHT) {
                    dayOfLastWeek = dayOfLastWeek.minusDays(1)
                }
                Week(dayOfLastWeek.with(ChronoField.DAY_OF_WEEK, 1))
            }
            if (lastWeek == firstWeek) return listOf(lastWeek)
            return buildList {
                addAll(generateSequence(lastWeek) {
                    val next = it.next()
                    if (next == firstWeek) null else next
                })
                add(firstWeek)
            }
        }

        fun fromInstant(instant: Instant): Week = Week(instant.atOffset(ZoneOffset.UTC).toLocalDate().with(ChronoField.DAY_OF_WEEK, 1))
    }
}

internal fun List<Week>.forTypes(eventTypes: Set<EventType>): Sequence<Pair<Week, EventType>> =
    asSequence().flatMap { it.forTypes(eventTypes) }

internal fun Week.forTypes(eventTypes: Set<EventType>): Sequence<Pair<Week, EventType>> =
    eventTypes.asSequence().map { type -> this to type }
