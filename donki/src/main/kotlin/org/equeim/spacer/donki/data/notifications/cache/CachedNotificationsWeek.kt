// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import java.time.Instant

@Entity(
    tableName = "cached_weeks",
    primaryKeys = ["week_based_year", "week_of_week_based_year"]
)
internal data class CachedNotificationsWeek(
    @ColumnInfo(name = "week_based_year")
    val weekBasedYear: Int,
    @ColumnInfo(name = "week_of_week_based_year")
    val weekOfWeekBasedYear: Int,
    @ColumnInfo(name = "load_time")
    val loadTime: Instant
)

@Dao
internal interface CachedNotificationsWeeksDao {
    @Query(
        """
            SELECT load_time FROM cached_weeks
            WHERE week_based_year = :weekBasedYear AND week_of_week_based_year = :weekOfWeekBasedYear
        """
    )
    suspend fun getWeekLoadTime(weekBasedYear: Int, weekOfWeekBasedYear: Int): Instant?

    @Insert(onConflict = REPLACE)
    suspend fun updateWeek(cachedWeek: CachedNotificationsWeek)
}
