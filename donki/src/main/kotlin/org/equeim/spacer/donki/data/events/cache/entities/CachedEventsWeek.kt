// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache.entities

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.events.EventType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.toKotlinDuration

@Entity(
    tableName = "cached_weeks",
    primaryKeys = ["time_at_start_of_first_day", "event_type"]
)
internal data class CachedEventsWeek(
    @ColumnInfo(name = "time_at_start_of_first_day")
    val timeAtStartOfFirstDay: Instant,
    @ColumnInfo(name = "event_type")
    val eventType: EventType,
    @ColumnInfo(name = "load_time")
    val loadTime: Instant
) {
    fun toLogString(clock: Clock): String =
        "First day of week: ${timeAtStartOfFirstDay.atOffset(ZoneOffset.UTC).toLocalDate()}, event type: $eventType, loaded ${Duration.between(loadTime, clock.instant()).toKotlinDuration()} ago"

    fun toDateRange(): DateRange = DateRange(timeAtStartOfFirstDay, timeAtStartOfFirstDay + Duration.ofDays(7))
}

@Dao
internal interface CachedEventsWeeksDao {
    @Query(
        """
            SELECT count(*) FROM cached_weeks
            WHERE time_at_start_of_first_day = :timeAtStartOfFirstDay AND event_type = :eventType
        """
    )
    suspend fun isWeekCached(timeAtStartOfFirstDay: Instant, eventType: EventType): Boolean

    @Query(
        """
            SELECT load_time FROM cached_weeks
            WHERE time_at_start_of_first_day = :timeAtStartOfFirstDay AND event_type = :eventType
        """
    )
    suspend fun getWeekLoadTime(timeAtStartOfFirstDay: Instant, eventType: EventType): Instant?

    @Query(
        """
            SELECT * FROM cached_weeks
            WHERE load_time < (time_at_start_of_first_day + $EVENTS_DONT_NEED_REFRESH_THRESHOLD_SECONDS)
        """
    )
    fun getWeeksThatNeedRefresh(): Flow<List<CachedEventsWeek>>

    @Insert(onConflict = REPLACE)
    suspend fun updateWeek(cachedWeek: CachedEventsWeek)
}

// Duration in seconds between start of the week and the time week doesn't need refresh anymore
internal const val EVENTS_DONT_NEED_REFRESH_THRESHOLD_SECONDS: Long = 1209600 // 14 days
