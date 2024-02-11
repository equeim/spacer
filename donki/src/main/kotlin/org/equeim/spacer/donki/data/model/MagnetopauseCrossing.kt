// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class MagnetopauseCrossing(
    @SerialName("mpcID") override val id: EventId,
    @SerialName("eventTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") @Serializable(LinkedEventsSerializer::class) override val linkedEvents: List<EventId> = emptyList(),
    @SerialName("instruments") @Serializable(InstrumentsSerializer::class) val instruments: List<String> = emptyList(),
) : Event {
    override val type: EventType
        get() = EventType.MagnetopauseCrossing

    override fun toEventSummary(): EventSummary =
        MagnetopauseCrossingSummaryFromJson(
            id = id,
            time = time
        )
}

private data class MagnetopauseCrossingSummaryFromJson(
    override val id: EventId,
    override val time: Instant
) : EventSummary {
    override val type: EventType
        get() = EventType.MagnetopauseCrossing
}
