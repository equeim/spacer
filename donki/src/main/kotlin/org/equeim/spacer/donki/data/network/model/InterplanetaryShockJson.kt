package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.spacer.donki.data.model.InterplanetaryShockDetails
import org.equeim.spacer.donki.data.model.InterplanetaryShockSummary
import java.time.Instant

@Serializable
internal data class InterplanetaryShockJson(
    @SerialName("activityID") override val id: String,
    @SerialName("eventTime") @Serializable(InstantSerializer::class) override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("instruments") val instruments: List<InstrumentJson>,
    @SerialName("location") val location: String
) : EventJson {
    override fun toEventSummary() =
        InterplanetaryShockSummary(
            id = id,
            time = time
        )

    override fun toEventDetails() =
        InterplanetaryShockDetails(
            id = id,
            time = time,
            link = link,
            linkedEvents = linkedEvents.toLinkedEvents(),
            instruments = instruments.map { it.displayName },
            location = location
        )
}
