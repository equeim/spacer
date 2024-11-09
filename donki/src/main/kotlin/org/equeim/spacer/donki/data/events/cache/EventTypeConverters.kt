// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache

import androidx.room.TypeConverter
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection.CmeType
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection.EarthImpactType

internal object EventTypeConverters {
    @TypeConverter
    fun eventTypeToString(eventType: EventType): String = eventType.stringValue

    @TypeConverter
    fun eventTypeFromString(eventType: String): EventType = EventType.entries.first { it.stringValue == eventType }

    @TypeConverter
    fun earthImpactTypeToInt(earthImpactType: EarthImpactType): Int = earthImpactType.integerValue

    @TypeConverter
    fun earthImpactTypeFromInt(earthImpactType: Int): EarthImpactType = EarthImpactType.entries.first { it.integerValue == earthImpactType }

    @TypeConverter
    fun cmeTypeToString(cmeType: CmeType): String = cmeType.stringValue

    @TypeConverter
    fun cmeTypeFromString(cmeType: String): CmeType = CmeType.entries.first { it.stringValue == cmeType }
}
