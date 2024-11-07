// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalPagingApi::class)

package org.equeim.spacer.donki.data.events

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.equeim.spacer.donki.CoroutinesRule
import org.equeim.spacer.donki.FakeClock
import org.equeim.spacer.donki.TEST_INSTANT_INSIDE_TEST_WEEK
import org.equeim.spacer.donki.TEST_WEEK
import org.equeim.spacer.donki.allExceptions
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.HttpErrorResponse
import org.equeim.spacer.donki.data.common.InvalidApiKeyError
import org.equeim.spacer.donki.data.common.TooManyRequestsError
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.cache.EventsCacheDatabase
import org.equeim.spacer.donki.timeZoneParameters
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ConnectException
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
class EventsSummariesRemoteMediatorTest(systemTimeZone: ZoneId) {
    @get:Rule
    val coroutinesRule = CoroutinesRule()

    private val clock = FakeClock(TEST_INSTANT_INSIDE_TEST_WEEK, systemTimeZone)
    private val server = MockWebServer().apply {
        dispatcher = MockWebServerDispatcher()
        start()
    }
    private val db: EventsCacheDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        EventsCacheDatabase::class.java
    ).setQueryExecutor(coroutinesRule.testExecutor).setTransactionExecutor(coroutinesRule.testExecutor).allowMainThreadQueries().build()
    private val repository = DonkiEventsRepository(
        customNasaApiKey = flowOf(null),
        context = ApplicationProvider.getApplicationContext(),
        baseUrl = server.url("/"),
        db = db,
        coroutineDispatchers = coroutinesRule.coroutineDispatchers,
        clock = clock
    )
    private val filters =
        MutableStateFlow(DonkiEventsRepository.Filters(types = EventType.entries, dateRange = null))
    private val mediator: EventsSummariesRemoteMediator = repository.createRemoteMediator(filters)

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
        server.shutdown()
        repository.close()
    }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when last week is not cached`() = runTest {
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator.initialize())
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
    fun `initialize returns SKIP_INITIAL_REFRESH when with date range filter and its last week was loaded a week after its last day`() =
        runTest {
            filters.value = DonkiEventsRepository.Filters(
                types = EventType.entries,
                dateRange = DateRange(
                    TEST_WEEK.getFirstDayInstant(),
                    TEST_WEEK.getInstantAfterLastDay()
                )
            )
            prepareInitialState(
                week = TEST_WEEK,
                loadTime = TEST_INSTANT_WEEK_AFTER_TEST_WEEK
            )
            clock.instant = TEST_INSTANT_WEEK_AFTER_TEST_WEEK + Duration.ofDays(1)
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
        }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when with date range filter and its last week was loaded less than a week before it's last day but less than an hour ago`() =
        runTest {
            filters.value = DonkiEventsRepository.Filters(
                types = EventType.entries,
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
            // Initial state
            prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))

            // Checking initialize
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )

            // Checking load after LAUNCH_INITIAL_REFRESH
            server.respondWithSampleEvents()
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(1, actualRefreshedEvents)
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
        }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when last week was cached more than an hour ago for one event type`() =
        runTest {
            server.respondWithSampleEvents()

            // Initial state
            coroutineScope {
                for (eventType in EventType.entries.dropLast(1)) {
                    launch { repository.updateEventsForWeek(TEST_WEEK, eventType) }
                }
            }
            clock.instant =
                TEST_INSTANT_INSIDE_TEST_WEEK - Duration.ofHours(1) - Duration.ofSeconds(1)
            repository.updateEventsForWeek(TEST_WEEK, EventType.entries.last())
            clock.instant = TEST_INSTANT_INSIDE_TEST_WEEK

            // Checking initialize
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )

            // Checking load after LAUNCH_INITIAL_REFRESH
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(1, actualRefreshedEvents)
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
        }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when with date range filter and its last week was loaded before its last day but more than an hour ago`() =
        runTest {
            // Initial state
            filters.value = DonkiEventsRepository.Filters(
                types = EventType.entries,
                dateRange = DateRange(
                    TEST_WEEK.getFirstDayInstant(),
                    TEST_WEEK.getInstantAfterLastDay()
                )
            )
            prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))

            // Checking initialize
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )

            // Checking load after LAUNCH_INITIAL_REFRESH
            server.respondWithSampleEvents()
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(1, actualRefreshedEvents)
        }

    @Test
    fun `Manual refresh when last week was loaded less than an hour ago`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(59))
        checkManualRefresh()
    }

    @Test
    fun `Manual refresh when last week was loaded more than an hour ago`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        checkManualRefresh()
    }

    private suspend fun checkManualRefresh() {
        server.respondWithSampleEvents()
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(1, actualRefreshedEvents)
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator.initialize())
    }

    private suspend fun prepareInitialState(week: Week, loadTime: Instant) {
        val saved = clock.instant
        clock.instant = loadTime
        server.respondWithSampleEvents()
        coroutineScope {
            for (eventType in EventType.entries) {
                launch { repository.updateEventsForWeek(week, eventType) }
            }
        }
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
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertEquals(0, actualRefreshedEvents)
    }

    @Test
    fun `Verify 403 error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse().setResponseCode(403) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertIs<InvalidApiKeyError>(result.throwable)
    }

    @Test
    fun `Verify 429 error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse().setResponseCode(429) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertIs<TooManyRequestsError>(result.throwable)
    }

    @Test
    fun `Verify 500 error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse().setResponseCode(500) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertIs<HttpErrorResponse>(result.throwable)
    }

    @Test
    fun `Verify network error handling`() = runTest {
        prepareInitialState(TEST_WEEK, clock.instant - Duration.ofMinutes(61))
        server.respond = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertTrue(result.throwable.allExceptions().filterIsInstance<ConnectException>().any())
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()

        private val TEST_INSTANT_WEEK_AFTER_TEST_WEEK: Instant =
            TEST_WEEK.getInstantAfterLastDay() + Duration.ofDays(7)
    }
}
