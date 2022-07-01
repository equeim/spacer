package org.equeim.spacer.donki.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.equeim.spacer.donki.data.model.*

internal val DonkiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

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
