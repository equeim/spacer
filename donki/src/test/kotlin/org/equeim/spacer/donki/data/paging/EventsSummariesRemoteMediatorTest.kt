@file:OptIn(ExperimentalPagingApi::class)

package org.equeim.spacer.donki.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.RemoteMediator
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.anyWeek
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.cache.DonkiDataSourceCache
import org.equeim.spacer.donki.timeZoneParameters
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Clock
import java.time.ZoneId
import kotlin.test.*

@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class EventsSummariesRemoteMediatorTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
    private val repository = mockk<DonkiRepositoryInternal>()
    private val cacheDataSource = mockk<DonkiDataSourceCache>()
    private val mediator = EventsSummariesRemoteMediator(repository, cacheDataSource, clock)
    private val refreshedEvents = mutableListOf<Unit>()

    override fun after() {
        super.after()
        confirmVerified(repository, cacheDataSource)
    }

    @Test
    fun `initialize() returns SKIP_INITIAL_REFRESH when none of initial load weeks require refresh`() =
        runTest {
            EXPECTED_INITIAL_LOAD_WEEKS.forEach {
                coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(it, any()) } returns false
            }
            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )
            EXPECTED_INITIAL_LOAD_WEEKS.forEach {
                coVerify { cacheDataSource.isWeekCachedAndNeedsRefresh(it, any()) }
            }
        }

    @Test
    fun `initialize() returns LAUNCH_INITIAL_REFRESH when some of initial load weeks require refresh`() =
        runTest {
            coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(EXPECTED_INITIAL_LOAD_WEEKS.first(), any()) } returns true
            EXPECTED_INITIAL_LOAD_WEEKS.drop(1).forEach {
                coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(it, any()) } returns false
            }
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )
            coVerify { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any()) }
        }

    @Test
    fun `initialize() returns LAUNCH_INITIAL_REFRESH when all of initial load weeks require refresh`() =
        runTest {
            EXPECTED_INITIAL_LOAD_WEEKS.forEach {
                coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(it, any()) } returns true
            }
            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )
            coVerify { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any()) }
        }

    @Test
    fun `load() returns Success when loadType is PREPEND`() = validateLoad {
        val result = mediator.load(LoadType.PREPEND, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertTrue(result.endOfPaginationReached)
        assertEquals(emptyList(), refreshedEvents)
    }

    @Test
    fun `load() returns Success when loadType is APPEND`() = validateLoad {
        val result = mediator.load(LoadType.APPEND, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(emptyList(), refreshedEvents)
    }

    @Test
    fun `Validate load() when none of initial load weeks require refresh`() = validateLoad {
        coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any()) } returns false
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(emptyList(), refreshedEvents)
        coVerify { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any()) }
    }

    @Test
    fun `Validate load() when some of initial load weeks require refresh`() = validateLoad {
        coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(EXPECTED_INITIAL_LOAD_WEEKS.first(), any()) } returns true
        EXPECTED_INITIAL_LOAD_WEEKS.drop(1).forEach {
            coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(it, any()) } returns false
        }
        coEvery { repository.updateEventsForWeek(EXPECTED_INITIAL_LOAD_WEEKS.first(), any()) } just runs
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(listOf(Unit), refreshedEvents)
        coVerify { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any()) }
        coVerify { repository.updateEventsForWeek(EXPECTED_INITIAL_LOAD_WEEKS.first(), any()) }
    }

    @Test
    fun `Validate load() when all of initial load weeks require refresh`() = validateLoad {
        EXPECTED_INITIAL_LOAD_WEEKS.forEach {
            coEvery { cacheDataSource.isWeekCachedAndNeedsRefresh(it, any()) } returns true
            coEvery { repository.updateEventsForWeek(it, any()) } just runs
        }
        val result = mediator.load(LoadType.REFRESH, EMPTY_PAGING_STATE)
        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertFalse(result.endOfPaginationReached)
        assertEquals(listOf(Unit), refreshedEvents)
        coVerify { cacheDataSource.isWeekCachedAndNeedsRefresh(anyWeek(), any()) }
        EXPECTED_INITIAL_LOAD_WEEKS.forEach {
            coVerify { repository.updateEventsForWeek(it, any()) }
        }
    }

    private fun validateLoad(block: suspend () -> Unit) = runTest {
        val refreshedEventsJob = launch { mediator.refreshed.toCollection(refreshedEvents) }
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
