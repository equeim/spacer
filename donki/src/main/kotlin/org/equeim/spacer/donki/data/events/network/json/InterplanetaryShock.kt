// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.events.network.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.cache.entities.InterplanetaryShockExtrasSummaryCached
import java.time.Instant

@Serializable
data class InterplanetaryShock(
    @SerialName("activityID") override val id: EventId,
    @SerialName("eventTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") @Serializable(LinkedEventsSerializer::class) override val linkedEvents: List<EventId> = emptyList(),
    @SerialName("instruments") @Serializable(InstrumentsSerializer::class) val instruments: List<String> = emptyList(),
    @SerialName("location") val location: String
) : Event {
    override val type: EventType
        get() = EventType.InterplanetaryShock

    override fun toEventSummary(): EventSummary =
        InterplanetaryShockExtrasSummaryCached(
            id = id,
            time = time,
            location = location
        )
}

interface InterplanetaryShockSummary : EventSummary {
    override val type: EventType
        get() = EventType.InterplanetaryShock
    val location: String
}
