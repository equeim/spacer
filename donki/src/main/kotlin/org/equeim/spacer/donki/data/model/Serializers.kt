package org.equeim.spacer.donki.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
