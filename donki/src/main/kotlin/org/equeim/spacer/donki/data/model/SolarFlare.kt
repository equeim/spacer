package org.equeim.spacer.donki.data.model

import java.time.Instant

data class SolarFlareSummary(
    override val id: EventId,
    override val time: Instant
) : EventSummary {
    override val type = EventType.SolarFlare
}

data class SolarFlareDetails(
    override val id: EventId,
    override val time: Instant,
    override val link: String,
    override val linkedEvents: List<EventId>,
    val instruments: List<String>,
    val peakTime: Instant,
    val endTime: Instant?,
    val classType: String,
    val sourceLocation: String
) : EventDetails {
    override val type = EventType.SolarFlare
}
