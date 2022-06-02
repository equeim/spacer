@file:UseSerializers(InstantSerializer::class, EventIdSerializer::class)

package org.equeim.spacer.donki.data.network.model

import androidx.annotation.Keep
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.spacer.donki.data.model.CoronalMassEjectionDetails
import org.equeim.spacer.donki.data.model.CoronalMassEjectionSummary
import org.equeim.spacer.donki.data.model.EventId
import java.time.Duration
import java.time.Instant

@Serializable
@Keep
internal data class CoronalMassEjectionJson(
    @SerialName("activityID") override val id: EventId,
    @SerialName("startTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") override val linkedEvents: List<EventJson.LinkedEventJson>? = null,
    @SerialName("note") val note: String,
    @SerialName("instruments") val instruments: List<InstrumentJson>,
    @SerialName("cmeAnalyses") val cmeAnalyses: List<AnalysisJson>?
) : EventJson {
    @Serializable
    data class AnalysisJson(
        @SerialName("time21_5") val time215: Instant?,
        @SerialName("latitude") val latitude: Float?,
        @SerialName("longitude") val longitude: Float?,
        @SerialName("halfAngle") val halfAngle: Float?,
        @SerialName("speed") val speed: Float?,
        @SerialName("note") val note: String,
        @SerialName("link") val link: String,
        @SerialName("enlilList") val enlilSimulations: List<EnlilSimulationJson>?,
    )

    @Serializable
    data class EnlilSimulationJson(
        @SerialName("au") val au: Float?,
        @SerialName("estimatedShockArrivalTime") val estimatedShockArrivalTime: Instant?,
        @SerialName("estimatedDuration") @Serializable(EnlilSimulationEstimatedDurationSerializer::class) val estimatedDuration: Duration?,
        @SerialName("rmin_re") val rminRe: Float?,
        @SerialName("kp_18") val kp18: Int?,
        @SerialName("kp_90") val kp90: Int?,
        @SerialName("kp_135") val kp135: Int?,
        @SerialName("kp_180") val kp180: Int?,
        @SerialName("isEarthGB") val isEarthGlancingBlow: Boolean?,
        @SerialName("link") val link: String,
        @SerialName("impactList") val impacts: List<ImpactJson>?
    )

    @Serializable
    data class ImpactJson(
        @SerialName("isGlancingBlow") val isGlancingBlow: Boolean,
        @SerialName("location") val location: String,
        @SerialName("arrivalTime") val arrivalTime: Instant
    )

    override fun toEventSummary() =
        CoronalMassEjectionSummary(
            id = id,
            time = time,
            isEarthShockPredicted = cmeAnalyses?.any { analysis ->
                analysis.enlilSimulations?.any { it.estimatedShockArrivalTime != null } ?: false
            } ?: false
        )

    override fun toEventDetails() =
        CoronalMassEjectionDetails(
            id = id,
            time = time,
            link = link,
            linkedEvents = linkedEvents.toEventIds(),
            note = note,
            instruments = instruments.map { it.displayName },
            cmeAnalyses = cmeAnalyses?.map { analysis ->
                CoronalMassEjectionDetails.Analysis(
                    time215 = analysis.time215,
                    latitude = analysis.latitude,
                    longitude = analysis.longitude,
                    halfAngle = analysis.halfAngle,
                    speed = analysis.speed,
                    note = analysis.note,
                    link = analysis.link,
                    enlilSimulations = analysis.enlilSimulations?.map { simulation ->
                        CoronalMassEjectionDetails.EnlilSimulation(
                            au = simulation.au,
                            estimatedShockArrivalTime = simulation.estimatedShockArrivalTime,
                            estimatedDuration = simulation.estimatedDuration,
                            rminRe = simulation.rminRe,
                            kp18 = simulation.kp18,
                            kp90 = simulation.kp90,
                            kp135 = simulation.kp135,
                            kp180 = simulation.kp180,
                            isEarthGlancingBlow = simulation.isEarthGlancingBlow ?: false,
                            link = simulation.link,
                            impacts = simulation.impacts?.map { impact ->
                                CoronalMassEjectionDetails.Impact(
                                    isGlancingBlow = impact.isGlancingBlow,
                                    location = impact.location,
                                    arrivalTime = impact.arrivalTime
                                )
                            }.orEmpty()
                        )
                    }.orEmpty()
                )
            }.orEmpty()
        )
}

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

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeFloat(value.seconds.toFloat() / SECONDS_IN_HOUR)
    }
}
