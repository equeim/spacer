// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.equeim.spacer.donki.data.common.DateRange
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.toKotlinDuration

@Entity(tableName = "cached_weeks")
internal data class CachedNotificationsWeek(
    @PrimaryKey
    @ColumnInfo(name = "time_at_start_of_first_day")
    val timeAtStartOfFirstDay: Instant,
    @ColumnInfo(name = "load_time")
    val loadTime: Instant
) {
    fun toLogString(clock: Clock): String =
        "First day of week: ${timeAtStartOfFirstDay.atOffset(ZoneOffset.UTC).toLocalDate()}, loaded ${Duration.between(loadTime, clock.instant()).toKotlinDuration()} ago"

    fun toDateRange(): DateRange = DateRange(timeAtStartOfFirstDay, timeAtStartOfFirstDay + Duration.ofDays(7))
}

@Dao
internal interface CachedNotificationsWeeksDao {
    @Query(
        """
            SELECT count(*) FROM cached_weeks
            WHERE time_at_start_of_first_day = :timeAtStartOfFirstDay
        """
    )
    suspend fun isWeekCached(timeAtStartOfFirstDay: Instant): Boolean

    @Query(
        """
            SELECT * FROM cached_weeks
            WHERE load_time < (time_at_start_of_first_day + $NOTIFICATIONS_DONT_NEED_REFRESH_THRESHOLD_SECONDS)
        """
    )
    fun getWeeksThatNeedRefresh(): Flow<List<CachedNotificationsWeek>>

    @Insert(onConflict = REPLACE)
    suspend fun updateWeek(cachedWeek: CachedNotificationsWeek)

    @Query(
        """
            SELECT count(*) FROM cached_weeks
        """
    )
    suspend fun haveCachedWeeks(): Boolean
}

// Duration in seconds between start of the week and the time week doesn't need refresh anymore
private const val NOTIFICATIONS_DONT_NEED_REFRESH_THRESHOLD_SECONDS: Long = 604800 // 7 days
