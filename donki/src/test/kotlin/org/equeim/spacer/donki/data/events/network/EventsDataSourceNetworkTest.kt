// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.equeim.spacer.donki.apiKey
import org.equeim.spacer.donki.data.DEFAULT_NASA_API_KEY
import org.equeim.spacer.donki.data.common.InvalidApiKeyError
import org.equeim.spacer.donki.data.common.TooManyRequestsError
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStorm
import org.equeim.spacer.donki.data.events.network.json.HighSpeedStream
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShock
import org.equeim.spacer.donki.data.events.network.json.MagnetopauseCrossing
import org.equeim.spacer.donki.data.events.network.json.RadiationBeltEnhancement
import org.equeim.spacer.donki.data.events.network.json.SolarEnergeticParticle
import org.equeim.spacer.donki.data.events.network.json.SolarFlare
import org.equeim.spacer.donki.data.events.network.json.units.Angle
import org.equeim.spacer.donki.data.events.network.json.units.Coordinates
import org.equeim.spacer.donki.data.events.network.json.units.Speed
import org.equeim.spacer.donki.getTestResource
import org.equeim.spacer.donki.instantOf
import org.equeim.spacer.donki.readToBuffer
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

@Suppress("BlockingMethodInNonBlockingContext")
class EventsDataSourceNetworkTest {
    private val server = MockWebServer()
    private lateinit var dataSource: EventsDataSourceNetwork
    private val nasaApiKey = MutableStateFlow(DEFAULT_NASA_API_KEY)

