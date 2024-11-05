// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
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
import java.time.Instant

@Entity(tableName = "cached_weeks")
internal data class CachedNotificationsWeek(
    @PrimaryKey
    @ColumnInfo(name = "time_at_start_of_first_day")
    val timeAtStartOfFirstDay: Instant,
    @ColumnInfo(name = "load_time")
    val loadTime: Instant
)

@Dao
internal interface CachedNotificationsWeeksDao {
    @Query(
        """
            SELECT load_time FROM cached_weeks
            WHERE time_at_start_of_first_day = :timeAtStartOfFirstDay
        """
    )
    suspend fun getWeekLoadTime(timeAtStartOfFirstDay: Instant): Instant?

    @Insert(onConflict = REPLACE)
    suspend fun updateWeek(cachedWeek: CachedNotificationsWeek)
}
