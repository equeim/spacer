// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.anyWeek
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import org.equeim.spacer.donki.timeZoneParameters
import org.equeim.spacer.donki.weekOf
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val CURRENT_INSTANT: Instant = LocalDate.of(2022, 1, 20).atTime(4, 2).toInstant(ZoneOffset.UTC)
internal val EXPECTED_INITIAL_LOAD_WEEK: Week = Week(LocalDate.of(2022, 1, 17))
internal val EMPTY_PAGING_STATE = PagingState<Week, EventSummary>(
    listOf(PagingSource.LoadResult.Page(emptyList(), null, null)),
    anchorPosition = null,
    config = mockk(),
    leadingPlaceholderCount = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class EventsSummariesPagingSourceTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
    private val repository = mockk<DonkiEventsRepository>()
    private lateinit var pagingSource: EventsSummariesPagingSource

    override fun before() {
        super.before()
        pagingSource = EventsSummariesPagingSource(
            repository,
            emptyFlow<Any>(),
            DonkiEventsRepository.Filters(types = EventType.entries, dateRange = null),
            coroutineDispatchers,
            clock
        )
    }

    override fun after() {
        confirmVerified(repository)
        super.after()
    }

    @Test
    fun `getRefreshKey() returns null`() = runTest {
        assertNull(pagingSource.getRefreshKey(EMPTY_PAGING_STATE))
    }

    @Test
    fun `Validate load() if requested week is null`() = runTest {
        mockEmptySummaries()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)

        coVerifyAll {
            repository.getEventSummariesForWeek(
                week = EXPECTED_INITIAL_LOAD_WEEK,
                eventTypes = EventType.entries,
                dateRange = null,
                refreshCacheIfNeeded = false
            )
        }
    }

    @Test
    fun `Validate that load() does not refresh cache when refreshing`() = runTest {
        mockEmptySummaries()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        pagingSource.load(params)
        coVerifyAll {
            repository.getEventSummariesForWeek(
                week = EXPECTED_INITIAL_LOAD_WEEK,
                eventTypes = EventType.entries,
                dateRange = null,
                refreshCacheIfNeeded = false
            )
        }
    }

    @Test
    fun `Validate load() if requested week is current week`() = runTest {
        mockEmptySummaries()

        val requestedWeeks = weekOf(2022, 1, 17)

        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)

        coVerify {
            repository.getEventSummariesForWeek(
                week = Week(LocalDate.of(2022, 1, 17)),
                eventTypes = EventType.entries,
                dateRange = null,
                refreshCacheIfNeeded = false
            )
        }
    }

    @Test
    fun `Validate load() if requested week is in the past`() = runTest {
        mockEmptySummaries()

        val requestedWeeks = weekOf(2022, 1, 3)

        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2021, 12, 27), result.nextKey)
        coVerify {
            repository.getEventSummariesForWeek(
                week = Week(LocalDate.of(2022, 1, 3)),
                eventTypes = EventType.entries,
                dateRange = null,
                refreshCacheIfNeeded = false
            )
        }
    }

    @Test
    fun `Validate that load() refreshes cache when appending`() = runTest {
        mockEmptySummaries()
        val params = PagingSource.LoadParams.Append(weekOf(2022, 1, 3), 20, false)
        pagingSource.load(params)
        coVerify {
            repository.getEventSummariesForWeek(
                week = Week(LocalDate.of(2022, 1, 3)),
                eventTypes = EventType.entries,
                dateRange = null,
                refreshCacheIfNeeded = true
            )
        }
    }

    @Test
    fun `Validate that load() refreshes cache cache when prepending`() = runTest {
        mockEmptySummaries()
        val params = PagingSource.LoadParams.Prepend(weekOf(2022, 1, 3), 20, false)
        pagingSource.load(params)
        coVerify {
            repository.getEventSummariesForWeek(
                week = Week(LocalDate.of(2022, 1, 3)),
                eventTypes = EventType.entries,
                dateRange = null,
                refreshCacheIfNeeded = true
            )
        }
    }

    @Test
    fun `Validate load() if requested week is in the past and previous page (next week) is current week`() =
        runTest {
            mockEmptySummaries()

            val requestedWeeks = weekOf(2022, 1, 10)

            val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
            val result = pagingSource.load(params).assertIsPage()
            assertNotNull(result.prevKey).validate()
            assertEquals(weekOf(2022, 1, 17), result.prevKey)
            assertNotNull(result.nextKey).validate()
            assertEquals(
                weekOf(2022, 1, 3), result.nextKey
            )
            coVerify {
                repository.getEventSummariesForWeek(
                    week = Week(LocalDate.of(2022, 1, 10)),
                    eventTypes = EventType.entries,
                    dateRange = null,
                    refreshCacheIfNeeded = false
                )
            }
        }

    @Test
    fun `Validate load() if requested week is in the future`() = runTest {
        val requestedWeeks = weekOf(2022, 1, 24)
        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        pagingSource.load(params).assertIsInvalid()
    }

    @Test
    fun `Validate that load() handles exceptions`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                week = anyWeek(),
                eventTypes = any(),
                dateRange = any(),
                refreshCacheIfNeeded = any()
            )
        } throws Exception("nope")
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        pagingSource.load(params).assertIsError()
        coVerify {
            repository.getEventSummariesForWeek(
                week = anyWeek(),
                eventTypes = any(),
                dateRange = any(),
                refreshCacheIfNeeded = any()
            )
        }
    }

    @Test
    fun `Validate that load() handles cancellation`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                week = anyWeek(),
                eventTypes = any(),
                dateRange = any(),
                refreshCacheIfNeeded = any()
            )
        } coAnswers {
            delay(1000)
            emptyList()
        }
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val job = launch {
            assertFailsWith<CancellationException> {
                pagingSource.load(params)
            }
        }
        runCurrent()
        job.cancel()
        coVerify {
            repository.getEventSummariesForWeek(
                week = anyWeek(),
                eventTypes = any(),
                dateRange = any(),
                refreshCacheIfNeeded = any()
            )
        }
    }

    @Test
    fun `Validate that successive loads of empty pages throttle`() = runTest {
        mockEmptySummaries()
        val requestedWeeks = weekOf(2022, 1, 10)
        val params = PagingSource.LoadParams.Append(requestedWeeks, 20, false)
        pagingSource.load(params)
        val time = currentTime
        pagingSource.load(params)
        assertNotEquals(time, currentTime)
        coVerify {
            repository.getEventSummariesForWeek(
                week = anyWeek(),
                eventTypes = any(),
                dateRange = any(),
                refreshCacheIfNeeded = any()
            )
        }
    }

    private fun mockEmptySummaries() {
        coEvery {
            repository.getEventSummariesForWeek(
                week = anyWeek(),
                eventTypes = any(),
                dateRange = any(),
                refreshCacheIfNeeded = any()
            )
        } returns emptyList()
    }

    companion object {
        @Parameterized.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()
    }
}

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
