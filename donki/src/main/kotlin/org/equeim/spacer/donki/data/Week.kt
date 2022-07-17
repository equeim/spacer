package org.equeim.spacer.donki.data

import androidx.annotation.VisibleForTesting
import java.time.*
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
        fun getCurrentWeek(clock: Clock): Week {
            return Week(
                Instant.now(clock)
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDate()
                    .with(ChronoField.DAY_OF_WEEK, 1)
            )
        }

        private const val INITIAL_LOAD_WEEKS_COUNT = 3

        fun getInitialLoadWeeks(currentWeek: Week): List<Week> =
            (0 until INITIAL_LOAD_WEEKS_COUNT - 1).runningFold(currentWeek) { week, _ -> week.next() }

        fun getInitialLoadWeeks(clock: Clock): List<Week> =
            getInitialLoadWeeks(getCurrentWeek(clock))
    }
}
