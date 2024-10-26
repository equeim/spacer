// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.FakeClock
import org.equeim.spacer.donki.TEST_INSTANT_INSIDE_TEST_WEEK
import org.equeim.spacer.donki.TEST_WEEK
import org.equeim.spacer.donki.TEST_WEEK_NEAREST_FUTURE
import org.equeim.spacer.donki.TEST_WEEK_NEAREST_PAST
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.HttpErrorResponse
import org.equeim.spacer.donki.data.common.InvalidApiKeyError
import org.equeim.spacer.donki.data.common.TooManyRequestsError
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDatabase
import org.equeim.spacer.donki.getTestResourceInputStream
import org.equeim.spacer.donki.instantOf
import org.equeim.spacer.donki.readTestResourceToBuffer
import org.equeim.spacer.donki.timeZoneParameters
import org.equeim.spacer.donki.weekOf
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal val EMPTY_PAGING_STATE = PagingState<Week, CachedNotificationSummary>(
    listOf(PagingSource.LoadResult.Page(emptyList(), null, null)),
    anchorPosition = null,
    config = PagingConfig(20),
    leadingPlaceholderCount = 0
)

internal val SAMPLE_NOTIFICATION: String =
    NotificationsParsingTest::class.java.getTestResourceInputStream("notification.json")
        .use { it.reader().readText() }
internal val SAMPLE_NOTIFICATION_ID = NotificationId("20241002-AL-002")

internal class MockWebServerDispatcher : Dispatcher() {
    var respond: (() -> MockResponse)? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val respond = this.respond ?: return MockResponse().setResponseCode(500)
        return respond()
    }
}

internal var MockWebServer.respond: (() -> MockResponse)?
    get() = (dispatcher as MockWebServerDispatcher).respond
    set(value) {
        (dispatcher as MockWebServerDispatcher).respond = value
    }

internal fun MockWebServer.respondWithSampleNotification() {
    respond = { MockResponse().setBody(SAMPLE_NOTIFICATION) }
}

internal fun MockWebServer.respondWithEmptyBody() {
    respond = { MockResponse().setBody(Buffer()) }
}

