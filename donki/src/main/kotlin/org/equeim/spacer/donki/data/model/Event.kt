package org.equeim.spacer.donki.data.model

import java.time.Instant

sealed interface EventSummary {
    val id: String
    val type: EventType
    val time: Instant
}

sealed interface EventDetails {
    val id: String
    val type: EventType
    val time: Instant
    val link: String
    val linkedEvents: List<LinkedEvent>

    @JvmInline
    value class LinkedEvent(val id: String)
}

enum class EventType {
    CoronalMassEjection,
    GeomagneticStorm,
    InterplanetaryShock,
    SolarFlare,
    SolarEnergeticParticle,
    MagnetopauseCrossing,
    RadiationBeltEnhancement,
    HighSpeedStream
}
