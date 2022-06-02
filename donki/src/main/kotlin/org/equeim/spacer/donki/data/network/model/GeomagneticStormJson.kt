@file:UseSerializers(InstantSerializer::class, EventIdSerializer::class)

package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.GeomagneticStormDetails
import org.equeim.spacer.donki.data.model.GeomagneticStormSummary
import java.time.Instant

@Serializable
internal data class GeomagneticStormJson(
    @SerialName("gstID") override val id: EventId,
    @SerialName("startTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("allKpIndex") val kpIndexes: List<KpIndexJson>
) : EventJson {
    @Serializable
    data class KpIndexJson(
        @SerialName("kpIndex") val kpIndex: Int,
        @SerialName("observedTime") val observedTime: Instant,
        @SerialName("source") val source: String
    )

    override fun toEventSummary() =
        GeomagneticStormSummary(
            id = id,
            time = time,
            kpIndex = kpIndexes.firstOrNull()?.kpIndex
        )

    override fun toEventDetails() =
        GeomagneticStormDetails(
            id = id,
            time = time,
            link = link,
            linkedEvents = linkedEvents.toEventIds(),
            kpIndexes = kpIndexes.map {
                GeomagneticStormDetails.KpIndex(
                    kpIndex = it.kpIndex,
                    observedTime = it.observedTime,
                    source = it.source
                )
            }
        )
}
