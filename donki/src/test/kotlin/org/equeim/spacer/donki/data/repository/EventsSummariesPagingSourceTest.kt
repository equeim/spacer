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
import org.equeim.spacer.donki.data.repository.EventsSummariesPagingSource.Week
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.test.*

private val CURRENT_INSTANT = LocalDate.of(2022, 1, 20).atTime(4, 2).toInstant(ZoneOffset.UTC)
private val EXPECTED_INITIAL_LOAD_TIMES: List<Pair<Instant, Instant>> = listOf(
    instantOf(2022, 1, 17) to instantOf(2022, 1, 20),
    instantOf(2022, 1, 10) to instantOf(2022, 1, 16),
    instantOf(2022, 1, 3) to instantOf(2022, 1, 9)
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class EventsSummariesPagingSourceTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
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
    fun `getRefreshKey() returns null`() = runTest {
        val state = PagingState<Week, EventSummary>(
            listOf(PagingSource.LoadResult.Page(emptyList(), null, null)),
            anchorPosition = null,
            config = mockk(),
            leadingPlaceholderCount = 0
        )
        assertNull(pagingSource.getRefreshKey(state))
    }

    @Test
    fun `Validate load() if requested week is null`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2021, 12, 27), result.nextKey)

        coVerifyAll {
            EventType.values.forEach { type ->
                EXPECTED_INITIAL_LOAD_TIMES.forEach { (startDate, endDate) ->
                    dataSource.getEvents(type, startDate, endDate)
                }
            }
        }
    }

    @Test
    fun `Validate load() if requested week is current week`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

        val requestedWeeks = weekOf(2022, 1, 17)

        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)

        val startDate = instantOf(2022, 1, 17)
        val endDate = instantOf(2022, 1, 20)
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested week is in the past`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

        val requestedWeeks = weekOf(2022, 1, 3)

        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2021, 12, 27), result.nextKey)

        val startDate = instantOf(2022, 1, 3)
        val endDate = instantOf(2022, 1, 9)
        coVerifyAll {
            EventType.values.forEach {
                dataSource.getEvents(it, startDate, endDate)
            }
        }
    }

    @Test
    fun `Validate load() if requested week is in the past and previous page (next week) is current week`() =
        runTest {
            coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()

            val requestedWeeks = weekOf(2022, 1, 10)

            val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
            val result = pagingSource.load(params).assertIsPage()
            assertNotNull(result.prevKey).validate()
            assertEquals(weekOf(2022, 1, 17), result.prevKey)
            assertNotNull(result.nextKey).validate()
            assertEquals(
                weekOf(2022, 1, 3), result.nextKey
            )

            val startDate = instantOf(2022, 1, 10)
            val endDate = instantOf(2022, 1, 16)
            coVerifyAll {
                EventType.values.forEach {
                    dataSource.getEvents(it, startDate, endDate)
                }
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
        coEvery { dataSource.getEvents(any(), any(), any()) } throws Exception("nope")
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        pagingSource.load(params).assertIsError()
        coVerify { dataSource.getEvents(any(), any(), any()) }
    }

    @Test
    fun `Validate that load() handles cancellation`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } coAnswers {
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
        coVerify { dataSource.getEvents(any(), any(), any()) }
    }

    @Test
    fun `Validate that successive loads of empty pages throttle`() = runTest {
        coEvery { dataSource.getEvents(any(), any(), any()) } returns emptyList()
        val requestedWeeks = weekOf(2022, 1, 10)
        val params = PagingSource.LoadParams.Append(requestedWeeks, 20, false)
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
    }
}

private fun weekOf(year: Int, month: Int, dayOfMonthAtStartOfWeek: Int): Week {
    return Week(LocalDate.of(year, month, dayOfMonthAtStartOfWeek))
}

private fun instantOf(year: Int, month: Int, dayOfMonth: Int): Instant {
    return LocalDate.of(year, month, dayOfMonth).atStartOfDay().toInstant(ZoneOffset.UTC)
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
