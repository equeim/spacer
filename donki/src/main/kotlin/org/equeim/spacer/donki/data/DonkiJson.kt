// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.equeim.spacer.donki.data.model.CoronalMassEjection
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.model.GeomagneticStorm
import org.equeim.spacer.donki.data.model.HighSpeedStream
import org.equeim.spacer.donki.data.model.InterplanetaryShock
import org.equeim.spacer.donki.data.model.MagnetopauseCrossing
import org.equeim.spacer.donki.data.model.RadiationBeltEnhancement
import org.equeim.spacer.donki.data.model.SolarEnergeticParticle
import org.equeim.spacer.donki.data.model.SolarFlare

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
