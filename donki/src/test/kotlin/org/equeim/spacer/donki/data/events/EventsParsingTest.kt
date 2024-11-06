// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromStream
import org.equeim.spacer.donki.data.common.DonkiJson
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStorm
import org.equeim.spacer.donki.data.events.network.json.HighSpeedStream
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShock
import org.equeim.spacer.donki.data.events.network.json.MagnetopauseCrossing
import org.equeim.spacer.donki.data.events.network.json.RadiationBeltEnhancement
import org.equeim.spacer.donki.data.events.network.json.SolarEnergeticParticle
import org.equeim.spacer.donki.data.events.network.json.SolarFlare
import org.equeim.spacer.donki.data.events.network.json.eventSerializer
import org.equeim.spacer.donki.data.events.network.json.units.Angle
import org.equeim.spacer.donki.data.events.network.json.units.Coordinates
import org.equeim.spacer.donki.data.events.network.json.units.Speed
import org.equeim.spacer.donki.getTestResourceInputStream
import org.equeim.spacer.donki.getTestResourceURL
import org.equeim.spacer.donki.instantOf
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EventsParsingTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `Validate that dataset parses without exceptions`() {
        val datasetsUrl = EventsParsingTest::class.java.getTestResourceURL("datasets")
        Files.list(Paths.get(datasetsUrl.toURI())).parallel().forEach { datasetPath ->
            val fileName = datasetPath.name
            val eventTypeString = fileName.take(3)
            val eventType = EventType.entries.first { it.stringValue == eventTypeString }
            try {
                datasetPath.inputStream().use {
                    DonkiJson.decodeFromStream(
                        ListSerializer(eventType.eventSerializer()),
                        it
                    )
                }
            } catch (e: Exception) {
                fail("Failed to parse $datasetsUrl", e)
            }
        }
    }

    @Test
    fun `Validate coronal mass ejection event parsing`() {
        val event = parseSampleEvent<CoronalMassEjection>(EventType.CoronalMassEjection)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T05:36:00-CME-001"),
            expectedEventType = EventType.CoronalMassEjection,
            expectedTime = instantOf(2022, 1, 19, 5, 36),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CME/19695/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-04-09T12:46:00-IPS-001") to EventType.InterplanetaryShock,
                EventId("2022-04-10T03:00:00-GST-001") to EventType.GeomagneticStorm
            )
        )
        assertEquals(Coordinates(Angle.ofDegrees(-35.0f), Angle.ofDegrees(0.0f)), event.sourceLocation)
        assertEquals(
            "Partial halo S in COR2A and SE in C2/C3, associated with the large horizontally-stretched filament eruption with the center at around S35W00 which starts erupting around 2022-04-07T05:00Z as seen in AIA 304, other coronal signatures (dimming, rising post-eruptive arcades) also seen in AIA 193 and EUVI A 195.",
            event.note
        )
        assertEquals(
            listOf(
                "SOHO: LASCO/C2",
                "SOHO: LASCO/C3",
                "STEREO A: SECCHI/COR2"
            ), event.instruments
        )
        val expectedAnalyses = listOf(
            CoronalMassEjection.Analysis(
                submissionTime = instantOf(2022, 10, 10, 19, 13),
                levelOfData = CoronalMassEjection.DataLevel.RealTimeChecked,
                measurementTechnique = "SWPC_CAT",
                measurementType = CoronalMassEjection.MeasurementType.LeadingEdge,
                isMostAccurate = true,
                imageType = "direct",
                type = CoronalMassEjection.CmeType.Slowest,
                speed = Speed.ofKilometersPerSecond(457.0f),
                speedMeasuredAtHeight = null,
                time215 = instantOf(2022, 4, 7, 14, 8),
                latitude = Angle.ofDegrees(-31.0f),
                longitude = Angle.ofDegrees(-40.0f),
                halfWidth = Angle.ofDegrees(43.0f),
                minorHalfWidth = null,
                tilt = null,
                note = "SOHO LASCO C3 imagery was partially blocked by the pylon (which lined up almost perfectly with the leading edge) and the CME boundary became very faint in later STEREO A COR2 imagery. However, this was mostly navigated by adjusting the image brightness and contrast.",
                link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CMEAnalysis/19700/-1",
                enlilSimulations = listOf(
                    CoronalMassEjection.EnlilSimulation(
                        au = 2.0f,
                        modelCompletionTime = instantOf(2022, 4, 7, 13, 56),
                        estimatedShockArrivalTime = instantOf(2022, 4, 10, 16, 0),
                        estimatedDuration = null,
                        rminRe = null,
                        kp18 = null,
                        kp90 = 3,
                        kp135 = 4,
                        kp180 = 4,
                        isEarthGlancingBlow = true,
                        link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/WSA-ENLIL/19699/-1",
                        impacts = listOf(
                            CoronalMassEjection.Impact(
                                isGlancingBlow = false,
                                location = "STEREO A",
                                arrivalTime = instantOf(2022, 4, 10, 14, 54)
                            )
                        )
                    )
                )
            ),
            CoronalMassEjection.Analysis(
                submissionTime = instantOf(2022, 4, 7, 13, 3),
                levelOfData = CoronalMassEjection.DataLevel.RealTime,
                measurementTechnique = "SWPC_CAT",
                measurementType = CoronalMassEjection.MeasurementType.RightHandBoundary,
                isMostAccurate = false,
                imageType = "running difference",
                type = CoronalMassEjection.CmeType.Slowest,
                speed = Speed.ofKilometersPerSecond(418.0f),
                speedMeasuredAtHeight = null,
                time215 = instantOf(2022, 4, 7, 14, 58),
                latitude = Angle.ofDegrees(-34.0f),
                longitude = Angle.ofDegrees(-41.0f),
                halfWidth = Angle.ofDegrees(41.0f),
                minorHalfWidth = null,
                tilt = null,
                note = "SOHO LASCO C3 imagery was partially blocked by the pylon (which lined up almost perfectly with the leading edge) and the CME boundary became very faint in later STEREO A COR2 imagery. However, this was mostly navigated by adjusting the image brightness and contrast.",
                link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CMEAnalysis/19697/-1",
                enlilSimulations = emptyList()
            )
        )
        assertEquals(expectedAnalyses, event.cmeAnalyses)
    }

    @Test
    fun `Validate geomagnetic storm event parsing`() {
        val event = parseSampleEvent<GeomagneticStorm>(EventType.GeomagneticStorm)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T15:00:00-GST-001"),
            expectedEventType = EventType.GeomagneticStorm,
            expectedTime = instantOf(2022, 1, 19, 15, 0),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/GST/19773/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-04-11T06:00:00-CME-001") to EventType.CoronalMassEjection,
                EventId("2022-04-14T03:37:00-IPS-001") to EventType.InterplanetaryShock
            )
        )
        assertEquals(
            listOf(
                GeomagneticStorm.KpIndex(
                    observedTime = instantOf(2022, 4, 14, 18, 0),
                    kpIndex = 6.0f,
                    source = "NOAA"
                ),
                GeomagneticStorm.KpIndex(
                    observedTime = instantOf(2022, 4, 14, 18, 0),
                    kpIndex = 6.0f,
                    source = "NOAA"
                )
            ), event.kpIndexes
        )
    }

    @Test
    fun `Validate interplanetary shock event parsing`() {
        val event = parseSampleEvent<InterplanetaryShock>(EventType.InterplanetaryShock)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T22:45:00-IPS-001"),
            expectedEventType = EventType.InterplanetaryShock,
            expectedTime = instantOf(2022, 1, 19, 22, 45),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/IPS/19698/-1",
            expectedLinkedEvents = emptyList()
        )
        assertEquals(
            listOf(
                "DSCOVR: PLASMAG",
                "ACE: SWEPAM",
                "ACE: MAG"
            ), event.instruments
        )
    }

    @Test
    fun `Validate solar flare event parsing`() {
        val event = parseSampleEvent<SolarFlare>(EventType.SolarFlare)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T02:24:00-FLR-001"),
            expectedEventType = EventType.SolarFlare,
            expectedTime = instantOf(2022, 1, 19, 2, 24),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/FLR/19972/-1",
            expectedLinkedEvents = listOf(EventId("2022-04-28T03:09:00-CME-001") to EventType.CoronalMassEjection)
        )
        assertEquals(listOf("GOES-P: EXIS 1.0-8.0"), event.instruments)
        assertEquals(instantOf(2022, 4, 28, 3, 6), event.peakTime)
        assertEquals(instantOf(2022, 4, 28, 3, 27), event.endTime)
        assertEquals("C6.7", event.classType)
        assertEquals(Coordinates(Angle.ofDegrees(27.0f), Angle.ofDegrees(-24.0f)), event.sourceLocation)
    }

    @Test
    fun `Validate solar energetic particle event parsing`() {
        val event = parseSampleEvent<SolarEnergeticParticle>(EventType.SolarEnergeticParticle)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T14:39:00-SEP-001"),
            expectedEventType = EventType.SolarEnergeticParticle,
            expectedTime = instantOf(2022, 1, 19, 14, 39),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/SEP/19640/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-04-02T12:56:00-FLR-001") to EventType.SolarFlare,
                EventId("2022-04-02T13:38:00-CME-001") to EventType.CoronalMassEjection
            )
        )
        assertEquals(listOf("SOHO: COSTEP 28.2-50.1 MeV"), event.instruments)
    }

    @Test
    fun `Validate magnetopause crossing event parsing`() {
        val event = parseSampleEvent<MagnetopauseCrossing>(EventType.MagnetopauseCrossing)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T04:10:00-MPC-001"),
            expectedEventType = EventType.MagnetopauseCrossing,
            expectedTime = instantOf(2022, 1, 19, 4, 10),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/MPC/19603/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-03-28T12:09:00-CME-001") to EventType.CoronalMassEjection,
                EventId("2022-03-28T20:23:00-CME-001") to EventType.CoronalMassEjection,
                EventId("2022-03-31T01:41:00-IPS-001") to EventType.InterplanetaryShock
            )
        )
        assertEquals(listOf("MODEL: SWMF"), event.instruments)
    }

    @Test
    fun `Validate radiation belt enhancement event parsing`() {
        val event = parseSampleEvent<RadiationBeltEnhancement>(EventType.RadiationBeltEnhancement)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T20:05:00-RBE-001"),
            expectedEventType = EventType.RadiationBeltEnhancement,
            expectedTime = instantOf(2022, 1, 19, 20, 5),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/RBE/19647/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-03-28T12:09:00-CME-001") to EventType.CoronalMassEjection,
                EventId("2022-03-28T20:23:00-CME-001") to EventType.CoronalMassEjection,
                EventId("2022-04-02T00:33:00-HSS-001") to EventType.HighSpeedStream
            )
        )
        assertEquals(listOf("GOES-P: SEISS >2MeV"), event.instruments)
    }

    @Test
    fun `Validate high speed stream event parsing`() {
        val event = parseSampleEvent<HighSpeedStream>(EventType.HighSpeedStream)
        validateCommonProperties(
            event,
            expectedId = EventId("2022-01-19T00:33:00-HSS-001"),
            expectedEventType = EventType.HighSpeedStream,
            expectedTime = instantOf(2022, 1, 19, 0, 33),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/HSS/19661/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-04-02T00:33:00-IPS-001") to EventType.InterplanetaryShock,
                EventId("2022-04-02T20:05:00-RBE-001") to EventType.RadiationBeltEnhancement
            )
        )
        assertEquals(
            listOf(
                "DSCOVR: PLASMAG",
                "ACE: SWEPAM",
                "ACE: MAG"
            ), event.instruments
        )
    }

    private fun validateCommonProperties(
        event: Event,
        expectedId: EventId,
        expectedEventType: EventType,
        expectedTime: Instant,
        expectedLink: String,
        expectedLinkedEvents: List<Pair<EventId, EventType>>
    ) {
        assertEquals(expectedId, event.id)
        val (eventTypeFromId, _) = event.id.parse()
        assertEquals(expectedEventType, eventTypeFromId)
        assertEquals(expectedTime, event.time)
        assertEquals(expectedLink, event.link)
        assertEquals(expectedLinkedEvents.map { it.first }, event.linkedEvents)
        assertEquals(
            expectedLinkedEvents.map { it.second },
            event.linkedEvents.map { it.parse().type })
    }

    private companion object {
        @OptIn(ExperimentalSerializationApi::class)
        inline fun <reified T> parseSampleEvent(eventType: EventType): T =
            EventsParsingTest::class.java.getTestResourceInputStream("${eventType.stringValue}.json").use {
                val events = DonkiJson.decodeFromStream<List<T>>(it)
                assertEquals(1, events.size)
                events.first()
            }
    }
}
