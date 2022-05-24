package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.format.DateTimeFormatter

internal object InstantSerializer : KSerializer<Instant> {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

    override val descriptor =
        PrimitiveSerialDescriptor(InstantSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant =
        formatter.parse(decoder.decodeString(), Instant::from)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(formatter.format(value))
}
