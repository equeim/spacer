// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache.entities

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import org.equeim.spacer.donki.data.events.EventType
import java.time.Instant

@Entity(
    tableName = "cached_weeks",
    primaryKeys = ["week_based_year", "week_of_week_based_year", "event_type"]
)
internal data class CachedEventsWeek(
    @ColumnInfo(name = "week_based_year")
    val weekBasedYear: Int,
    @ColumnInfo(name = "week_of_week_based_year")
    val weekOfWeekBasedYear: Int,
    @ColumnInfo(name = "event_type")
    val eventType: EventType,
    @ColumnInfo(name = "load_time")
    val loadTime: Instant
)

@Dao
internal interface CachedEventsWeeksDao {
    @Query(
        """
            SELECT load_time FROM cached_weeks
            WHERE week_based_year = :weekBasedYear AND week_of_week_based_year = :weekOfWeekBasedYear AND event_type = :eventType
        """
    )
    suspend fun getWeekLoadTime(weekBasedYear: Int, weekOfWeekBasedYear: Int, eventType: EventType): Instant?

    @Insert(onConflict = REPLACE)
    suspend fun updateWeek(cachedWeek: CachedEventsWeek)
}
