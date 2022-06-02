@file:UseSerializers(EventIdSerializer::class)

package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.equeim.spacer.donki.data.model.*
import java.time.Instant

internal sealed interface EventJson {
    val id: EventId
    val time: Instant
    val link: String
    val linkedEvents: List<LinkedEventJson>?

    fun toEventSummary(): EventSummary
    fun toEventDetails(): EventDetails

    @Serializable
    data class LinkedEventJson(@SerialName("activityID") val id: EventId)
}

internal fun List<EventJson.LinkedEventJson>?.toEventIds(): List<EventId> =
    this?.map { it.id }.orEmpty()
