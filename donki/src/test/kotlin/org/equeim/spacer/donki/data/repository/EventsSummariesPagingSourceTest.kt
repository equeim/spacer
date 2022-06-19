package org.equeim.spacer.donki.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.model.HighSpeedStreamSummary
import org.equeim.spacer.donki.data.network.DonkiDataSourceNetwork
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class EventsSummariesPagingSourceTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(LocalDate.of(2022, 1, 16).atTime(4, 2).toInstant(ZoneOffset.UTC), systemTimeZone)
    private val dataSource = mockk<DonkiDataSourceNetwork>()
    private lateinit var pagingSource: EventsSummariesPagingSource

    override fun before() {
        super.before()
        pagingSource = EventsSummariesPagingSource(dataSource, coroutineDispatchers, clock)
    }

    override fun after() {
        super.after()
        confirmVerified(dataSource)
    }

    @Test
    fun `getRefreshKey() returns null if anchorPosition is null`() = runTest {
        val state = PagingState<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>(
            listOf(PagingSource.LoadResult.Page(listOf(mockk()), null, null)),
            anchorPosition = null,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        assertNull(pagingSource.getRefreshKey(state))
    }

    @Test
    fun `getRefreshKey() returns null if pages are empty`() = runTest {
        val state = PagingState<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>(
            listOf(
                PagingSource.LoadResult.Page(emptyList(), null, null),
                PagingSource.LoadResult.Page(emptyList(), null, null)
            ),
            anchorPosition = 42,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        assertNull(pagingSource.getRefreshKey(state))
    }

    @Test
    fun `Validate getRefreshKey() if range surrounding anchorPosition is in the past`() = runTest {
        val state = PagingState<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>(
            listOf(
                PagingSource.LoadResult.Page(
                    listOf(
                        HighSpeedStreamSummary(
                            EventId(""),
                            LocalDate.of(2021, 1, 15).atTime(6, 6).toInstant(ZoneOffset.UTC)
                        )
                    ), null, null
                )
            ),
            anchorPosition = 0,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        val range = assertNotNull(pagingSource.getRefreshKey(state))
        range.validate(allowSmallerRange = false)
        val expectedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2021, 1, 12).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2021, 1, 18).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        )
        assertEquals(expectedRange, range)
    }

    @Test
    fun `Validate getRefreshKey() if range surrounding anchorPosition is partially in the future`() = runTest {
        val state = PagingState<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>(
            listOf(
                PagingSource.LoadResult.Page(
                    listOf(
                        HighSpeedStreamSummary(
                            EventId(""),
                            LocalDate.of(2022, 1, 14).atTime(6, 6).toInstant(ZoneOffset.UTC)
                        )
                    ), null, null
                )
            ),
            anchorPosition = 0,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        val range = assertNotNull(pagingSource.getRefreshKey(state))
        range.validate(allowSmallerRange = false)
        val expectedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2022, 1, 10).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )
        assertEquals(expectedRange, range)
    }

    @Test
    fun `Validate getRefreshKey() if range surrounding anchorPosition is the same as last week range`() = runTest {
        val state = PagingState<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>(
            listOf(
                PagingSource.LoadResult.Page(
                    listOf(
                        HighSpeedStreamSummary(
                            EventId(""),
                            LocalDate.of(2022, 1, 13).atTime(6, 6).toInstant(ZoneOffset.UTC)
                        )
                    ), null, null
                )
            ),
            anchorPosition = 0,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        val range = assertNotNull(pagingSource.getRefreshKey(state))
        range.validate(allowSmallerRange = false)
        val expectedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2022, 1, 10).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )
        assertEquals(expectedRange, range)
    }

    @Test
    fun `Validate getRefreshKey() if anchor item day is current day`() = runTest {
        val state = PagingState<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>(
            listOf(
                PagingSource.LoadResult.Page(
                    listOf(
                        HighSpeedStreamSummary(
                            EventId(""),
                            LocalDate.now(clock.withZone(ZoneOffset.UTC)).atTime(6, 6).toInstant(ZoneOffset.UTC)
                        )
                    ), null, null
                )
            ),
            anchorPosition = 0,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        val range = assertNotNull(pagingSource.getRefreshKey(state))
        range.validate(allowSmallerRange = false)
        val expectedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2022, 1, 10).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )
        assertEquals(expectedRange, range)
    }

    @Test
    fun `Validate load() if requested range is null`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()
        val params = PagingSource.LoadParams.Refresh<EventsSummariesPagingSource.EventsSummariesDateRange>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate(allowSmallerRange = false)

        val startDate = LocalDate.of(2022, 1, 10).atStartOfDay().toInstant(ZoneOffset.UTC)
        val endDate = LocalDate.of(2022, 1, 16).atStartOfDay().toInstant(ZoneOffset.UTC)
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested range's endDate is current day and was at creation time`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 1, 14).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )

        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate(allowSmallerRange = false)

        val startDate = requestedRange.startDate.toInstant()
        val endDate = requestedRange.endDate.toInstant()
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested range's endDate is current day but it wasn't at creation time`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 1, 14).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        )

        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate(allowSmallerRange = false)

        val startDate = requestedRange.startDate.toInstant()
        val endDate = requestedRange.endDate.toInstant()
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested range is in the past`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2021, 1, 9).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2021, 1, 15).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        )

        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate(allowSmallerRange = true)
        assertEquals(EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2021, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2021, 1, 22).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        ), result.prevKey)
        assertNotNull(result.nextKey).validate(allowSmallerRange = false)
        assertEquals(EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2021, 1, 2).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2021, 1, 8).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        ), result.nextKey)

        val startDate = requestedRange.startDate.toInstant()
        val endDate = requestedRange.endDate.toInstant()
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested range is in the past and previous page contains current day`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 1, 9).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 1, 15).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        )

        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate(allowSmallerRange = true)
        assertEquals(EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        ), result.prevKey)
        assertNotNull(result.nextKey).validate(allowSmallerRange = false)
        assertEquals(EventsSummariesPagingSource.EventsSummariesDateRange(
            LocalDate.of(2022, 1, 2).atStartOfDay().atOffset(ZoneOffset.UTC),
            LocalDate.of(2022, 1, 8).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        ), result.nextKey)

        val startDate = requestedRange.startDate.toInstant()
        val endDate = requestedRange.endDate.toInstant()
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested range is in the future`() = runTest {
        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 2, 9).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 2, 15).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = false
        )
        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        pagingSource.load(params).assertIsInvalid()
    }

    @Test
    fun `Validate that load() handles exceptions`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } throws Exception("nope")
        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 1, 14).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )
        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        pagingSource.load(params).assertIsError()
        coVerify { dataSource.getEvents(any(), any(), any()) }
    }

    @Test
    fun `Validate that load() handles cancellation`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } coAnswers {
            delay(1000)
            emptyList()
        }
        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 1, 14).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )
        val params = PagingSource.LoadParams.Refresh(requestedRange, 20, false)
        val job = launch {
            assertFailsWith<CancellationException> {
                pagingSource.load(params)
            }
        }
        runCurrent()
        job.cancel()
        coVerify { dataSource.getEvents(any(), any(), any()) }
    }

    @Test
    fun `Validate that successive loads of empty pages throttle`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()
        val requestedRange = EventsSummariesPagingSource.EventsSummariesDateRange(
            startDate = LocalDate.of(2022, 1, 14).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDate = LocalDate.of(2022, 1, 16).atStartOfDay().atOffset(ZoneOffset.UTC),
            endDateWasCurrentDayAtCreationTime = true
        )
        val params = PagingSource.LoadParams.Append(requestedRange, 20, false)
        pagingSource.load(params)
        val time = currentTime
        pagingSource.load(params)
        assertNotEquals(time, currentTime)
        coVerify { dataSource.getEvents(any(), any(), any()) }
    }

    companion object {
        private val timeZones = setOf(
            ZoneId.ofOffset("UTC", ZoneOffset.UTC),
            ZoneId.systemDefault(),
            ZoneId.of("Asia/Yakutsk"),
            ZoneId.of("America/Los_Angeles")
        )

        @Parameterized.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): Iterable<ZoneId> = timeZones

        private fun EventsSummariesPagingSource.EventsSummariesDateRange.validate(allowSmallerRange: Boolean) {
            val duration = Duration.between(startDate, endDate)
            if (!allowSmallerRange) {
                assertEquals(Duration.ofDays(EventsSummariesPagingSource.PAGE_SIZE_IN_DAYS), duration)
            } else {
                assertEquals(duration.truncatedTo(ChronoUnit.DAYS), duration)
                assertTrue(duration.toDays() in 0..EventsSummariesPagingSource.PAGE_SIZE_IN_DAYS, "Duration of ${duration.toDays()} days is not in valid range")
            }
            assertEquals(startDate.truncatedTo(ChronoUnit.DAYS), startDate)
            assertEquals(endDate.truncatedTo(ChronoUnit.DAYS), endDate)
            assertEquals(ZoneOffset.UTC, startDate.offset)
            assertEquals(ZoneOffset.UTC, endDate.offset)
        }

        private fun PagingSource.LoadResult<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>.assertIsPage() =
            assertIs<PagingSource.LoadResult.Page<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>>(this)

        private fun PagingSource.LoadResult<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>.assertIsInvalid() =
            assertIs<PagingSource.LoadResult.Invalid<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>>(this)

        private fun PagingSource.LoadResult<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>.assertIsError() =
            assertIs<PagingSource.LoadResult.Error<EventsSummariesPagingSource.EventsSummariesDateRange, EventSummary>>(this)
    }
}
