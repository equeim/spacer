// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.events.network.json

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import java.time.Instant

@Serializable
data class GeomagneticStorm(
    @SerialName("gstID") override val id: EventId,
    @SerialName("startTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") @Serializable(LinkedEventsSerializer::class) override val linkedEvents: List<EventId> = emptyList(),
    @SerialName("allKpIndex") val kpIndexes: List<KpIndex>
) : Event {
    override val type: EventType
        get() = EventType.GeomagneticStorm

    fun kpIndex(): Float? = kpIndexes.maxOfOrNull { it.kpIndex }

    @Serializable
    @Immutable
    data class KpIndex(
        @SerialName("kpIndex") val kpIndex: Float,
        @SerialName("observedTime") val observedTime: Instant,
        @SerialName("source") val source: String
    )

    override fun toEventSummary(): GeomagneticStormSummary =
        GeomagneticStormSummaryFromJson(
            id = id,
            time = time,
            kpIndex = kpIndex()
        )
}

interface GeomagneticStormSummary : EventSummary {
    override val type: EventType
        get() = EventType.GeomagneticStorm
    val kpIndex: Float?
}

private data class GeomagneticStormSummaryFromJson(
    override val id: EventId,
    override val time: Instant,
    override val kpIndex: Float?
) : GeomagneticStormSummary
