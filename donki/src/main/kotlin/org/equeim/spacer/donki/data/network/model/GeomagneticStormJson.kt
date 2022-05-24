package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.spacer.donki.data.model.GeomagneticStormDetails
import org.equeim.spacer.donki.data.model.GeomagneticStormSummary
import java.time.Instant

@Serializable
internal data class GeomagneticStormJson(
    @SerialName("gstID") override val id: String,
    @SerialName("startTime") @Serializable(InstantSerializer::class) override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("allKpIndex") val kpIndexes: List<KpIndexJson>
) : EventJson {
    @Serializable
    data class KpIndexJson(
        @SerialName("kpIndex") val kpIndex: Int,
        @SerialName("observedTime") @Serializable(InstantSerializer::class) val observedTime: Instant,
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
            linkedEvents = linkedEvents.toLinkedEvents(),
            kpIndexes = kpIndexes.map {
                GeomagneticStormDetails.KpIndex(
                    kpIndex = it.kpIndex,
                    observedTime = it.observedTime,
                    source = it.source
                )
            }
        )
}
