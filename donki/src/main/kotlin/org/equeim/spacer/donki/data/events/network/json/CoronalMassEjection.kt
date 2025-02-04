// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.events.network.json

import androidx.compose.runtime.Immutable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.cache.entities.CoronalMassEjectionExtrasSummaryCached
import org.equeim.spacer.donki.data.events.network.json.units.Angle
import org.equeim.spacer.donki.data.events.network.json.units.Coordinates
import org.equeim.spacer.donki.data.events.network.json.units.Speed
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

    val predictedEarthImpact: EarthImpactType
        get() = cmeAnalyses.sortedByDescending { it.submissionTime }.firstNotNullOfOrNull { analysis ->
            analysis.predictedEarthImpact.takeIf { it != EarthImpactType.NoImpact }
        } ?: EarthImpactType.NoImpact

    val cmeType: CmeType?
        get() = cmeAnalyses.sortedByDescending { it.submissionTime }.firstNotNullOfOrNull { it.type }

    enum class EarthImpactType(val integerValue: Int) {
        NoImpact(0),
        Impact(1),
        GlancingBlow(2)
    }

    @Serializable
    @Immutable
    data class Analysis(
        @SerialName("submissionTime") val submissionTime: Instant,
        @SerialName("levelOfData") val levelOfData: DataLevel,
        @SerialName("measurementTechnique") val measurementTechnique: String,
        @SerialName("featureCode") val measurementType: MeasurementType?,
        @SerialName("isMostAccurate") val isMostAccurate: Boolean,
        @SerialName("imageType") val imageType: String?,
        @SerialName("type") val type: CmeType?,
        @SerialName("speed") val speed: Speed?,
        @SerialName("speedMeasuredAtHeight") val speedMeasuredAtHeight: Float?,
        @SerialName("time21_5") val time215: Instant?,
        @SerialName("latitude") val latitude: Angle?,
        @SerialName("longitude") val longitude: Angle?,
        @SerialName("halfAngle") val halfWidth: Angle?,
        @SerialName("minorHalfWidth") val minorHalfWidth: Angle?,
        @SerialName("tilt") val tilt: Angle?,
        @SerialName("note") val note: String,
        @SerialName("link") val link: String,
        @SerialName("enlilList") val enlilSimulations: List<EnlilSimulation> = emptyList(),
    ) {
        val predictedEarthImpact: EarthImpactType
            get() {
                val simulation = enlilSimulations
                    .sortedByDescending { it.modelCompletionTime }
                    .find { it.estimatedShockArrivalTime != null }
                return if (simulation != null) {
                    if (simulation.isEarthGlancingBlow == true) {
                        EarthImpactType.GlancingBlow
                    } else {
                        EarthImpactType.Impact
                    }
                } else {
                    EarthImpactType.NoImpact
                }
            }
    }

    @Serializable(MeasurementType.Serializer::class)
    enum class MeasurementType(val stringValue: String) {
        LeadingEdge("LE"),
        ShockFront("SH"),
        RightHandBoundary("RHB"),
        LeftHandBoundary("LHB"),
        BlackWhiteBoundary("BW"),
        ProminenceCore("COR"),
        DisconnectionFront("DIS"),
        TrailingEdge("TE");

        internal object Serializer : KSerializer<MeasurementType?> {
            private const val NULL = "null"

            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(MeasurementType::class.qualifiedName!!, PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): MeasurementType? {
                val string = decoder.decodeString()
                if (string == NULL) return null
                return MeasurementType.entries.find { it.stringValue == string } ?: throw SerializationException("Unknown CME measurement type '$string'")
            }

            @OptIn(ExperimentalSerializationApi::class)
            override fun serialize(encoder: Encoder, value: MeasurementType?) {
                if (value != null) {
                    encoder.encodeString(value.stringValue)
                } else {
                    encoder.encodeNull()
                }
            }
        }
    }

    @Serializable(DataLevel.Serializer::class)
    enum class DataLevel(val integerValue: Int) {
        RealTime(0),
        RealTimeChecked(1),
        Retrospective(2);

        internal object Serializer : KSerializer<DataLevel> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(DataLevel::class.qualifiedName!!, PrimitiveKind.INT)

            override fun deserialize(decoder: Decoder): DataLevel {
                val integer = decoder.decodeInt()
                return DataLevel.entries.find { it.integerValue == integer } ?: throw SerializationException("Unknown CME analysis data level $integer")
            }

            override fun serialize(encoder: Encoder, value: DataLevel) {
                encoder.encodeInt(value.integerValue)
            }
        }
    }

    @Serializable(CmeType.Serializer::class)
    enum class CmeType(val stringValue: String) {
        Slowest("S"),
        Common("C"),
        Occasional("O"),
        Rare("R"),
        ExtremelyRare("ER");

        internal object Serializer : KSerializer<CmeType?> {
            private const val NONE = "NONE"

            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(CmeType::class.qualifiedName!!, PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): CmeType? {
                val string = decoder.decodeString()
                if (string == NONE) return null
                return CmeType.entries.find { it.stringValue == string } ?: throw SerializationException("Unknown CME type '$string'")
            }

            @OptIn(ExperimentalSerializationApi::class)
            override fun serialize(encoder: Encoder, value: CmeType?) {
                if (value != null) {
                    encoder.encodeString(value.stringValue)
                } else {
                    encoder.encodeNull()
                }
            }
        }
    }

    @Serializable
    @Immutable
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
    @Immutable
    data class Impact(
        @SerialName("isGlancingBlow") val isGlancingBlow: Boolean,
        @SerialName("location") val location: String,
        @SerialName("arrivalTime") val arrivalTime: Instant
    )

    override fun toEventSummary(): CoronalMassEjectionSummary =
        CoronalMassEjectionExtrasSummaryCached(
            id = id,
            time = time,
            predictedEarthImpact = predictedEarthImpact,
            cmeType = cmeType,
        )
}

interface CoronalMassEjectionSummary : EventSummary {
    override val type: EventType
        get() = EventType.CoronalMassEjection
    val predictedEarthImpact: CoronalMassEjection.EarthImpactType
    val cmeType: CoronalMassEjection.CmeType?
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

    override fun serialize(encoder: Encoder, value: Duration) = throw NotImplementedError()
}
