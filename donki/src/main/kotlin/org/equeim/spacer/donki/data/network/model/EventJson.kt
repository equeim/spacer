package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.spacer.donki.data.model.*
import java.time.Instant

internal sealed interface EventJson {
    val id: String
    val time: Instant
    val link: String
    val linkedEvents: List<LinkedEventJson>?

    fun toEventSummary(): EventSummary
    fun toEventDetails(): EventDetails

    @Serializable
    data class LinkedEventJson(@SerialName("activityID") val id: String)
}

internal fun List<EventJson.LinkedEventJson>?.toLinkedEvents(): List<EventDetails.LinkedEvent> =
    this?.map { EventDetails.LinkedEvent(it.id) }.orEmpty()
