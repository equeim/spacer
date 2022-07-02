@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
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

    @Serializable
    data class KpIndex(
        @SerialName("kpIndex") val kpIndex: Int,
        @SerialName("observedTime") val observedTime: Instant,
        @SerialName("source") val source: String
    )

    override fun toEventSummary(): GeomagneticStormSummary =
        GeomagneticStormSummaryFromJson(
            id = id,
            time = time,
            kpIndex = kpIndexes.firstOrNull()?.kpIndex
        )
}

interface GeomagneticStormSummary : EventSummary {
    override val type: EventType
        get() = EventType.GeomagneticStorm
    val kpIndex: Int?
}

private data class GeomagneticStormSummaryFromJson(
    override val id: EventId,
    override val time: Instant,
    override val kpIndex: Int?
) : GeomagneticStormSummary
