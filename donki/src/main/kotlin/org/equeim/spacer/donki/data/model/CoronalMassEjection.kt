@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.spacer.donki.data.model.units.Angle
import org.equeim.spacer.donki.data.model.units.Coordinates
import org.equeim.spacer.donki.data.model.units.Speed
import java.time.Duration
import java.time.Instant

@Serializable
data class CoronalMassEjection(
    @SerialName("activityID") override val id: EventId,
    @SerialName("startTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") @Serializable(LinkedEventsSerializer::class) override val linkedEvents: List<EventId> = emptyList(),
    @SerialName("sourceLocation") @Serializable(SourceLocationSerializer::class) val sourceLocation: Coordinates? = null,
    @SerialName("note") val note: String,
    @SerialName("instruments") @Serializable(InstrumentsSerializer::class) val instruments: List<String> = emptyList(),
    @SerialName("cmeAnalyses") val cmeAnalyses: List<Analysis> = emptyList()
) : Event {
    override val type: EventType
        get() = EventType.CoronalMassEjection

    fun isEarthShockPredicted(): Boolean {
        return cmeAnalyses.any { analysis ->
            analysis.enlilSimulations.any { it.estimatedShockArrivalTime != null }
        }
    }

    @Serializable
    data class Analysis(
        @SerialName("time21_5") val time215: Instant?,
        @SerialName("latitude") val latitude: Angle?,
        @SerialName("longitude") val longitude: Angle?,
        @SerialName("halfAngle") val halfAngle: Angle?,
        @SerialName("speed") val speed: Speed?,
        @SerialName("type") val type: String,
        @SerialName("isMostAccurate") val isMostAccurate: Boolean,
        @SerialName("note") val note: String,
        @SerialName("levelOfData") val levelOfData: Int,
        @SerialName("link") val link: String,
        @SerialName("enlilList") val enlilSimulations: List<EnlilSimulation> = emptyList(),
    )

    @Serializable
    data class EnlilSimulation(
        @SerialName("au") val au: Float,
        @SerialName("modelCompletionTime") val modelCompletionTime: Instant,
        @SerialName("estimatedShockArrivalTime") val estimatedShockArrivalTime: Instant?,
        @SerialName("estimatedDuration") @Serializable(EnlilSimulationEstimatedDurationSerializer::class) val estimatedDuration: Duration?,
        @SerialName("rmin_re") val rminRe: Angle?,
        @SerialName("kp_18") val kp18: Int?,
        @SerialName("kp_90") val kp90: Int?,
        @SerialName("kp_135") val kp135: Int?,
        @SerialName("kp_180") val kp180: Int?,
        @SerialName("isEarthGB") val isEarthGlancingBlow: Boolean?,
        @SerialName("link") val link: String,
        @SerialName("impactList") val impacts: List<Impact> = emptyList()
    )

    @Serializable
    data class Impact(
        @SerialName("isGlancingBlow") val isGlancingBlow: Boolean,
        @SerialName("location") val location: String,
        @SerialName("arrivalTime") val arrivalTime: Instant
    )

    override fun toEventSummary(): CoronalMassEjectionSummary =
        CoronalMassEjectionSummaryFromJson(
            id = id,
            time = time,
            isEarthShockPredicted = isEarthShockPredicted()
        )
}

interface CoronalMassEjectionSummary : EventSummary {
    override val type: EventType
        get() = EventType.CoronalMassEjection
    val isEarthShockPredicted: Boolean
}

private data class CoronalMassEjectionSummaryFromJson(
    override val id: EventId,
    override val time: Instant,
    override val isEarthShockPredicted: Boolean
) : CoronalMassEjectionSummary

private object EnlilSimulationEstimatedDurationSerializer : KSerializer<Duration> {
    private val SECONDS_IN_HOUR = Duration.ofHours(1).seconds.toFloat()

    override val descriptor = PrimitiveSerialDescriptor(
        EnlilSimulationEstimatedDurationSerializer::class.qualifiedName!!,
        PrimitiveKind.FLOAT
    )

    override fun deserialize(decoder: Decoder): Duration {
        val hours = decoder.decodeFloat()
        return Duration.ofSeconds((hours * SECONDS_IN_HOUR).toLong())
    }

    override fun serialize(encoder: Encoder, value: Duration) = throw NotImplementedError()
}
