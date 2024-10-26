// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import java.net.HttpURLConnection
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class NotificationsDataSourceNetworkTest : BaseCoroutineTest() {
    private val server = MockWebServer()
    private lateinit var dataSource: NotificationsDataSourceNetwork
    private val nasaApiKey = MutableStateFlow(DEFAULT_NASA_API_KEY)

    @BeforeTest
    override fun before() {
        super.before()
        server.start()
        dataSource = NotificationsDataSourceNetwork(
            customNasaApiKey = nasaApiKey,
            baseUrl = server.url("/")
        )
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
}
