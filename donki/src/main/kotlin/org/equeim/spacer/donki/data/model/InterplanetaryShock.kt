package org.equeim.spacer.donki.data.model

import java.time.Instant

data class InterplanetaryShockSummary(
    override val id: EventId,
    override val time: Instant
) : EventSummary {
    override val type = EventType.InterplanetaryShock
}

data class InterplanetaryShockDetails(
    override val id: EventId,
    override val time: Instant,
    override val link: String,
    override val linkedEvents: List<EventId>,
    val instruments: List<String>,
    val location: String
) : EventDetails {
    override val type = EventType.InterplanetaryShock
}
