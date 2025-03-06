// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.equeim.spacer.donki.data.common.InstantConverters
import org.equeim.spacer.donki.data.events.cache.entities.CachedEvent
import org.equeim.spacer.donki.data.events.cache.entities.CachedEventsDao
import org.equeim.spacer.donki.data.events.cache.entities.CachedEventsWeek
import org.equeim.spacer.donki.data.events.cache.entities.CachedEventsWeeksDao
import org.equeim.spacer.donki.data.events.cache.entities.CoronalMassEjectionDao
import org.equeim.spacer.donki.data.events.cache.entities.CoronalMassEjectionExtras
import org.equeim.spacer.donki.data.events.cache.entities.GeomagneticStormDao
import org.equeim.spacer.donki.data.events.cache.entities.GeomagneticStormExtras
import org.equeim.spacer.donki.data.events.cache.entities.InterplanetaryShockDao
import org.equeim.spacer.donki.data.events.cache.entities.InterplanetaryShockExtras
import org.equeim.spacer.donki.data.events.cache.entities.SolarFlareDao
import org.equeim.spacer.donki.data.events.cache.entities.SolarFlareExtras

@Database(
    entities = [
        CachedEventsWeek::class,
        CachedEvent::class,
        CoronalMassEjectionExtras::class,
        GeomagneticStormExtras::class,
        InterplanetaryShockExtras::class,
        SolarFlareExtras::class
    ],
    exportSchema = false,
    version = 1
)
@TypeConverters(InstantConverters::class, EventTypeConverters::class)
internal abstract class EventsCacheDatabase : RoomDatabase() {
    abstract fun cachedWeeks(): CachedEventsWeeksDao
    abstract fun events(): CachedEventsDao
    abstract fun coronalMassEjection(): CoronalMassEjectionDao
    abstract fun geomagneticStorm(): GeomagneticStormDao
    abstract fun interplanetaryShock(): InterplanetaryShockDao
    abstract fun solarFlare(): SolarFlareDao

    companion object {
        const val NAME = "donki-cache"
    }
}
