package org.equeim.spacer.donki.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

@JvmInline
@Parcelize
value class EventId(internal val id: String) : Parcelable {
    data class Parsed(
        val type: EventType,
        val time: Instant
    )

    fun parse(): Parsed = runCatching {
        val typeEnd = id.lastIndexOf('-')
        val typeStart = id.lastIndexOf('-', typeEnd - 1) + 1
        val type = when (val typeString = id.substring(typeStart, typeEnd)) {
            "CME" -> EventType.CoronalMassEjection
            "GST" -> EventType.GeomagneticStorm
            "IPS" -> EventType.InterplanetaryShock
            "FLR" -> EventType.SolarFlare
            "SEP" -> EventType.SolarEnergeticParticle
            "MPC" -> EventType.MagnetopauseCrossing
            "RBE" -> EventType.RadiationBeltEnhancement
            "HSS" -> EventType.HighSpeedStream
            else -> throw RuntimeException("Unknown event type string $typeString")
        }
        val time = DateTimeFormatter.ISO_DATE_TIME.parse(id.substring(0, typeStart - 1), LocalDateTime::from).toInstant(ZoneOffset.UTC)
        Parsed(type, time)
    }.getOrElse {
        throw RuntimeException("Failed to parse event id $this", it)
    }
}

sealed interface EventSummary {
    val id: EventId
    val type: EventType
    val time: Instant
}

sealed interface EventDetails {
    val id: EventId
    val type: EventType
    val time: Instant
    val link: String
    val linkedEvents: List<EventId>
}
