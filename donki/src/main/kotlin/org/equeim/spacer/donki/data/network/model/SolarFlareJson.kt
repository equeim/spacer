package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.spacer.donki.data.model.SolarFlareDetails
import org.equeim.spacer.donki.data.model.SolarFlareSummary
import java.time.Instant

@Serializable
internal data class SolarFlareJson(
    @SerialName("flrID") override val id: String,
    @SerialName("beginTime") @Serializable(InstantSerializer::class) override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("instruments") val instruments: List<InstrumentJson>,
    @SerialName("peakTime") @Serializable(InstantSerializer::class) val peakTime: Instant,
    @SerialName("endTime") @Serializable(InstantSerializer::class) val endTime: Instant?,
    @SerialName("classType") val classType: String,
    @SerialName("sourceLocation") val sourceLocation: String
) : EventJson {
    override fun toEventSummary() =
        SolarFlareSummary(
            id = id,
            time = time
        )

    override fun toEventDetails() =
        SolarFlareDetails(
            id = id,
            time = time,
            link = link,
            linkedEvents = linkedEvents.toLinkedEvents(),
            instruments = instruments.map { it.displayName },
            peakTime = peakTime,
            endTime = endTime,
            classType = classType,
            sourceLocation = sourceLocation
        )
}
