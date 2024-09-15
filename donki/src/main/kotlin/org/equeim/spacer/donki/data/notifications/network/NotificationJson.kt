// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:UseSerializers(InstantSerializer::class, NotificationTypeSerializer::class)

package org.equeim.spacer.donki.data.notifications.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.spacer.donki.data.events.network.json.InstantSerializer
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import java.time.Instant

@Serializable
internal data class NotificationJson(
    @SerialName("messageID")
    val id: NotificationId,
    @SerialName("messageType")
    val type: NotificationType,
    @SerialName("messageIssueTime")
    val time: Instant,
    @SerialName("messageURL")
    val link: String,
    @SerialName("messageBody")
    val body: String,
)

private object NotificationTypeSerializer : KSerializer<NotificationType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(NotificationType::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): NotificationType {
        val string = decoder.decodeString()
        return NotificationType.entries.find { it.stringValue == string }
            ?: throw IllegalArgumentException("Failed to convert notification type $string")
    }

    override fun serialize(encoder: Encoder, value: NotificationType) =
        encoder.encodeString(value.stringValue)
}
