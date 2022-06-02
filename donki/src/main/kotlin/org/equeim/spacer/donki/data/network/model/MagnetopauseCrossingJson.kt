@file:UseSerializers(InstantSerializer::class, EventIdSerializer::class)

package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.MagnetopauseCrossingDetails
import org.equeim.spacer.donki.data.model.MagnetopauseCrossingSummary
import java.time.Instant

@Serializable
internal data class MagnetopauseCrossingJson(
    @SerialName("mpcID") override val id: EventId,
    @SerialName("eventTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("instruments") val instruments: List<InstrumentJson>
) : EventJson {
    override fun toEventSummary() =
        MagnetopauseCrossingSummary(
            id = id,
            time = time
        )

    override fun toEventDetails() =
        MagnetopauseCrossingDetails(
            id = id,
            time = time,
            link = link,
            linkedEvents = linkedEvents.toEventIds(),
            instruments = instruments.map { it.displayName }
        )
}
