// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network.json

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import java.time.Instant

@Immutable
sealed interface Event {
    val id: EventId
    val type: EventType
    val time: Instant
    val link: String
    val linkedEvents: List<EventId>

    fun toEventSummary(): EventSummary = EventSummaryWithoutExtras(
        id = id,
        time = time,
        type = type
    )
}

@Immutable
interface EventSummary {
    val id: EventId
    val type: EventType
    val time: Instant
}

internal data class EventSummaryWithoutExtras(
    override val id: EventId,
    override val type: EventType,
    override val time: Instant
) : EventSummary

@Suppress("UNCHECKED_CAST")
internal fun EventType.eventSerializer(): KSerializer<Event> = when (this) {
    EventType.CoronalMassEjection -> CoronalMassEjection.serializer()
    EventType.GeomagneticStorm -> GeomagneticStorm.serializer()
    EventType.HighSpeedStream -> HighSpeedStream.serializer()
    EventType.InterplanetaryShock -> InterplanetaryShock.serializer()
    EventType.MagnetopauseCrossing -> MagnetopauseCrossing.serializer()
    EventType.RadiationBeltEnhancement -> RadiationBeltEnhancement.serializer()
    EventType.SolarEnergeticParticle -> SolarEnergeticParticle.serializer()
    EventType.SolarFlare -> SolarFlare.serializer()
} as KSerializer<Event>