    @BeforeTest
    fun before() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } answers { println("${args[0]}: ${args[1]}"); 0 }
        every { Log.e(any(), any(), any()) } answers {
            println("${args[0]}: ${args[1]}")
            arg<Throwable>(2).printStackTrace(System.out)
            0
        }
        server.start()
        dataSource = EventsDataSourceNetwork(nasaApiKey, server.url("/"))
    }

    @AfterTest
    fun after() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `Validate urls`() = runTest {
        val week = Week(LocalDate.of(2016, 8, 29))
        for (eventType in EventType.entries) {
            server.enqueue(MockResponse().setBody(""))

            dataSource.getEvents(week, eventType)

            val request = server.takeRequest()
            val url = assertNotNull(request.requestUrl)

            val path = url.pathSegments.single()
            assertNotNull(EventType.entries.find { it.stringValue == path })

            val startDateQuery = assertNotNull(url.queryParameter("startDate"))
            assertEquals("2016-08-29", startDateQuery)
            val endDateQuery = assertNotNull(url.queryParameter("endDate"))
            assertEquals("2016-09-04", endDateQuery)
            assertEquals(DEFAULT_NASA_API_KEY, url.apiKey)
        }
    }

    @Test
    fun `Validate 403 error handling`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFailsWith<InvalidApiKeyError> {
            dataSource.getEvents(Week(LocalDate.of(2016, 8, 29)), EventType.GeomagneticStorm)
        }
    }

    @Test
    fun `Validate 429 error handling`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertFailsWith<TooManyRequestsError> {
            dataSource.getEvents(Week(LocalDate.of(2016, 8, 29)), EventType.GeomagneticStorm)
        }
    }

    @Test
    fun `Validate request interrupton on API key change`() = runTest {
        server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(""))
        val requestJob = launch {
            dataSource.getEvents(Week(LocalDate.of(2016, 8, 29)), EventType.GeomagneticStorm)
        }
        withContext(Dispatchers.Default) {
            delay(500.milliseconds)
            println("Changing API key")
            nasaApiKey.value = "lol"
        }
        requestJob.join()
        assertEquals(2, server.requestCount)
        assertEquals(DEFAULT_NASA_API_KEY, server.takeRequest().apiKey)
        assertEquals("lol", server.takeRequest().apiKey)
    }

    @Test
    fun `Validate that dataset parses without exceptions`() = runBlocking {
        val datasetsUrl = EventsDataSourceNetworkTest::class.java.getTestResource("datasets")
        Files.list(Paths.get(datasetsUrl.toURI())).asSequence().forEach { datasetPath ->
            val urlPath = datasetPath.fileName.toString().split('_').first()
            val eventType = EventType.entries.first { it.stringValue == urlPath }
            server.enqueue(MockResponse().setBody(datasetPath.readToBuffer()))
            dataSource.getEvents(Week(LocalDate.MIN), eventType)
        }
    }

    @Test
    fun `Validate coronal mass ejection event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.CoronalMassEjection)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 4, 4)), EventType.CoronalMassEjection)
        val event = events.single().first as CoronalMassEjection
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-07T05:36:00-CME-001"),
            expectedEventType = EventType.CoronalMassEjection,
            expectedTime = instantOf(2022, 4, 7, 5, 36),
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
                time215 = instantOf(2022, 4, 7, 14, 8),
                latitude = Angle.ofDegrees(-31.0f),
                longitude = Angle.ofDegrees(-40.0f),
                halfAngle = Angle.ofDegrees(43.0f),
                speed = Speed.ofKilometersPerSecond(457.0f),
                type = "S",
                isMostAccurate = true,
                note = "SOHO LASCO C3 imagery was partially blocked by the pylon (which lined up almost perfectly with the leading edge) and the CME boundary became very faint in later STEREO A COR2 imagery. However, this was mostly navigated by adjusting the image brightness and contrast.",
                levelOfData = 1,
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
                time215 = instantOf(2022, 4, 7, 14, 58),
                latitude = Angle.ofDegrees(-34.0f),
                longitude = Angle.ofDegrees(-41.0f),
                halfAngle = Angle.ofDegrees(41.0f),
                speed = Speed.ofKilometersPerSecond(418.0f),
                type = "S",
                isMostAccurate = false,
                note = "SOHO LASCO C3 imagery was partially blocked by the pylon (which lined up almost perfectly with the leading edge) and the CME boundary became very faint in later STEREO A COR2 imagery. However, this was mostly navigated by adjusting the image brightness and contrast.",
                levelOfData = 0,
                link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CMEAnalysis/19697/-1",
                enlilSimulations = emptyList()
            )
        )
        assertEquals(expectedAnalyses, event.cmeAnalyses)
    }

    @Test
    fun `Validate geomagnetic storm event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.GeomagneticStorm)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 4, 11)), EventType.GeomagneticStorm)
        val event = events.single().first as GeomagneticStorm
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-14T15:00:00-GST-001"),
            expectedEventType = EventType.GeomagneticStorm,
            expectedTime = instantOf(2022, 4, 14, 15, 0),
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
    fun `Validate interplanetary shock event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.InterplanetaryShock)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 4, 4)), EventType.InterplanetaryShock)
        val event = events.single().first as InterplanetaryShock
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-06T22:45:00-IPS-001"),
            expectedEventType = EventType.InterplanetaryShock,
            expectedTime = instantOf(2022, 4, 6, 22, 45),
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
    fun `Validate solar flare event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.SolarFlare)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 4, 25)), EventType.SolarFlare)
        val event = events.single().first as SolarFlare
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-28T02:24:00-FLR-001"),
            expectedEventType = EventType.SolarFlare,
            expectedTime = instantOf(2022, 4, 28, 2, 24),
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
    fun `Validate solar energetic particle event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.SolarEnergeticParticle)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 3, 28)), EventType.SolarEnergeticParticle)
        val event = events.single().first as SolarEnergeticParticle
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-02T14:39:00-SEP-001"),
            expectedEventType = EventType.SolarEnergeticParticle,
            expectedTime = instantOf(2022, 4, 2, 14, 39),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/SEP/19640/-1",
            expectedLinkedEvents = listOf(
                EventId("2022-04-02T12:56:00-FLR-001") to EventType.SolarFlare,
                EventId("2022-04-02T13:38:00-CME-001") to EventType.CoronalMassEjection
            )
        )
        assertEquals(listOf("SOHO: COSTEP 28.2-50.1 MeV"), event.instruments)
    }

    @Test
    fun `Validate magnetopause crossing event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.MagnetopauseCrossing)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 3, 28)), EventType.MagnetopauseCrossing)
        val event = events.single().first as MagnetopauseCrossing
        validateCommonProperties(
            event,
            expectedId = EventId("2022-03-31T04:10:00-MPC-001"),
            expectedEventType = EventType.MagnetopauseCrossing,
            expectedTime = instantOf(2022, 3, 31, 4, 10),
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
    fun `Validate radiation belt enhancement event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.RadiationBeltEnhancement)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 3, 28)), EventType.RadiationBeltEnhancement)
        val event = events.single().first as RadiationBeltEnhancement
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-02T20:05:00-RBE-001"),
            expectedEventType = EventType.RadiationBeltEnhancement,
            expectedTime = instantOf(2022, 4, 2, 20, 5),
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
    fun `Validate high speed stream event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.HighSpeedStream)))
        val events =
            dataSource.getEvents(Week(LocalDate.of(2022, 3, 28)), EventType.HighSpeedStream)
        val event = events.single().first as HighSpeedStream
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-02T00:33:00-HSS-001"),
            expectedEventType = EventType.HighSpeedStream,
            expectedTime = instantOf(2022, 4, 2, 0, 33),
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

    private fun readSampleEvent(eventType: EventType, suffix: String = ""): Buffer {
        return EventsDataSourceNetworkTest::class.java.getTestResource("${eventType.stringValue}${suffix}.json").readToBuffer()
    }
}
