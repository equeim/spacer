// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.model.units

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(Speed.SerializerKilometersPerSecond::class)
value class Speed private constructor(val metersPerSecond: Float) {
    fun toKilometersPerSecond(): Float = metersPerSecond / 1000.0f

    companion object {
        fun ofMetersPerSecond(metersPerSecond: Float) = Speed(metersPerSecond)
        fun ofKilometersPerSecond(kilometersPerSecond: Float) = ofMetersPerSecond(kilometersPerSecond * 1000.0f)
    }

    object SerializerKilometersPerSecond : KSerializer<Speed> {
        override val descriptor = PrimitiveSerialDescriptor(SerializerKilometersPerSecond::class.qualifiedName!!, PrimitiveKind.FLOAT)
        override fun deserialize(decoder: Decoder) = ofKilometersPerSecond(decoder.decodeFloat())
        override fun serialize(encoder: Encoder, value: Speed) = throw NotImplementedError()
    }
}
