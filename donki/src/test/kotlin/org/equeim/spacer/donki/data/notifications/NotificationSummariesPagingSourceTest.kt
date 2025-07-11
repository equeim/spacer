// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDatabase
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
        val respond = this.respond ?: return MockResponse(code = 500)
        return respond()
    }
}

internal var MockWebServer.respond: (() -> MockResponse)?
    get() = (dispatcher as MockWebServerDispatcher).respond
    set(value) {
        (dispatcher as MockWebServerDispatcher).respond = value
    }

internal fun MockWebServer.respondWithSampleNotification() {
    respond = { MockResponse.Builder().body(SAMPLE_NOTIFICATION).build() }
}

internal fun MockWebServer.respondWithEmptyBody() {
    respond = { MockResponse.Builder().body(Buffer()).build() }
}

internal fun MockWebServer.respondWithError() {
    respond = null
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NotificationSummariesPagingSourceTest(systemTimeZone: ZoneId) {
    @get:Rule
    val coroutinesRule = CoroutinesRule()

    private val clock = FakeClock(TEST_INSTANT_INSIDE_TEST_WEEK, systemTimeZone)
    private val server = MockWebServer().apply {
        dispatcher = MockWebServerDispatcher()
        start()
    }

    private val db: NotificationsDatabase = createInMemoryTestDatabase(coroutinesRule.coroutineDispatchers)
    private val repository = DonkiNotificationsRepository(
        customNasaApiKey = flowOf(null),
        okHttpClient = createDonkiOkHttpClient(),
        baseUrl = server.url("/"),
        context = ApplicationProvider.getApplicationContext(),
        db = db,
        coroutineDispatchers = coroutinesRule.coroutineDispatchers,
        clock = clock
    )
    private var pagingSource: NotificationSummariesPagingSource =
        repository.createPagingSource(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = null
            )
        )

    @AfterTest
    fun after() {
        server.close()
        repository.close()
    }

    @Test
    fun `getRefreshKey() returns null`() = runTest {
        assertNull(pagingSource.getRefreshKey(EMPTY_PAGING_STATE))
    }

    @Test
    fun `Loading first page when cache is empty and notification is older than 12 hours`() = runTest {
        loadingFirstPageWithoutCache(TEST_INSTANT_INSIDE_TEST_WEEK, notificationShouldBeUnread = false)
    }

    @Test
    fun `Loading first page when cache is empty and notification is newer than 12 hours`() = runTest {
        loadingFirstPageWithoutCache(instantOf(2022, 1, 19, 20, 0), notificationShouldBeUnread = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.loadingFirstPageWithoutCache(currentTime: Instant, notificationShouldBeUnread: Boolean) {
        clock.instant = currentTime

        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        server.respondWithSampleNotification()
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)
        result.data.validateSampleNotification(shouldBeUnread = notificationShouldBeUnread)

        advanceUntilIdle()

        loadingFirstPageFromCache(notificationShouldBeUnread = notificationShouldBeUnread)
    }

    @Test
    fun `Loading first page when current week was cached less than an hour ago`() = runTest {
        prepareInitialState(
            week = TEST_WEEK,
            loadTime = TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(59),
            emptyResponse = false
        )
        loadingFirstPageFromCache(notificationShouldBeUnread = false)
    }

    @Test
    fun `Loading first page when current week was cached more than an hour ago`() = runTest {
        prepareInitialState(
            week = TEST_WEEK,
            loadTime = TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(61),
            emptyResponse = false
        )
        loadingFirstPageFromCache(notificationShouldBeUnread = false)
    }

    private suspend fun loadingFirstPageFromCache(notificationShouldBeUnread: Boolean) {
        server.respondWithError()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(TEST_WEEK_NEAREST_PAST, result.nextKey)
        result.data.validateSampleNotification(shouldBeUnread = notificationShouldBeUnread)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.loadingNextPageAndUpdatingCache(emptyResponse: Boolean) {
        // From network
        if (emptyResponse) {
            server.respondWithEmptyBody()
        } else {
            server.respondWithSampleNotification()
        }
        checkLoadingNextPage(shouldBeEmpty = emptyResponse)
        advanceUntilIdle()
        // From cache
        server.respondWithError()
        checkLoadingNextPage(shouldBeEmpty = emptyResponse)
    }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached notifications are empty`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                TEST_INSTANT_INSIDE_TEST_WEEK,
                emptyResponse = true
            )
            server.respondWithError()
            checkLoadingNextPage(shouldBeEmpty = true)
        }

    @Test
    fun `Loading next page when its week was cached more than an hour ago - cached notifications are not empty`() =
        runTest {
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            prepareInitialState(
                TEST_WEEK,
                TEST_INSTANT_INSIDE_TEST_WEEK,
                emptyResponse = false
            )
            checkLoadingNextPage(shouldBeEmpty = false)
        }

    @Test
    fun `Loading next page when it was cached before its last day but less than an hour ago`() = runTest {
        clock.instant = TEST_WEEK.getInstantAfterLastDay()
        prepareInitialState(
            week = TEST_WEEK,
            loadTime = TEST_WEEK.getInstantAfterLastDay() - Duration.ofMinutes(59),
            emptyResponse = false
        )
        checkLoadingNextPage(shouldBeEmpty = false)
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
            result.data.validateSampleNotification(shouldBeUnread = false)
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
                NotificationsParsingTest::class.java.readTestResourceToBuffer("datasets/2016-01-01_2016-01-31.json")
            ).build()
        }
        pagingSource.invalidate()
        pagingSource = repository.createPagingSource(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    firstDayInstant = instantOf(2016, 1, 18, 0, 0),
                    instantAfterLastDay = instantOf(2016, 1, 25, 0, 0),
                )
            )
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
            MockResponse.Builder().body(
                NotificationsParsingTest::class.java.readTestResourceToBuffer("datasets/2016-01-01_2016-01-31.json")
            ).build()
        }
        pagingSource.invalidate()
        pagingSource = repository.createPagingSource(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    firstDayInstant = instantOf(2016, 1, 19, 0, 0),
                    instantAfterLastDay = instantOf(2016, 1, 22, 0, 0),
                )
            )
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
        emptyResponse: Boolean
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

        private fun List<CachedNotificationSummary>.validateSampleNotification(shouldBeUnread: Boolean) {
            assertEquals(1, size)
            val notification = first()
            assertEquals(SAMPLE_NOTIFICATION_ID, notification.id)
            assertEquals(!shouldBeUnread, notification.read)
        }
    }
}
