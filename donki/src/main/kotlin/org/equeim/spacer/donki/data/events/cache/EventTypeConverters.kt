// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache

import androidx.room3.ColumnTypeConverter
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection.CmeType
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection.EarthImpactType

internal object EventTypeConverters {
    @ColumnTypeConverter
    fun eventTypeToString(eventType: EventType): String = eventType.stringValue

    @ColumnTypeConverter
    fun eventTypeFromString(eventType: String): EventType = EventType.entries.first { it.stringValue == eventType }

    @ColumnTypeConverter
    fun earthImpactTypeToInt(earthImpactType: EarthImpactType): Int = earthImpactType.integerValue

    @ColumnTypeConverter
    fun earthImpactTypeFromInt(earthImpactType: Int): EarthImpactType = EarthImpactType.entries.first { it.integerValue == earthImpactType }

    @ColumnTypeConverter
    fun cmeTypeToString(cmeType: CmeType): String = cmeType.stringValue

    @ColumnTypeConverter
    fun cmeTypeFromString(cmeType: String): CmeType = CmeType.entries.first { it.stringValue == cmeType }
}
