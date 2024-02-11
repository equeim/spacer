// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.equeim.spacer.donki.data.model.units.Coordinates
import java.time.Instant

@Serializable
data class SolarFlare(
    @SerialName("flrID") override val id: EventId,
    @SerialName("beginTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") @Serializable(LinkedEventsSerializer::class) override val linkedEvents: List<EventId> = emptyList(),
    @SerialName("instruments") @Serializable(InstrumentsSerializer::class) val instruments: List<String> = emptyList(),
    @SerialName("peakTime") val peakTime: Instant,
    @SerialName("endTime") val endTime: Instant?,
    @SerialName("classType") val classType: String,
    @SerialName("sourceLocation") @Serializable(SourceLocationSerializer::class) val sourceLocation: Coordinates?,
) : Event {
    override val type: EventType
        get() = EventType.SolarFlare

    override fun toEventSummary(): EventSummary =
        SolarFlareSummaryFromJson(
            id = id,
            time = time,
            classType = classType
        )
}

interface SolarFlareSummary : EventSummary {
    override val type: EventType
        get() = EventType.SolarFlare
    val classType: String
}

private data class SolarFlareSummaryFromJson(
    override val id: EventId,
    override val time: Instant,
    override val classType: String,
) : SolarFlareSummary
