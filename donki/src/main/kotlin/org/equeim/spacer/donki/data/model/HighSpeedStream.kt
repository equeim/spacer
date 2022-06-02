package org.equeim.spacer.donki.data.model

import java.time.Instant

data class HighSpeedStreamSummary(
    override val id: EventId,
    override val time: Instant
) : EventSummary {
    override val type = EventType.HighSpeedStream
}

data class HighSpeedStreamDetails(
    override val id: EventId,
    override val time: Instant,
    override val link: String,
    override val linkedEvents: List<EventId>,
    val instruments: List<String>
) : EventDetails {
    override val type = EventType.HighSpeedStream
}
