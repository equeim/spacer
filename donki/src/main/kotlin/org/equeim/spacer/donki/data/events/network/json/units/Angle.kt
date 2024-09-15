// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network.json.units

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(Angle.SerializerDegrees::class)
value class Angle private constructor(val degrees: Float) {
    companion object {
        fun ofDegrees(degrees: Float) = Angle(degrees)
    }

    object SerializerDegrees : KSerializer<Angle> {
        override val descriptor = PrimitiveSerialDescriptor(SerializerDegrees::class.qualifiedName!!, PrimitiveKind.FLOAT)
        override fun deserialize(decoder: Decoder) = ofDegrees(decoder.decodeFloat())
        override fun serialize(encoder: Encoder, value: Angle) = throw NotImplementedError()
    }
}
