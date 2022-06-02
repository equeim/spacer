package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.spacer.donki.data.model.EventId

internal object EventIdSerializer : KSerializer<EventId> {
    override val descriptor = PrimitiveSerialDescriptor(EventIdSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder) = EventId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: EventId) {
        encoder.encodeString(value.id)
    }
}
