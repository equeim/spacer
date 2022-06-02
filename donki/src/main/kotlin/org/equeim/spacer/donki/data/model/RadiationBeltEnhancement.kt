package org.equeim.spacer.donki.data.model

import java.time.Instant

data class RadiationBeltEnhancementSummary(
    override val id: EventId,
    override val time: Instant
) : EventSummary {
    override val type = EventType.RadiationBeltEnhancement
}

data class RadiationBeltEnhancementDetails(
    override val id: EventId,
    override val time: Instant,
    override val link: String,
    override val linkedEvents: List<EventId>,
    val instruments: List<String>
) : EventDetails {
    override val type = EventType.RadiationBeltEnhancement
}
