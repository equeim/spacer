// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import org.equeim.spacer.donki.CoroutinesRule
import org.equeim.spacer.donki.FakeClock
import org.equeim.spacer.donki.TEST_INSTANT_INSIDE_TEST_WEEK
import org.equeim.spacer.donki.TEST_WEEK
import org.equeim.spacer.donki.TEST_WEEK_NEAREST_FUTURE
import org.equeim.spacer.donki.TEST_WEEK_NEAREST_PAST
import org.equeim.spacer.donki.createInMemoryTestDatabase
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.createDonkiOkHttpClient
import org.equeim.spacer.donki.data.events.cache.EventsCacheDatabase
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import org.equeim.spacer.donki.getTestResourceInputStream
import org.equeim.spacer.donki.instantOf
import org.equeim.spacer.donki.readTestResourceToBuffer
import org.equeim.spacer.donki.timeZoneParameters
import org.equeim.spacer.donki.weekOf
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

internal val EMPTY_PAGING_STATE = PagingState<Week, EventSummary>(
    listOf(PagingSource.LoadResult.Page(emptyList(), null, null)),
    anchorPosition = null,
    config = PagingConfig(20),
    leadingPlaceholderCount = 0
)

internal val SAMPLE_EVENTS: Map<EventType, String> = EventType.entries.associateWith { eventType ->
    EventsParsingTest::class.java.getTestResourceInputStream("${eventType.stringValue}.json")
        .use { it.reader().readText() }
}

internal val SAMPLE_EVENTS_IDS: Set<EventId> = setOf(
    EventId("2022-01-19T05:36:00-CME-001"),
    EventId("2022-01-19T02:24:00-FLR-001"),
    EventId("2022-01-19T15:00:00-GST-001"),
    EventId("2022-01-19T00:33:00-HSS-001"),
    EventId("2022-01-19T22:45:00-IPS-001"),
    EventId("2022-01-19T04:10:00-MPC-001"),
    EventId("2022-01-19T20:05:00-RBE-001"),
    EventId("2022-01-19T14:39:00-SEP-001"),
)

internal class MockWebServerDispatcher : Dispatcher() {
    var respond: ((EventType) -> MockResponse)? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val respond = this.respond ?: return MockResponse(code = 500)
        val eventType = when (assertNotNull(request.url).pathSegments.single()) {
            "CME" -> EventType.CoronalMassEjection
            "FLR" -> EventType.SolarFlare
            "GST" -> EventType.GeomagneticStorm
            "HSS" -> EventType.HighSpeedStream
            "IPS" -> EventType.InterplanetaryShock
            "MPC" -> EventType.MagnetopauseCrossing
            "RBE" -> EventType.RadiationBeltEnhancement
            "SEP" -> EventType.SolarEnergeticParticle
            else -> fail("Unexpected URL ${request.url}")
        }
        return respond(eventType)
    }
}

internal var MockWebServer.respond: ((EventType) -> MockResponse)?
    get() = (dispatcher as MockWebServerDispatcher).respond
    set(value) {
        (dispatcher as MockWebServerDispatcher).respond = value
    }

internal fun MockWebServer.respondWithSampleEvents() {
    respond = { MockResponse.Builder().body(SAMPLE_EVENTS[it]!!).build() }
}

internal fun MockWebServer.respondWithEmptyBody() {
    respond = { MockResponse.Builder().body(Buffer()).build() }
}

