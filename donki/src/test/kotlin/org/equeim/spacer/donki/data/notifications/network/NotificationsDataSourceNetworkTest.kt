// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.apiKey
import org.equeim.spacer.donki.data.DEFAULT_NASA_API_KEY
import org.equeim.spacer.donki.data.common.InvalidApiKeyError
import org.equeim.spacer.donki.data.common.TooManyRequestsError
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.donki.data.notifications.findTitle
import org.equeim.spacer.donki.data.notifications.findWebLinks
import org.equeim.spacer.donki.getTestResource
import org.equeim.spacer.donki.instantOf
import org.equeim.spacer.donki.readToBuffer
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Paths
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
class NotificationsDataSourceNetworkTest : BaseCoroutineTest() {
    private val server = MockWebServer()
    private lateinit var dataSource: NotificationsDataSourceNetwork
    private val nasaApiKey = MutableStateFlow(DEFAULT_NASA_API_KEY)

    @BeforeTest
    override fun before() {
        super.before()
        server.start()
        dataSource = NotificationsDataSourceNetwork(nasaApiKey, server.url("/"))
    }

    @AfterTest
    override fun after() {
        server.shutdown()
        super.after()
    }

    @Test
    fun `Validate url`() = runTest {
        val week = Week(LocalDate.of(2016, 8, 29))
        server.enqueue(MockResponse().setBody(""))

        dataSource.getNotifications(week)

        val request = server.takeRequest()
        val url = assertNotNull(request.requestUrl)
        assertEquals("notifications", url.pathSegments.single())
        assertEquals("2016-08-29", url.queryParameter("startDate"))
        assertEquals("2016-09-04", url.queryParameter("endDate"))
        assertEquals(DEFAULT_NASA_API_KEY, url.apiKey)
    }

    @Test
    fun `Validate 403 error handling`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFailsWith<InvalidApiKeyError> {
            dataSource.getNotifications(Week(LocalDate.of(2016, 8, 29)))
        }
    }

    @Test
    fun `Validate 429 error handling`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertFailsWith<TooManyRequestsError> {
            dataSource.getNotifications(Week(LocalDate.of(2016, 8, 29)))
        }
    }

    @Test
    fun `Validate request interrupton on API key change`() = runTest {
        server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(""))
        val requestJob = launch {
            dataSource.getNotifications(Week(LocalDate.of(2016, 8, 29)))
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
        val datasetsUrl = NotificationsDataSourceNetworkTest::class.java.getTestResource("datasets")
        Files.list(Paths.get(datasetsUrl.toURI())).asSequence().forEach { datasetPath ->
            server.enqueue(MockResponse().setBody(datasetPath.readToBuffer()))
            dataSource.getNotifications(Week(LocalDate.MIN))
        }
    }

    @Test
    fun `Validate notification parsing`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                NotificationsDataSourceNetwork::class.java.getTestResource(
                    "notification.json"
                ).readToBuffer()
            )
        )
        val notifications =
            dataSource.getNotifications(Week(LocalDate.of(2024, 9, 30)))
        val notification = notifications.single()
        assertEquals("20241002-AL-002", notification.id.stringValue)
        assertEquals(NotificationType.CoronalMassEjection, notification.type)
        assertEquals(instantOf(2024, 10, 2, 13, 17), notification.time)
        assertEquals("https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/Alert/33676/1", notification.link)
        assertEquals(
            """
            ## Community Coordinated Modeling Center Database Of Notifications, Knowledge, Information ( CCMC DONKI )
            ## Message Type: Space Weather Notification - CME update (Lucy, Solar Orbiter, STEREO A, Missions Near Earth)
            ##
            ## Message Issue Date: 2024-10-02T13:17:33Z
            ## Message ID: 20241002-AL-002
            ##
            ## Disclaimer: NOAA's Space Weather Prediction Center is the United States Government official source for space weather forecasts. This "Experimental Research Information" consists of preliminary NASA research products and should be interpreted and used accordingly.


            ## Summary:

            Update on CME with ID 2024-10-01T23:09:00-CME-001 (see previous notification 20241002-AL-001). Based on preliminary analysis by the Moon to Mars Space Weather Analysis Office and heliospheric modeling carried out at NASA Community Coordinated Modeling Center, it is estimated that this CME may affect Lucy, Solar Orbiter (glancing blow), and STEREO A (glancing blow).  The leading edge or flank of the CME may reach Lucy at 2024-10-04T19:43Z, Solar Orbiter at 2024-10-03T00:00Z, and STEREO A at 2024-10-04T16:30Z (plus minus 7 hours). 

            The simulation also indicates that the CME may impact NASA missions near Earth. Simulations indicate that the leading edge of the CME will reach NASA missions near Earth at about 2024-10-04T20:40Z (plus minus 7 hours). The roughly estimated expected range of the maximum Kp index is 4-6 (below minor to moderate).
               

            Updated CME parameters are (event upgraded to C-type):

            Start time of the event: 2024-10-01T23:09Z.

            Estimated speed: ~594 km/s.

            Estimated opening half-angle: 38 deg.

            Direction (lon./lat.): -19/-10 in Heliocentric Earth Equatorial coordinates.

            Activity ID: 2024-10-01T23:09:00-CME-001


            Links to the movies of the modeled event (includes CME: 2024-10-01T23:09:00-CME-001):

            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-den.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-vel.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-den-Stereo_A.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-vel-Stereo_A.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_timeline.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_STA_timeline.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_Lucy_timeline.gif
            http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_SolO_timeline.gif


            ## Notes: 

            This CME event (2024-10-01T23:09:00-CME-001) is associated with X7.1 flare with ID 2024-10-01T21:58:00-FLR-001 from Active Region 13942 (S17E18) which peaked at 2024-10-01T22:20Z (see notifications 20241001-AL-003 and 20241001-AL-004).


            SCORE CME typification system:
            S-type: CMEs with speeds less than 500 km/s
            C-type: Common 500-999 km/s
            O-type: Occasional 1000-1999 km/s
            R-type: Rare 2000-2999 km/s
            ER-type: Extremely Rare >3000 km/s



        """.trimIndent(), notification.body
        )

        assertEquals(
            "CME update (Lucy, Solar Orbiter, STEREO A, Missions Near Earth)",
            notification.body.findTitle()
        )
        assertEquals(
            listOf(
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-den.gif" to (1812 until 1884),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-vel.gif" to (1885 until 1957),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-den-Stereo_A.gif" to (1958 until 2039),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-vel-Stereo_A.gif" to (2040 until 2121),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_timeline.gif" to (2122 until 2201),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_STA_timeline.gif" to (2202 until 2285),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_Lucy_timeline.gif" to (2286 until 2370),
                "http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_SolO_timeline.gif" to (2371 until 2455)
            ),
            notification.body.findWebLinks()
        )
    }
}