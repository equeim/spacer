package org.equeim.spacer.donki.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class EventType(internal val stringValue: String) {
    CoronalMassEjection("CME"),
    GeomagneticStorm("GST"),
    InterplanetaryShock("IPS"),
    SolarFlare("FLR"),
    SolarEnergeticParticle("SEP"),
    MagnetopauseCrossing("MPC"),
    RadiationBeltEnhancement("RBE"),
    HighSpeedStream("HSS");

    internal companion object {
        val values = values()
    }
}

@JvmInline
@Parcelize
@Serializable
value class EventId(internal val id: String) : Parcelable {
    data class Parsed(
        val type: EventType,
        val time: Instant
    )

    fun parse(): Parsed = runCatching {
        val typeEnd = id.lastIndexOf('-')
        val typeStart = id.lastIndexOf('-', typeEnd - 1) + 1
        val typeString = id.substring(typeStart, typeEnd)
        val type = EventType.values.find { it.stringValue == typeString }
            ?: throw RuntimeException("Unknown event type string $typeString")
        val time = DateTimeFormatter.ISO_DATE_TIME.parse(id.substring(0, typeStart - 1), LocalDateTime::from).toInstant(ZoneOffset.UTC)
        Parsed(type, time)
    }.getOrElse {
        throw RuntimeException("Failed to parse event id $this", it)
    }
}

sealed interface Event {
    val id: EventId
    val type: EventType
    val time: Instant
    val link: String
    val linkedEvents: List<EventId>

    fun toEventSummary(): EventSummary
}

sealed interface EventSummary {
    val id: EventId
    val type: EventType
    val time: Instant
}