internal fun MockWebServer.respondWithError() {
    respond = null
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EventsSummariesPagingSourceTest(systemTimeZone: ZoneId) {
    @get:Rule
    val coroutinesRule = CoroutinesRule()

    private val clock = FakeClock(TEST_INSTANT_INSIDE_TEST_WEEK, systemTimeZone)
    private val server = MockWebServer().apply {
        dispatcher = MockWebServerDispatcher()
        start()
    }

    private val db: EventsCacheDatabase = createInMemoryTestDatabase(coroutinesRule.coroutineDispatchers)
    private val repository = DonkiEventsRepository(
        customNasaApiKey = flowOf(null),
        okHttpClient = createDonkiOkHttpClient(),
        baseUrl = server.url("/"),
        context = ApplicationProvider.getApplicationContext(),
        db = db,
        coroutineDispatchers = coroutinesRule.coroutineDispatchers,
        clock = clock
    )
    private var pagingSource: EventsSummariesPagingSource =
        repository.createPagingSource(
            DonkiEventsRepository.Filters(
                types = EventType.entries,
                dateRange = null
            )
        )

    @AfterTest
    fun after() {
        server.close()
        repository.close()
        pagingSource.invalidate()
    }

    @Test
    fun `getRefreshKey() returns null`() = runTest {
        assertNull(pagingSource.getRefreshKey(EMPTY_PAGING_STATE))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Loading first page when current week is not cached`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respondWithSampleEvents()
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)
        result.data.validateSampleEvents()

        advanceUntilIdle()

        loadingFirstPageFromCache()
    }

    @Test
    fun `Loading first page when current week was cached before its last day but less than an hour ago`() = runTest {
        prepareInitialState(
            week = TEST_WEEK,
            loadTime = TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(59),
            emptyResponse = false
        )
        loadingFirstPageFromCache()
    }

    @Test
    fun `Loading first page when current week was cached more than an hour ago`() = runTest {
        prepareInitialState(
            week = TEST_WEEK,
            loadTime = TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(61),
            emptyResponse = false
        )
        loadingFirstPageFromCache()
    }

    private suspend fun loadingFirstPageFromCache() {
        server.respondWithError()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(TEST_WEEK_NEAREST_PAST, result.nextKey)
        result.data.validateSampleEvents()
    }

    @Test
    fun `Loading next page when its week is not cached - new events are empty`() = runTest {
        clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
        loadingNextPageAndUpdatingCache(emptyResponse = true)
    }

    @Test
    fun `Loading next page when its week is not cached - new events are not empty`() = runTest {
        clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
        loadingNextPageAndUpdatingCache(emptyResponse = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.loadingNextPageAndUpdatingCache(emptyResponse: Boolean) {
        // From network
        if (emptyResponse) {
            server.respondWithEmptyBody()
        } else {
            server.respondWithSampleEvents()
        }
        checkLoadingNextPage(shouldBeEmpty = emptyResponse)
        advanceUntilIdle()
        // From cache
        server.respondWithError()
        checkLoadingNextPage(shouldBeEmpty = emptyResponse)
    }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached events are empty`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                clock.instant - Duration.ofMinutes(61),
                emptyResponse = true
            )
            checkLoadingNextPage(shouldBeEmpty = true)
        }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached events are not empty`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                clock.instant - Duration.ofMinutes(61),
                emptyResponse = false
            )
            checkLoadingNextPage(shouldBeEmpty = false)
        }

    @Test
    fun `Loading next page when it was cached less than a week after its last day, and less than an hour ago`() = runTest {
        clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
        prepareInitialState(
            week = TEST_WEEK,
            loadTime = clock.instant - Duration.ofMinutes(59),
            emptyResponse = false
        )
        checkLoadingNextPage(shouldBeEmpty = false)
    }

    @Test
    fun `Loading next page when its week was cached more than a week after its last day`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(7)
            prepareInitialState(
                TEST_WEEK,
                clock.instant,
                emptyResponse = false
            )
            checkLoadingNextPage(shouldBeEmpty = false)
        }

    private suspend fun checkLoadingNextPage(shouldBeEmpty: Boolean) {
        val params = PagingSource.LoadParams.Append(TEST_WEEK, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate()
        assertEquals(TEST_WEEK_NEAREST_FUTURE, result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(TEST_WEEK_NEAREST_PAST, result.nextKey)
        if (shouldBeEmpty) {
            assertTrue(result.data.isEmpty())
        } else {
            result.data.validateSampleEvents()
        }
    }

    @Test
    fun `Validate load() if requested week is in the future`() = runTest {
        val params = PagingSource.LoadParams.Refresh(TEST_WEEK_NEAREST_FUTURE, 20, false)
        pagingSource.load(params).assertIsInvalid()
    }

    @Test
    fun `Verify 403 error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse(code = 403) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<DonkiNetworkDataSourceException.InvalidApiKey>(result.throwable)
    }

    @Test
    fun `Verify 429 error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse(code = 429) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<DonkiNetworkDataSourceException.TooManyRequests>(result.throwable)
    }

    @Test
    fun `Verify 500 error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse(code = 500) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<DonkiNetworkDataSourceException.HttpErrorResponse>(result.throwable)
    }

    @Test
    fun `Verify network error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build() }
        val result = pagingSource.load(params).assertIsError()
        assertIs<DonkiNetworkDataSourceException.NetworkError>(result.throwable)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Validate that load() handles cancellation`() = runTest {
        server.respond = { MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).build() }
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val job = launch {
            assertFailsWith<CancellationException> {
                pagingSource.load(params)
            }
        }
        runCurrent()
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Validate that successive loads of empty pages throttle`() = runTest {
        server.respondWithEmptyBody()
        repeat(2) {
            pagingSource.load(PagingSource.LoadParams.Append(TEST_WEEK, 20, false))
        }
        assertEquals(1000, currentTime)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Validate date range filtering when range is the same as week`() = runTest {
        server.respond = {
            MockResponse.Builder().body(
                EventsParsingTest::class.java.readTestResourceToBuffer("datasets/${it.stringValue}_2016-01-01_2016-01-31.json")
            ).build()
        }
        pagingSource.invalidate()
        pagingSource = repository.createPagingSource(
            DonkiEventsRepository.Filters(
                types = EventType.entries,
                dateRange = DateRange(
                    firstDayInstant = instantOf(2016, 1, 18, 0, 0),
                    instantAfterLastDay = instantOf(2016, 1, 25, 0, 0),
                )
            )
        )
        val params = PagingSource.LoadParams.Append(weekOf(2016, 1, 18), 20, false)
        val resultFromNetwork = pagingSource.load(params).assertIsPage()

        val expectedEventIds = mapOf(
            EventType.CoronalMassEjection to listOf(
                EventId("2016-01-24T13:24:00-CME-001"),
                EventId("2016-01-22T19:12:00-CME-001"),
                EventId("2016-01-21T07:00:00-CME-001"),
                EventId("2016-01-19T13:09:00-CME-001"),
            ),
            EventType.GeomagneticStorm to listOf(EventId("2016-01-21T03:00:00-GST-001")),
            EventType.HighSpeedStream to listOf(EventId("2016-01-21T10:00:00-HSS-001")),
            EventType.InterplanetaryShock to listOf(
                EventId("2016-01-21T00:48:00-IPS-001"),
                EventId("2016-01-18T21:00:00-IPS-001")
            ),
            EventType.RadiationBeltEnhancement to listOf(EventId("2016-01-24T15:05:00-RBE-001")),
        )

        assertEquals(
            expectedEventIds,
            resultFromNetwork.data.groupBy(
                keySelector = EventSummary::type,
                valueTransform = EventSummary::id
            )
        )

        advanceUntilIdle()

        server.respondWithError()
        val resultFromCache = pagingSource.load(params).assertIsPage()
        assertEquals(
            expectedEventIds,
            resultFromCache.data.groupBy(
                keySelector = EventSummary::type,
                valueTransform = EventSummary::id
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Validate date range filtering when range is inside the week`() = runTest {
        server.respond = {
            MockResponse.Builder().body(
                EventsParsingTest::class.java.readTestResourceToBuffer("datasets/${it.stringValue}_2016-01-01_2016-01-31.json")
            ).build()
        }
        pagingSource.invalidate()
        pagingSource = repository.createPagingSource(
            DonkiEventsRepository.Filters(
                types = EventType.entries,
                dateRange = DateRange(
                    firstDayInstant = instantOf(2016, 1, 19, 0, 0),
                    instantAfterLastDay = instantOf(2016, 1, 22, 0, 0),
                )
            )
        )
        val params = PagingSource.LoadParams.Append(weekOf(2016, 1, 18), 20, false)
        val resultFromNetwork = pagingSource.load(params).assertIsPage()

        val expectedEventIds = mapOf(
            EventType.CoronalMassEjection to listOf(
                EventId("2016-01-21T07:00:00-CME-001"),
                EventId("2016-01-19T13:09:00-CME-001"),
            ),
            EventType.GeomagneticStorm to listOf(EventId("2016-01-21T03:00:00-GST-001")),
            EventType.HighSpeedStream to listOf(EventId("2016-01-21T10:00:00-HSS-001")),
            EventType.InterplanetaryShock to listOf(EventId("2016-01-21T00:48:00-IPS-001")),
        )

        assertEquals(
            expectedEventIds,
            resultFromNetwork.data.groupBy(
                keySelector = EventSummary::type,
                valueTransform = EventSummary::id
            )
        )

        advanceUntilIdle()

        server.respondWithError()
        val resultFromCache = pagingSource.load(params).assertIsPage()
        assertEquals(
            expectedEventIds,
            resultFromCache.data.groupBy(
                keySelector = EventSummary::type,
                valueTransform = EventSummary::id
            )
        )
    }

    private suspend fun prepareInitialState(
        week: Week,
        loadTime: Instant,
        emptyResponse: Boolean
    ) {
        val saved = clock.instant
        clock.instant = loadTime
        if (emptyResponse) {
            server.respondWithEmptyBody()
        } else {
            server.respondWithSampleEvents()
        }
        coroutineScope {
            for (eventType in EventType.entries) {
                launch { repository.updateEventsForWeek(week, eventType) }
            }
        }
        server.respondWithError()
        clock.instant = saved
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()

        private fun Week.validate() {
            val firstDayDateTime = getFirstDayInstant().atOffset(ZoneOffset.UTC)
            assertEquals(firstDayDateTime.truncatedTo(ChronoUnit.DAYS), firstDayDateTime)
            assertEquals(DayOfWeek.MONDAY, firstDayDateTime.dayOfWeek)
        }

        private fun PagingSource.LoadResult<Week, EventSummary>.assertIsPage() =
            assertIs<PagingSource.LoadResult.Page<Week, EventSummary>>(this)

        private fun PagingSource.LoadResult<Week, EventSummary>.assertIsInvalid() =
            assertIs<PagingSource.LoadResult.Invalid<Week, EventSummary>>(this)

        private fun PagingSource.LoadResult<Week, EventSummary>.assertIsError() =
            assertIs<PagingSource.LoadResult.Error<Week, EventSummary>>(this)

        private fun List<EventSummary>.validateSampleEvents() {
            assertEquals(SAMPLE_EVENTS_IDS, this.map { it.id }.toSet())
            assertEquals(this.sortedByDescending { it.time }, this)
        }
    }
}
