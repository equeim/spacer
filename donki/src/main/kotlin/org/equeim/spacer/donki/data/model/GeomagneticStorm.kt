package org.equeim.spacer.donki.data.model

import java.time.Instant

data class GeomagneticStormSummary(
    override val id: String,
    override val time: Instant,
    val kpIndex: Int?
) : EventSummary {
    override val type = EventType.GeomagneticStorm
}

data class GeomagneticStormDetails(
    override val id: String,
    override val time: Instant,
    override val link: String,
    override val linkedEvents: List<EventDetails.LinkedEvent>,
    val kpIndexes: List<KpIndex>
) : EventDetails {
    override val type = EventType.GeomagneticStorm

    data class KpIndex(
        val kpIndex: Int,
        val observedTime: Instant,
        val source: String
    )
}
