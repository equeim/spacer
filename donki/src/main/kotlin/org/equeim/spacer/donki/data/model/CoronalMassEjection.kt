package org.equeim.spacer.donki.data.model

import java.time.Duration
import java.time.Instant

data class CoronalMassEjectionSummary(
    override val id: EventId,
    override val time: Instant,
    val isEarthShockPredicted: Boolean
) : EventSummary {
    override val type = EventType.CoronalMassEjection
}

data class CoronalMassEjectionDetails(
    override val id: EventId,
    override val time: Instant,
    override val link: String,
    override val linkedEvents: List<EventId>,
    val note: String,
    val instruments: List<String>,
    val cmeAnalyses: List<Analysis>
) : EventDetails {
    override val type = EventType.CoronalMassEjection

    data class Analysis(
        val time215: Instant?,
        val latitude: Float?,
        val longitude: Float?,
        val halfAngle: Float?,
        val speed: Float?,
        val note: String,
        val link: String,
        val enlilSimulations: List<EnlilSimulation>
    )

    data class EnlilSimulation(
        val au: Float?,
        val estimatedShockArrivalTime: Instant?,
        val estimatedDuration: Duration?,
        val rminRe: Float?,
        val kp18: Int?,
        val kp90: Int?,
        val kp135: Int?,
        val kp180: Int?,
        val isEarthGlancingBlow: Boolean,
        val link: String,
        val impacts: List<Impact>
    )

    data class Impact(
        val isGlancingBlow: Boolean,
        val location: String,
        val arrivalTime: Instant
    )
}
