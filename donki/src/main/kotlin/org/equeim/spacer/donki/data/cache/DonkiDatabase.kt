// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.equeim.spacer.donki.data.cache.entities.*

@Database(
    entities = [
        CachedWeek::class,
        CachedEvent::class,
        CoronalMassEjectionExtras::class,
        GeomagneticStormExtras::class,
        InterplanetaryShockExtras::class
    ],
    exportSchema = false,
    version = 1
)
@TypeConverters(Converters::class)
internal abstract class DonkiDatabase : RoomDatabase() {
    abstract fun cachedWeeks(): CachedWeeksDao
    abstract fun events(): CachedEventsDao
    abstract fun coronalMassEjection(): CoronalMassEjectionDao
    abstract fun geomagneticStorm(): GeomagneticStormDao
    abstract fun interplanetaryShock(): InterplanetaryShockDao

    companion object {
        const val NAME = "donki-cache"
    }
}
