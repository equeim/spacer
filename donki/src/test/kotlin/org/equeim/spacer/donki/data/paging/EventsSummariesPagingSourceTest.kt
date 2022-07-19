package org.equeim.spacer.donki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.mockk.*
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
import org.equeim.spacer.donki.data.DonkiRepositoryInternal
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.timeZoneParameters
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.test.*

val CURRENT_INSTANT: Instant = LocalDate.of(2022, 1, 20).atTime(4, 2).toInstant(ZoneOffset.UTC)
internal val EXPECTED_INITIAL_LOAD_WEEKS: List<Week> = listOf(
    Week(LocalDate.of(2022, 1, 17)),
    Week(LocalDate.of(2022, 1, 10)),
    Week(LocalDate.of(2022, 1, 3))
)
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
    private val repository = mockk<DonkiRepositoryInternal>()
    private lateinit var pagingSource: EventsSummariesPagingSource

    override fun before() {
        super.before()
        pagingSource = EventsSummariesPagingSource(
            repository,
            emptyFlow(),
            coroutineDispatchers,
            clock
        )
    }

    override fun after() {
        super.after()
        confirmVerified(repository)
    }

    @Test
    fun `getRefreshKey() returns null`() = runTest {
        assertNull(pagingSource.getRefreshKey(EMPTY_PAGING_STATE))
    }

    @Test
    fun `Validate load() if requested week is null`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                any()
            )
        } returns emptyList()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2021, 12, 27), result.nextKey)

        coVerifyAll {
            EXPECTED_INITIAL_LOAD_WEEKS.forEach { week ->
                repository.getEventSummariesForWeek(week, EventType.All, any())
            }
        }
    }

    @Test
    fun `Validate that load() does not refresh cache when refreshing`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                refreshCacheIfNeeded = false
            )
        } returns emptyList()
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        pagingSource.load(params)
        coVerifyAll {
            EXPECTED_INITIAL_LOAD_WEEKS.forEach { week ->
                repository.getEventSummariesForWeek(week, EventType.All, refreshCacheIfNeeded = false)
            }
        }
    }

    @Test
    fun `Validate load() if requested week is current week`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                any()
            )
        } returns emptyList()

        val requestedWeeks = weekOf(2022, 1, 17)

        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNull(result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.nextKey)

        coVerify {
            repository.getEventSummariesForWeek(
                Week(LocalDate.of(2022, 1, 17)),
                EventType.All,
                any()
            )
        }
    }

    @Test
    fun `Validate load() if requested week is in the past`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                any()
            )
        } returns emptyList()

        val requestedWeeks = weekOf(2022, 1, 3)

        val params = PagingSource.LoadParams.Refresh(requestedWeeks, 20, false)
        val result = pagingSource.load(params).assertIsPage()
        assertNotNull(result.prevKey).validate()
        assertEquals(weekOf(2022, 1, 10), result.prevKey)
        assertNotNull(result.nextKey).validate()
        assertEquals(weekOf(2021, 12, 27), result.nextKey)
        coVerify {
            repository.getEventSummariesForWeek(
                Week(LocalDate.of(2022, 1, 3)),
                EventType.All,
                any()
            )
        }
    }

    @Test
    fun `Validate that load() refreshes cache when appending`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                refreshCacheIfNeeded = true
            )
        } returns emptyList()
        val params = PagingSource.LoadParams.Append(weekOf(2022, 1, 3), 20, false)
        pagingSource.load(params)
        coVerify {
            repository.getEventSummariesForWeek(
                Week(LocalDate.of(2022, 1, 3)),
                EventType.All,
                refreshCacheIfNeeded = true
            )
        }
    }

    @Test
    fun `Validate that load() refreshes cache cache when prepending`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                refreshCacheIfNeeded = true
            )
        } returns emptyList()
        val params = PagingSource.LoadParams.Prepend(weekOf(2022, 1, 3), 20, false)
        pagingSource.load(params)
        coVerify {
            repository.getEventSummariesForWeek(
                Week(LocalDate.of(2022, 1, 3)),
                EventType.All,
                refreshCacheIfNeeded = true
            )
        }
    }

    @Test
    fun `Validate load() if requested week is in the past and previous page (next week) is current week`() =
        runTest {
            coEvery {
                repository.getEventSummariesForWeek(
                    anyWeek(),
                    any(),
                    any()
                )
            } returns emptyList()

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
                    Week(LocalDate.of(2022, 1, 10)),
                    EventType.All,
                    any()
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
                anyWeek(),
                any(),
                any()
            )
        } throws Exception("nope")
        val params = PagingSource.LoadParams.Refresh<Week>(null, 20, false)
        pagingSource.load(params).assertIsError()
        coVerify { repository.getEventSummariesForWeek(anyWeek(), any(), any()) }
    }

    @Test
    fun `Validate that load() handles cancellation`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                any()
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
        coVerify { repository.getEventSummariesForWeek(anyWeek(), any(), any()) }
    }

    @Test
    fun `Validate that successive loads of empty pages throttle`() = runTest {
        coEvery {
            repository.getEventSummariesForWeek(
                anyWeek(),
                any(),
                any()
            )
        } returns emptyList()
        val requestedWeeks = weekOf(2022, 1, 10)
        val params = PagingSource.LoadParams.Append(requestedWeeks, 20, false)
        pagingSource.load(params)
        val time = currentTime
        pagingSource.load(params)
        assertNotEquals(time, currentTime)
        coVerify { repository.getEventSummariesForWeek(anyWeek(), any(), any()) }
    }

    companion object {
        @Parameterized.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): Iterable<ZoneId> = timeZoneParameters()
    }
}

private fun weekOf(year: Int, month: Int, dayOfMonthAtStartOfWeek: Int): Week {
    return Week(LocalDate.of(year, month, dayOfMonthAtStartOfWeek))
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
