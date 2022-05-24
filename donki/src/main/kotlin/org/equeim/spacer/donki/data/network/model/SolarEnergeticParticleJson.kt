package org.equeim.spacer.donki.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.spacer.donki.data.model.*
import java.time.Instant

@Serializable
internal data class SolarEnergeticParticleJson(
    @SerialName("sepID") override val id: String,
    @SerialName("eventTime") @Serializable(InstantSerializer::class) override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("instruments") val instruments: List<InstrumentJson>
) : EventJson {
    override fun toEventSummary() =
        SolarEnergeticParticleSummary(
            id = id,
            time = time
        )

    override fun toEventDetails() =
        SolarEnergeticParticleDetails(
            id = id,
            time = time,
            link = link,
            linkedEvents = linkedEvents.toLinkedEvents(),
            instruments = instruments.map { it.displayName }
        )
}
