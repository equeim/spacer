// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalPagingApi::class)

package org.equeim.spacer.donki.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.RemoteMediator
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.anyWeek
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.cache.DonkiDataSourceCache
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.timeZoneParameters
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Clock
import java.time.ZoneId
import kotlin.test.*

@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
class EventsSummariesRemoteMediatorTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
    private val repository = mockk<DonkiRepositoryInternal>()
    private val cacheDataSource = mockk<DonkiDataSourceCache>()
    private val mediator = EventsSummariesRemoteMediator(
        repository = repository,
        cacheDataSource = cacheDataSource,
        filters = MutableStateFlow(DonkiRepository.EventFilters()),
        clock = clock,
    )
    private val actualRefreshedEvents = mutableListOf<Unit>()

    override fun after() {
        super.after()
        confirmVerified(repository, cacheDataSource)
    }

    private fun `Initial week does not require refresh`() {
        coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(EXPECTED_INITIAL_LOAD_WEEK, any(), any()) } returns false
    }

    @Test
    fun `Initial week does not require refresh ## validate initialize()`() =
        base {
            `Initial week does not require refresh`()
            val action = mediator.initialize()
            assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, action)
            `Verify calls to isWeekCachedAndNeedsRefresh()`(expectedRefreshIfRecentlyLoaded = false)
        }

    @Test
    fun `Initial week does not require refresh ## validate load() when refresh is initial`() =
        base {
            `Initial week does not require refresh`()
            mediator.initialize()
            clearMocks(cacheDataSource)
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(emptyList(), actualRefreshedEvents)
        }

    @Test
    fun `Initial week does not require refresh ## validate load() when refresh is not initial`() =
        base {
            `Initial week does not require refresh`()
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(emptyList(), actualRefreshedEvents)
            `Verify calls to isWeekCachedAndNeedsRefresh()`(expectedRefreshIfRecentlyLoaded = true)
        }

    private fun `Initial load week requires refresh`() {
        coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(EXPECTED_INITIAL_LOAD_WEEK, any(), any()) } returns true
        coEvery { repository.updateEventsForWeek(anyWeek(), any()) } returns emptyList()
    }

    @Test
    fun `Initial load week requires refresh ## validate initialize()`() =
        base {
            `Initial load week requires refresh`()
            val action = mediator.initialize()
            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, action)
            `Verify calls to isWeekCachedAndNeedsRefresh()`(expectedRefreshIfRecentlyLoaded = false)
            assertEquals(emptyList(), actualRefreshedEvents)
        }

    @Test
    fun `Initial load week requires refresh ## validate load() when refresh is initial`() =
        base {
            `Initial load week requires refresh`()
            mediator.initialize()
            clearMocks(cacheDataSource)
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(listOf(Unit), actualRefreshedEvents)
            for (type in EventType.entries) {
                coVerify { repository.updateEventsForWeek(EXPECTED_INITIAL_LOAD_WEEK, type) }
            }
        }

    @Test
    fun `Initial load week requires refresh ## validate load() when refresh is not initial`() =
        base {
            `Initial load week requires refresh`()
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Success>(result)
            assertFalse(result.endOfPaginationReached)
            assertEquals(listOf(Unit), actualRefreshedEvents)
            `Verify calls to isWeekCachedAndNeedsRefresh()`(expectedRefreshIfRecentlyLoaded = true)
            for (type in EventType.entries) {
                coVerify { repository.updateEventsForWeek(EXPECTED_INITIAL_LOAD_WEEK, type) }
            }
        }

    private fun `Verify calls to isWeekCachedAndNeedsRefresh()`(expectedRefreshIfRecentlyLoaded: Boolean) {
        for (type in EventType.entries) {
            coVerify {
                cacheDataSource.isWeekCachedAndNeedsRefresh(
                    EXPECTED_INITIAL_LOAD_WEEK,
                    type,
                    refreshIfRecentlyLoaded = expectedRefreshIfRecentlyLoaded
                )
            }
        }
    }

    @Test
    fun `load() returns Success when loadType is PREPEND`() = base {
        val result = mediator.load(LoadType.PREPEND, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertTrue(result.endOfPaginationReached)
        assertEquals(emptyList(), actualRefreshedEvents)
    }

    @Test
    fun `load() returns Success when loadType is APPEND`() = base {
        val result = mediator.load(LoadType.APPEND, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(emptyList(), actualRefreshedEvents)
    }

    @Test
    fun `Verify that initialize() handles isWeekCachedAndNeedsRefresh() errors`() =
        base {
            coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any(), any()) } throws RuntimeException("NOPE")
            val action = mediator.initialize()
            assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, action)
            coVerify(exactly = 1) {
                cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any(), refreshIfRecentlyLoaded = false)
            }
            assertEquals(emptyList(), actualRefreshedEvents)
        }

    @Test
    fun `Verify that load() handles isWeekCachedAndNeedsRefresh() errors`() =
        base {
            coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any(), any()) } throws RuntimeException("NOPE")
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Error>(result)
            coVerify(exactly = 1) {
                cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any(), refreshIfRecentlyLoaded = true)
            }
            assertEquals(emptyList(), actualRefreshedEvents)
        }

    @Test
    fun `Verify that load() handles updateEventsForWeek() errors`() =
        base {
            `Initial load week requires refresh`()
            coEvery { repository.updateEventsForWeek(anyWeek(), any()) } throws RuntimeException("NOPE")
            val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
            assertIs<RemoteMediator.MediatorResult.Error>(result)
            `Verify calls to isWeekCachedAndNeedsRefresh()`(expectedRefreshIfRecentlyLoaded = true)
            coVerify(exactly = 1) {
                repository.updateEventsForWeek(anyWeek(), any())
            }
            assertEquals(emptyList(), actualRefreshedEvents)
        }

    private fun base(block: suspend () -> Unit) =
        runTest {
            val refreshedEventsJob = launch { mediator.refreshed.toCollection(actualRefreshedEvents) }
            runCurrent()
            block()
            refreshedEventsJob.cancel()
        }

    companion object {
        @Parameterized.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()
    }
}