internal fun MockWebServer.respondWithError() {
    respond = null
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NotificationSummariesPagingSourceTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = FakeClock(TEST_INSTANT_INSIDE_TEST_WEEK, systemTimeZone)
    private val server = MockWebServer().apply {
        dispatcher = MockWebServerDispatcher()
        start()
    }

    private val db: NotificationsDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        NotificationsDatabase::class.java
    ).setQueryExecutor(testExecutor).setTransactionExecutor(testExecutor).allowMainThreadQueries()
        .build()
    private val repository = DonkiNotificationsRepository(
        customNasaApiKey = flowOf(null),
        context = ApplicationProvider.getApplicationContext(),
        baseUrl = server.url("/"),
        db = db,
        coroutineDispatchers = coroutineDispatchers,
        clock = clock
    )
    private var pagingSource: NotificationSummariesPagingSource =
        repository.createPagingSource(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = null
            ), emptyFlow()
        )

    override fun after() {
        server.shutdown()
        repository.close()
        super.after()
    }

    @Test
    fun `getRefreshKey() returns null`() = runTest {
        assertNull(pagingSource.getRefreshKey(EMPTY_PAGING_STATE))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Loading first page when current week is not cached`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respondWithSampleNotification()
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)
        result.data.validateSampleNotification()

        advanceUntilIdle()

        loadingFirstPageFromCache()
    }

    @Test
    fun `Loading first page when current week was cached less than an hour ago`() = runTest {
        prepareInitialState(TEST_WEEK, TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(59))
        loadingFirstPageFromCache()
    }

    @Test
    fun `Loading first page when current week was cached more than an hour ago`() = runTest {
        prepareInitialState(TEST_WEEK, TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(61))
        loadingFirstPageFromCache()
    }

    private suspend fun loadingFirstPageFromCache() {
        server.respondWithError()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(TEST_WEEK_NEAREST_PAST, result.nextKey)
        result.data.validateSampleNotification()
    }

    @Test
    fun `Loading next page when its week is not cached - new notifications are empty`() = runTest {
        clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
        loadingNextPageAndUpdatingCache(emptyResponse = true)
    }

    @Test
    fun `Loading next page when its week is not cached - new notifications are not empty`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            loadingNextPageAndUpdatingCache(emptyResponse = false)
        }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached notifications are empty and new ones are too`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                TEST_INSTANT_INSIDE_TEST_WEEK,
                emptyResponse = true
            )
            loadingNextPageAndUpdatingCache(emptyResponse = true)
        }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached notifications are empty and new ones are not`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                TEST_INSTANT_INSIDE_TEST_WEEK,
                emptyResponse = true
            )
            loadingNextPageAndUpdatingCache(emptyResponse = false)
        }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached notifications are not empty and new ones are not changed`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                TEST_INSTANT_INSIDE_TEST_WEEK,
                emptyResponse = false
            )
            loadingNextPageAndUpdatingCache(emptyResponse = false)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.loadingNextPageAndUpdatingCache(emptyResponse: Boolean) {
        val loadPage = suspend {
            val params = PagingSource.LoadParams.Append(TEST_WEEK, 20, false)
            val result = pagingSource.load(params).assertIsPage()
            assertNotNull(result.prevKey).validate()
            assertEquals(TEST_WEEK_NEAREST_FUTURE, result.prevKey)
            assertNotNull(result.nextKey).validate()
            assertEquals(TEST_WEEK_NEAREST_PAST, result.nextKey)
            if (emptyResponse) {
                assertTrue(result.data.isEmpty())
            } else {
                result.data.validateSampleNotification()
            }
        }
        // From network
        if (emptyResponse) {
            server.respondWithEmptyBody()
        } else {
            server.respondWithSampleNotification()
        }
        loadPage()
        advanceUntilIdle()
        // From cache
        server.respondWithError()
        loadPage()
    }

    @Test
    fun `Loading next page when it was cached before its last day but less than an hour ago`() = runTest {
        clock.instant = TEST_WEEK.getInstantAfterLastDay()
        prepareInitialState(TEST_WEEK, TEST_WEEK.getInstantAfterLastDay() - Duration.ofMinutes(59))

        loadNextPageFromCache()
    }

    @Test
    fun `Loading next page when its week was cached after its last day`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay()
            prepareInitialState(
                TEST_WEEK,
                clock.instant,
                emptyResponse = false
            )

            loadNextPageFromCache()
        }

    private suspend fun loadNextPageFromCache() {
        val params = PagingSource.LoadParams.Append(TEST_WEEK, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate()
        assertEquals(TEST_WEEK_NEAREST_FUTURE, result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(TEST_WEEK_NEAREST_PAST, result.nextKey)
        result.data.validateSampleNotification()
    }

    @Test
    fun `Validate load() if requested week is in the future`() = runTest {
        val params = PagingSource.LoadParams.Refresh(TEST_WEEK_NEAREST_FUTURE, 20, false)
        pagingSource.load(params).assertIsInvalid()
    }

    @Test
    fun `Verify 403 error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse().setResponseCode(403) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<InvalidApiKeyError>(result.throwable)
    }

    @Test
    fun `Verify 429 error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse().setResponseCode(429) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<TooManyRequestsError>(result.throwable)
    }

    @Test
    fun `Verify 500 error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse().setResponseCode(500) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<HttpErrorResponse>(result.throwable)
    }

    @Test
    fun `Verify network error handling`() = runTest {
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respond = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        val result = pagingSource.load(params).assertIsError()
        assertIs<IOException>(result.throwable)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Validate that load() handles cancellation`() = runTest {
        server.respond = { MockResponse().setHeadersDelay(1, TimeUnit.SECONDS) }
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
            MockResponse().setBody(
                NotificationsParsingTest::class.java.readTestResourceToBuffer("datasets/2016-01-01_2016-01-31.json")
            )
        }
        pagingSource.invalidate()
        pagingSource = repository.createPagingSource(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    firstDayInstant = instantOf(2016, 1, 18, 0, 0),
                    instantAfterLastDay = instantOf(2016, 1, 25, 0, 0),
                )
            ),
            emptyFlow()
        )
        val params = PagingSource.LoadParams.Append(weekOf(2016, 1, 18), 20, false)
        val resultFromNetwork = pagingSource.load(params).assertIsPage()

        val expectedNotificationIds = listOf(
            NotificationId("20160124-AL-002"),
            NotificationId("20160124-AL-001"),
            NotificationId("20160122-7D-001"),
            NotificationId("20160121-AL-002"),
            NotificationId("20160121-AL-001")
        )

        assertEquals(
            expectedNotificationIds,
            resultFromNetwork.data.map { it.id }
        )

        advanceUntilIdle()

        server.respondWithError()
        val resultFromCache = pagingSource.load(params).assertIsPage()
        assertEquals(
            expectedNotificationIds,
            resultFromCache.data.map { it.id }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Validate date range filtering when range is inside the week`() = runTest {
        server.respond = {
            MockResponse().setBody(
                NotificationsParsingTest::class.java.readTestResourceToBuffer("datasets/2016-01-01_2016-01-31.json")
            )
        }
        pagingSource.invalidate()
        pagingSource = repository.createPagingSource(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    firstDayInstant = instantOf(2016, 1, 19, 0, 0),
                    instantAfterLastDay = instantOf(2016, 1, 22, 0, 0),
                )
            ),
            emptyFlow()
        )
        val params = PagingSource.LoadParams.Append(weekOf(2016, 1, 18), 20, false)
        val resultFromNetwork = pagingSource.load(params).assertIsPage()

        val expectedNotificationIds = listOf(
            NotificationId("20160121-AL-002"),
            NotificationId("20160121-AL-001")
        )

        assertEquals(
            expectedNotificationIds,
            resultFromNetwork.data.map { it.id }
        )

        advanceUntilIdle()

        server.respondWithError()
        val resultFromCache = pagingSource.load(params).assertIsPage()
        assertEquals(
            expectedNotificationIds,
            resultFromCache.data.map { it.id }
        )
    }

    private suspend fun prepareInitialState(
        week: Week,
        loadTime: Instant,
        emptyResponse: Boolean = false
    ) {
        val saved = clock.instant
        clock.instant = loadTime
        if (emptyResponse) {
            server.respondWithEmptyBody()
        } else {
            server.respondWithSampleNotification()
        }
        repository.updateNotificationsForWeek(week)
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

        private fun PagingSource.LoadResult<Week, CachedNotificationSummary>.assertIsPage() =
            assertIs<PagingSource.LoadResult.Page<Week, CachedNotificationSummary>>(this)

        private fun PagingSource.LoadResult<Week, CachedNotificationSummary>.assertIsInvalid() =
            assertIs<PagingSource.LoadResult.Invalid<Week, CachedNotificationSummary>>(this)

        private fun PagingSource.LoadResult<Week, CachedNotificationSummary>.assertIsError() =
            assertIs<PagingSource.LoadResult.Error<Week, CachedNotificationSummary>>(this)

        private fun List<CachedNotificationSummary>.validateSampleNotification() {
            assertEquals(SAMPLE_NOTIFICATION_ID, this.single().id)
        }
    }
}
