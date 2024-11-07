// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.equeim.spacer.donki.data.common.DonkiJson
import org.equeim.spacer.donki.data.notifications.network.NotificationJson
import org.equeim.spacer.donki.getTestResourceInputStream
import org.equeim.spacer.donki.getTestResourceURL
import org.equeim.spacer.donki.instantOf
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NotificationsParsingTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `Validate that dataset parses without exceptions`() {
        val datasetsUrl = NotificationsParsingTest::class.java.getTestResourceURL("datasets")
        Files.list(Paths.get(datasetsUrl.toURI())).parallel().forEach { datasetPath ->
            try {
                datasetPath.inputStream().use {
                    DonkiJson.decodeFromStream<List<NotificationJson>>(it)
                }
            } catch (e: Exception) {
                fail("Failed to parse $datasetsUrl", e)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `Validate notification parsing`() {
        val notifications: List<NotificationJson> = NotificationsParsingTest::class.java.getTestResourceInputStream("notification.json").use {
            DonkiJson.decodeFromStream(it)
        }
        assertEquals(1, notifications.size)
        val notification = notifications.first()
        assertEquals("20241002-AL-002", notification.id.stringValue)
        assertEquals(NotificationType.CoronalMassEjection, notification.type)
        assertEquals(instantOf(2022, 1, 19, 15, 0), notification.time)
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
            "Update on CME with ID 2024-10-01T23:09:00-CME-001 (see previous notification 20241002-AL-001). Based on preliminary analysis by the Moon to Mars Space Weather Analysis Office and heliospheric modeling carried out at NASA Community Coordinated Modeling Center, it is estimated that this CME may affect Lucy, Solar Orbiter (glancing blow), and STEREO A (glancing blow).  The leading edge or flank of the CME may reach Lucy at 2024-10-04T19:43Z, Solar Orbiter at 2024-10-03T00:00Z, and STEREO A at 2024-10-04T16:30Z (plus minus 7 hours).",
            notification.body.findSubtitle()
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