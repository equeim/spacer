package org.equeim.spacer.donki.data.network

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.network.model.*
import org.equeim.spacer.donki.instantOf
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.*
import kotlin.io.path.inputStream
import kotlin.streams.asSequence
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("BlockingMethodInNonBlockingContext")
class DonkiDataSourceNetworkTest {
    private val server = MockWebServer()
    private lateinit var dataSource: DonkiDataSourceNetwork

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
        dataSource = DonkiDataSourceNetwork(server.url("/"))
    }

    @AfterTest
    fun after() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `Validate urls`() = runTest {
        val startDate = LocalDate.of(2016, 9, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val endDate = LocalDate.of(2016, 9, 30).atStartOfDay().toInstant(ZoneOffset.UTC)
        EventType.values().forEach { eventType ->
            server.enqueue(MockResponse().setBody(""))
            dataSource.getEvents(eventType, startDate, endDate)

            val request = server.takeRequest()
            val url = checkNotNull(request.requestUrl)

            assertEquals(eventType.getExpectedUrlPath(), url.pathSegments.single())

            val startDateQuery = checkNotNull(url.queryParameter("startDate"))
            assertEquals("2016-09-01", startDateQuery)
            val endDateQuery = checkNotNull(url.queryParameter("endDate"))
            assertEquals("2016-09-30", endDateQuery)
        }
    }

    @Test
    fun `Validate that dataset parses without exceptions`() = runBlocking {
        val cl = DonkiDataSourceNetworkTest::class.java
        val datasetsUrl =
            checkNotNull(cl.getResource("/${cl.packageName.replace('.', '/')}/datasets"))
        Files.list(Paths.get(datasetsUrl.toURI())).asSequence().forEach { datasetPath ->
            val urlPath = datasetPath.fileName.toString().split('_').first()
            val eventType = EventType.values().first { it.getExpectedUrlPath() == urlPath }
            server.enqueue(MockResponse().setBody(datasetPath.readToBuffer()))
            dataSource.getEvents(eventType, Instant.EPOCH, Instant.EPOCH)
        }
    }

    @Test
    fun `Validate coronal mass ejection event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.CoronalMassEjection)))
        val events =
            dataSource.getEvents(EventType.CoronalMassEjection, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as CoronalMassEjectionJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-07T05:36:00-CME-001"),
            expectedEventType = EventType.CoronalMassEjection,
            expectedTime = instantOf(2022, 4, 7, 5, 36),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CME/19695/-1",
            expectedLinkedEvents = listOf(
                EventJson.LinkedEventJson(EventId("2022-04-09T12:46:00-IPS-001")) to EventType.InterplanetaryShock,
                EventJson.LinkedEventJson(EventId("2022-04-10T03:00:00-GST-001")) to EventType.GeomagneticStorm
            )
        )
        assertEquals(
            "Partial halo S in COR2A and SE in C2/C3, associated with the large horizontally-stretched filament eruption with the center at around S35W00 which starts erupting around 2022-04-07T05:00Z as seen in AIA 304, other coronal signatures (dimming, rising post-eruptive arcades) also seen in AIA 193 and EUVI A 195.",
            event.note
        )
        assertEquals(
            listOf(
                InstrumentJson("SOHO: LASCO/C2"),
                InstrumentJson("SOHO: LASCO/C3"),
                InstrumentJson("STEREO A: SECCHI/COR2")
            ), event.instruments
        )
        val expectedAnalyses = listOf(
            CoronalMassEjectionJson.AnalysisJson(
                time215 = instantOf(2022, 4, 7, 14, 8),
                latitude = -31.0f,
                longitude = -40.0f,
                halfAngle = 43.0f,
                speed = 457.0f,
                note = "SOHO LASCO C3 imagery was partially blocked by the pylon (which lined up almost perfectly with the leading edge) and the CME boundary became very faint in later STEREO A COR2 imagery. However, this was mostly navigated by adjusting the image brightness and contrast.",
                link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CMEAnalysis/19700/-1",
                enlilSimulations = listOf(
                    CoronalMassEjectionJson.EnlilSimulationJson(
                        au = 2.0f,
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
                            CoronalMassEjectionJson.ImpactJson(
                                isGlancingBlow = false,
                                location = "STEREO A",
                                arrivalTime = instantOf(2022, 4, 10, 14, 54)
                            )
                        )
                    )
                )
            ),
            CoronalMassEjectionJson.AnalysisJson(
                time215 = instantOf(2022, 4, 7, 14, 58),
                latitude = -34.0f,
                longitude = -41.0f,
                halfAngle = 41.0f,
                speed = 418.0f,
                note = "SOHO LASCO C3 imagery was partially blocked by the pylon (which lined up almost perfectly with the leading edge) and the CME boundary became very faint in later STEREO A COR2 imagery. However, this was mostly navigated by adjusting the image brightness and contrast.",
                link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/CMEAnalysis/19697/-1",
                enlilSimulations = null
            )
        )
        assertEquals(expectedAnalyses, event.cmeAnalyses)
    }

    @Test
    fun `Validate geomagnetic storm event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.GeomagneticStorm)))
        val events =
            dataSource.getEvents(EventType.GeomagneticStorm, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as GeomagneticStormJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-14T15:00:00-GST-001"),
            expectedEventType = EventType.GeomagneticStorm,
            expectedTime = instantOf(2022, 4, 14, 15, 0),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/GST/19773/-1",
            expectedLinkedEvents = listOf(
                EventJson.LinkedEventJson(EventId("2022-04-11T06:00:00-CME-001")) to EventType.CoronalMassEjection,
                EventJson.LinkedEventJson(EventId("2022-04-14T03:37:00-IPS-001")) to EventType.InterplanetaryShock
            )
        )
        assertEquals(
            listOf(
                GeomagneticStormJson.KpIndexJson(
                    observedTime = instantOf(2022, 4, 14, 18, 0),
                    kpIndex = 6,
                    source = "NOAA"
                ),
                GeomagneticStormJson.KpIndexJson(
                    observedTime = instantOf(2022, 4, 14, 18, 0),
                    kpIndex = 6,
                    source = "NOAA"
                )
            ), event.kpIndexes
        )
    }

    @Test
    fun `Validate interplanetary shock event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.InterplanetaryShock)))
        val events =
            dataSource.getEvents(EventType.InterplanetaryShock, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as InterplanetaryShockJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-06T22:45:00-IPS-001"),
            expectedEventType = EventType.InterplanetaryShock,
            expectedTime = instantOf(2022, 4, 6, 22, 45),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/IPS/19698/-1",
            expectedLinkedEvents = null
        )
        assertEquals(
            listOf(
                InstrumentJson("DSCOVR: PLASMAG"),
                InstrumentJson("ACE: SWEPAM"),
                InstrumentJson("ACE: MAG")
            ), event.instruments
        )
    }

    @Test
    fun `Validate solar flare event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.SolarFlare)))
        val events =
            dataSource.getEvents(EventType.SolarFlare, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as SolarFlareJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-28T02:24:00-FLR-001"),
            expectedEventType = EventType.SolarFlare,
            expectedTime = instantOf(2022, 4, 28, 2, 24),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/FLR/19972/-1",
            expectedLinkedEvents = listOf(EventJson.LinkedEventJson(EventId("2022-04-28T03:09:00-CME-001")) to EventType.CoronalMassEjection)
        )
        assertEquals(listOf(InstrumentJson("GOES-P: EXIS 1.0-8.0")), event.instruments)
        assertEquals(instantOf(2022, 4, 28, 3, 6), event.peakTime)
        assertEquals(instantOf(2022, 4, 28, 3, 27), event.endTime)
        assertEquals("C6.7", event.classType)
        assertEquals("N27W24", event.sourceLocation)
    }

    @Test
    fun `Validate solar energetic particle event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.SolarEnergeticParticle)))
        val events =
            dataSource.getEvents(EventType.SolarEnergeticParticle, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as SolarEnergeticParticleJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-02T14:39:00-SEP-001"),
            expectedEventType = EventType.SolarEnergeticParticle,
            expectedTime = instantOf(2022, 4, 2, 14, 39),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/SEP/19640/-1",
            expectedLinkedEvents = listOf(
                EventJson.LinkedEventJson(EventId("2022-04-02T12:56:00-FLR-001")) to EventType.SolarFlare,
                EventJson.LinkedEventJson(EventId("2022-04-02T13:38:00-CME-001")) to EventType.CoronalMassEjection
            )
        )
        assertEquals(listOf(InstrumentJson("SOHO: COSTEP 28.2-50.1 MeV")), event.instruments)
    }

    @Test
    fun `Validate magnetopause crossing event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.MagnetopauseCrossing)))
        val events =
            dataSource.getEvents(EventType.MagnetopauseCrossing, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as MagnetopauseCrossingJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-03-31T04:10:00-MPC-001"),
            expectedEventType = EventType.MagnetopauseCrossing,
            expectedTime = instantOf(2022, 3, 31, 4, 10),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/MPC/19603/-1",
            expectedLinkedEvents = listOf(
                EventJson.LinkedEventJson(EventId("2022-03-28T12:09:00-CME-001")) to EventType.CoronalMassEjection,
                EventJson.LinkedEventJson(EventId("2022-03-28T20:23:00-CME-001")) to EventType.CoronalMassEjection,
                EventJson.LinkedEventJson(EventId("2022-03-31T01:41:00-IPS-001")) to EventType.InterplanetaryShock
            )
        )
        assertEquals(listOf(InstrumentJson("MODEL: SWMF")), event.instruments)
    }

    @Test
    fun `Validate radiation belt enhancement event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.RadiationBeltEnhancement)))
        val events =
            dataSource.getEvents(EventType.RadiationBeltEnhancement, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as RadiationBeltEnhancementJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-02T20:05:00-RBE-001"),
            expectedEventType = EventType.RadiationBeltEnhancement,
            expectedTime = instantOf(2022, 4, 2, 20, 5),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/RBE/19647/-1",
            expectedLinkedEvents = listOf(
                EventJson.LinkedEventJson(EventId("2022-03-28T12:09:00-CME-001")) to EventType.CoronalMassEjection,
                EventJson.LinkedEventJson(EventId("2022-03-28T20:23:00-CME-001")) to EventType.CoronalMassEjection,
                EventJson.LinkedEventJson(EventId("2022-04-02T00:33:00-HSS-001")) to EventType.HighSpeedStream
            )
        )
        assertEquals(listOf(InstrumentJson("GOES-P: SEISS >2MeV")), event.instruments)
    }

    @Test
    fun `Validate high speed stream event parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.HighSpeedStream)))
        val events =
            dataSource.getEvents(EventType.HighSpeedStream, Instant.EPOCH, Instant.EPOCH)
        val event = events.single() as HighSpeedStreamJson
        validateCommonProperties(
            event,
            expectedId = EventId("2022-04-02T00:33:00-HSS-001"),
            expectedEventType = EventType.HighSpeedStream,
            expectedTime = instantOf(2022, 4, 2, 0, 33),
            expectedLink = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/HSS/19661/-1",
            expectedLinkedEvents = listOf(
                EventJson.LinkedEventJson(EventId("2022-04-02T00:33:00-IPS-001")) to EventType.InterplanetaryShock,
                EventJson.LinkedEventJson(EventId("2022-04-02T20:05:00-RBE-001")) to EventType.RadiationBeltEnhancement
            )
        )
        assertEquals(
            listOf(
                InstrumentJson("DSCOVR: PLASMAG"),
                InstrumentJson("ACE: SWEPAM"),
                InstrumentJson("ACE: MAG")
            ), event.instruments
        )
    }

    private fun validateCommonProperties(
        event: EventJson,
        expectedId: EventId,
        expectedEventType: EventType,
        expectedTime: Instant,
        expectedLink: String,
        expectedLinkedEvents: List<Pair<EventJson.LinkedEventJson, EventType>>?
    ) {
        assertEquals(expectedId, event.id)
        val (eventTypeFromId, _) = event.id.parse()
        assertEquals(expectedEventType, eventTypeFromId)
        assertEquals(expectedTime, event.time)
        assertEquals(expectedLink, event.link)
        assertEquals(expectedLinkedEvents?.map { it.first }, event.linkedEvents)
        assertEquals(
            expectedLinkedEvents?.map { it.second },
            event.linkedEvents?.map { it.id.parse().type })
    }

    private fun readSampleEvent(eventType: EventType, suffix: String = ""): Buffer {
        val cl = DonkiDataSourceNetworkTest::class.java
        val url = cl.getResource(
            "/${
                cl.packageName.replace(
                    '.',
                    '/'
                )
            }/${eventType.getExpectedUrlPath()}${suffix}.json"
        )
        return checkNotNull(url).readToBuffer()
    }

    @Test
    fun `Check that getEventById() throws if response is empty`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        assertFails { dataSource.getEventById(EventId("2022-04-07T05:36:00-CME-001")) }
    }

    @Test
    fun `Check that getEventById() throws if there is no event with given id`() = runTest {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.CoronalMassEjection)))
        assertFails { dataSource.getEventById(EventId("2022-04-07T05:36:00-CME-002")) }
    }

    @Test
    fun `Check that getEventById() succeeds`() = runTest {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.CoronalMassEjection)))
        val expectedId = EventId("2022-04-07T05:36:00-CME-001")
        val event = dataSource.getEventById(expectedId)
        assertEquals(expectedId, event.id)
    }

    @Test
    fun `Check that getEventById() succeeds if there are multiple events`() = runTest {
        server.enqueue(MockResponse().setBody(readSampleEvent(EventType.CoronalMassEjection, "_multiple")))
        val expectedId = EventId("2022-04-07T05:36:00-CME-002")
        val event = dataSource.getEventById(expectedId)
        assertEquals(expectedId, event.id)
    }
}

private fun EventType.getExpectedUrlPath() = when (this) {
    EventType.CoronalMassEjection -> "CME"
    EventType.GeomagneticStorm -> "GST"
    EventType.InterplanetaryShock -> "IPS"
    EventType.SolarFlare -> "FLR"
    EventType.SolarEnergeticParticle -> "SEP"
    EventType.MagnetopauseCrossing -> "MPC"
    EventType.RadiationBeltEnhancement -> "RBE"
    EventType.HighSpeedStream -> "HSS"
}

private fun Path.readToBuffer() = Buffer().apply {
    this@readToBuffer.inputStream().use { readFrom(it) }
}

private fun URL.readToBuffer() = Buffer().apply {
    this@readToBuffer.openStream().use { readFrom(it) }
}
