// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalPagingApi::class)

package org.equeim.spacer.donki.data.notifications

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.equeim.spacer.donki.CoroutinesRule
import org.equeim.spacer.donki.FakeClock
import org.equeim.spacer.donki.TEST_INSTANT_INSIDE_TEST_WEEK
import org.equeim.spacer.donki.TEST_WEEK
import org.equeim.spacer.donki.allExceptions
import org.equeim.spacer.donki.createInMemoryTestDatabase
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.createDonkiOkHttpClient
import org.equeim.spacer.donki.data.notifications.cache.CachedNotificationSummary
import org.equeim.spacer.donki.data.notifications.cache.NotificationsDatabase
import org.equeim.spacer.donki.getTestResourceInputStream
import org.equeim.spacer.donki.timeZoneParameters
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalPagingApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NotificationSummariesRemoteMediatorTest(systemTimeZone: ZoneId) {
    @get:Rule
    val coroutinesRule = CoroutinesRule()

    private val clock = FakeClock(TEST_INSTANT_INSIDE_TEST_WEEK, systemTimeZone)
    private val server = MockWebServer().apply {
        dispatcher = MockWebServerDispatcher()
        start()
    }
    private val db: NotificationsDatabase =
        createInMemoryTestDatabase(coroutinesRule.coroutineDispatchers)
    private val repository = DonkiNotificationsRepository(
        customNasaApiKey = flowOf(null),
        okHttpClient = createDonkiOkHttpClient(),
        baseUrl = server.url("/"),
        context = ApplicationProvider.getApplicationContext(),
        db = db,
        coroutineDispatchers = coroutinesRule.coroutineDispatchers,
        clock = clock
    )
    private val filters =
        MutableStateFlow(
            DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = null
            )
        )
    private val mediator: NotificationsRemoteMediator = repository.createRemoteMediator(filters)
    private val pagingSource: NotificationSummariesPagingSource = repository.createPagingSource(
        DonkiNotificationsRepository.Filters(NotificationType.entries, null)
    )

    private lateinit var actualRefreshedEventsScope: CoroutineScope
    private var actualRefreshedEvents = 0

    @BeforeTest
    fun before() {
        actualRefreshedEventsScope = CoroutineScope(coroutinesRule.coroutineDispatchers.Default)
        actualRefreshedEventsScope.launch { mediator.refreshed.collect { actualRefreshedEvents++ } }
    }

    @AfterTest
    fun after() {
        actualRefreshedEventsScope.cancel()
        server.close()
        repository.close()
        coroutinesRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when last week is not cached`() = runTest {
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())
    }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when last week was cached less than an hour ago`() =
        runTest {
            prepareInitialState(
                week = TEST_WEEK,
                loadTime = TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofMinutes(59)
            )
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
        }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when with date range filter and its last week was loaded after its last day`() =
        runTest {
            filters.value = DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    TEST_WEEK.getFirstDayInstant(),
                    TEST_WEEK.getInstantAfterLastDay()
                )
            )
            prepareInitialState(
                week = TEST_WEEK,
                loadTime = TEST_WEEK.getInstantAfterLastDay()
            )
            clock.instant = TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(1)
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
        }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when with date range filter and its last week was loaded less than a week before it's last day but less than an hour ago`() =
        runTest {
            filters.value = DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    TEST_WEEK.getFirstDayInstant() - Duration.ofDays(1),
                    TEST_WEEK.getInstantAfterLastDay()
                )
            )
            clock.instant = TEST_WEEK.getInstantAfterLastDay() - Duration.ofDays(1)
            prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(59))
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
        }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when last week was cached more than an hour ago`() =
        runTest {
            prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )
            checkRefresh()
        }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when with date range filter and its last week was loaded before its last day but more than an hour ago`() =
        runTest {
            filters.value = DonkiNotificationsRepository.Filters(
                types = NotificationType.entries,
                dateRange = DateRange(
                    TEST_WEEK.getFirstDayInstant(),
                    TEST_WEEK.getInstantAfterLastDay()
                )
            )
            prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )
            checkRefresh()
        }

    @Test
    fun `Manual refresh when last week was loaded less than an hour ago`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(59))
        checkRefresh()
    }

    @Test
    fun `Manual refresh when last week was loaded more than an hour ago`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        checkRefresh()
    }

    private suspend fun checkRefresh() {
        server.respond = {
            MockResponse.Builder().body(
                NotificationsParsingTest::class.java.getTestResourceInputStream("new_notifications.json")
                    .use { it.reader().readText() }
            ).build()
        }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(1, actualRefreshedEvents)
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator.initialize())

        server.respondWithError()
        val pagingSourceResult = pagingSource.load(PagingSource.LoadParams.Refresh(null, 20, false))
        assertIs<PagingSource.LoadResult.Page<Week, CachedNotificationSummary>>(pagingSourceResult)
        assertEquals(
            listOf("20241002-AL-003" to false, "20241002-AL-002" to true),
            pagingSourceResult.data.map { it.id.stringValue to it.read }
        )
    }

    private suspend fun prepareInitialState(week: Week, loadTime: Instant) {
        val saved = clock.instant
        clock.instant = loadTime
        server.respondWithSampleNotification()
        repository.updateNotificationsForWeek(week)
        server.respondWithError()
        clock.instant = saved
    }

    @Test
    fun `load() returns Success when loadType is PREPEND`() = runTest {
        val result = mediator.load(LoadType.PREPEND, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertTrue(result.endOfPaginationReached)
        assertEquals(0, actualRefreshedEvents)
    }

    @Test
    fun `load() returns Success when loadType is APPEND`() = runTest {
        val result = mediator.load(LoadType.APPEND, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(0, actualRefreshedEvents)
    }

    @Test
    fun `Verify that initialize() handles SQLite errors`() = runTest {
        db.openHelper.writableDatabase.execSQL("DROP TABLE cached_weeks")
        val action = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, action)
    }

    @Test
    fun `Verify that load() handles SQLite errors`() = runTest {
        db.openHelper.writableDatabase.execSQL("DROP TABLE cached_weeks")
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        println(result)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertEquals(0, actualRefreshedEvents)
    }

    @Test
    fun `Verify 403 error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse(code = 403) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertIs<DonkiNetworkDataSourceException.InvalidApiKey>(result.throwable)
    }

    @Test
    fun `Verify 429 error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse(code = 429) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertIs<DonkiNetworkDataSourceException.TooManyRequests>(result.throwable)
    }

    @Test
    fun `Verify 500 error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse(code = 500) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertIs<DonkiNetworkDataSourceException.HttpErrorResponse>(result.throwable)
    }

    @Test
    fun `Verify network error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build() }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertTrue(result.throwable.allExceptions().filterIsInstance<IOException>().any())
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()
    }
}
