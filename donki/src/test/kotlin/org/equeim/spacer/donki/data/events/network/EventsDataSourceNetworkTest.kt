// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.equeim.spacer.donki.CoroutinesRule
import org.equeim.spacer.donki.MockkLogRule
import org.equeim.spacer.donki.apiKey
import org.equeim.spacer.donki.data.DEFAULT_NASA_API_KEY
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.createDonkiOkHttpClient
import org.equeim.spacer.donki.data.events.EventType
import org.junit.Rule
import java.net.HttpURLConnection
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class EventsDataSourceNetworkTest {
    @get:Rule
    val coroutinesRule = CoroutinesRule()

    @get:Rule
    val logRule = MockkLogRule()

    private val server = MockWebServer().apply {
        start()
    }
    private val nasaApiKey = MutableStateFlow(DEFAULT_NASA_API_KEY)
    private val dataSource = EventsDataSourceNetwork(
        customNasaApiKey = nasaApiKey,
        okHttpClient = createDonkiOkHttpClient(),
        baseUrl = server.url("/")
    )

    @AfterTest
    fun after() {
        server.shutdown()
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
}
