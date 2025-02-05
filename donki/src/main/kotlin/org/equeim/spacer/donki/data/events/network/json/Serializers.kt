// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.network.json.units.Angle
import org.equeim.spacer.donki.data.events.network.json.units.Coordinates
import java.time.Instant
import java.time.format.DateTimeFormatter

internal object InstantSerializer : KSerializer<Instant> {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

    override val descriptor =
        PrimitiveSerialDescriptor(InstantSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant =
        formatter.parse(decoder.decodeString(), Instant::from)

    override fun serialize(encoder: Encoder, value: Instant) = throw NotImplementedError()
}

internal object LinkedEventsSerializer : KSerializer<List<EventId>> {
    @Serializable
    private data class LinkedEventJson(@SerialName("activityID") val id: EventId)

    private val actualSerializer = serializer<List<LinkedEventJson>>()
    override val descriptor = actualSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<EventId> {
        return decoder.decodeSerializableValue(actualSerializer).map(LinkedEventJson::id)
    }

    override fun serialize(encoder: Encoder, value: List<EventId>) = throw NotImplementedError()
}

internal object InstrumentsSerializer : KSerializer<List<String>> {
    @Serializable
    private data class InstrumentJson(@SerialName("displayName") val displayName: String)

    private val actualSerializer = serializer<List<InstrumentJson>>()
    override val descriptor = actualSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        return decoder.decodeSerializableValue(actualSerializer).map(InstrumentJson::displayName)
    }

    override fun serialize(encoder: Encoder, value: List<String>) = throw NotImplementedError()
}

internal object SourceLocationSerializer : KSerializer<Coordinates?> {
    override val descriptor = PrimitiveSerialDescriptor(
        SourceLocationSerializer::class.qualifiedName!!,
        PrimitiveKind.STRING
    )

    private const val NOT_AVAILABLE = "NA"
    private const val POSITIVE_LATITUDE_HEMISPHERE = 'N'
    private const val NEGATIVE_LATITUDE_HEMISPHERE = 'S'
    private const val POSITIVE_LONGITUDE_HEMISPHERE = 'E'
    private const val NEGATIVE_LONGITUDE_HEMISPHERE = 'W'
    private val longitudeHemispheres = charArrayOf(POSITIVE_LONGITUDE_HEMISPHERE, NEGATIVE_LONGITUDE_HEMISPHERE)

    override fun deserialize(decoder: Decoder): Coordinates? {
        val string = decoder.decodeString()
        if (string.isEmpty() || string == NOT_AVAILABLE) return null
        val latitudeHemisphere = string[0]
        when (latitudeHemisphere) {
            POSITIVE_LATITUDE_HEMISPHERE, NEGATIVE_LATITUDE_HEMISPHERE -> Unit
            else -> fail(string)
        }
        val longitudeHemisphereIndex = string.indexOfAny(longitudeHemispheres)
        if (longitudeHemisphereIndex == -1) fail(string)
        val longitudeHemisphere = string[longitudeHemisphereIndex]
        val latitude = string.substring(1, longitudeHemisphereIndex).toFloat().let {
            if (latitudeHemisphere == NEGATIVE_LATITUDE_HEMISPHERE && it != 0.0f) -it else it
        }
        val longitude = string.substring(longitudeHemisphereIndex + 1).toFloat().let {
            if (longitudeHemisphere == NEGATIVE_LONGITUDE_HEMISPHERE && it != 0.0f) -it else it
        }
        return Coordinates(Angle.ofDegrees(latitude), Angle.ofDegrees(longitude))
    }

    private fun fail(string: String): Nothing {
        throw SerializationException("Failed to deserialize string '$string' as coordinates")
    }

    override fun serialize(encoder: Encoder, value: Coordinates?) = throw NotImplementedError()
}
