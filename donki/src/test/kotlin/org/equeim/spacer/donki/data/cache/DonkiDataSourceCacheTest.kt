// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.model.HighSpeedStream
import org.equeim.spacer.donki.data.paging.CURRENT_INSTANT
import org.equeim.spacer.donki.instantOf
import org.equeim.spacer.donki.timeZoneParameters
import org.equeim.spacer.donki.weekOf
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DonkiDataSourceCacheTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
    private val db =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonkiDatabase::class.java).build()
    private lateinit var dataSource: DonkiDataSourceCache

    override fun before() {
        super.before()
        dataSource = DonkiDataSourceCache(ApplicationProvider.getApplicationContext(), db, coroutineDispatchers, clock)
    }

    override fun after() {
        dataSource.close()
        super.after()
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns false when week is not cached`() = runTest {
        val week = Week.getCurrentWeek(clock)
        for (type in EventType.entries) {
            assertFalse(
                dataSource.isWeekCachedAndNeedsRefresh(
                    week,
                    EventType.GeomagneticStorm,
                    refreshIfRecentlyLoaded = false
                )
            )
            assertFalse(
                dataSource.isWeekCachedAndNeedsRefresh(
                    week,
                    EventType.GeomagneticStorm,
                    refreshIfRecentlyLoaded = true
                )
            )
        }
    }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded a week after its last day`() = runTest {
        val week = Week(LocalDate.MIN)
        val eventType = EventType.GeomagneticStorm
        val loadTime = week.getInstantAfterLastDay().plus(Duration.ofDays(7))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = false))
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = true))
    }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded less than a week after its last day and more than hour ago`() =
        runTest {
            val week = Week.getCurrentWeek(clock).next()
            val eventType = EventType.GeomagneticStorm
            val loadTime = week.getInstantAfterLastDay().plus(Duration.ofDays(3))
            dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = false))
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = true))
        }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded less than a week after its last day and less than hour ago`() =
        runTest {
            val week = Week.getCurrentWeek(clock).next()
            val eventType = EventType.GeomagneticStorm
            val loadTime = Instant.now(clock).minus(Duration.ofMinutes(42))
            dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
            assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = false))
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = true))
        }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded before its last day and more than hour ago`() =
        runTest {
            val week = Week.getCurrentWeek(clock)
            val eventType = EventType.GeomagneticStorm
            val loadTime = Instant.now(clock).minus(Duration.ofHours(2))
            dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = false))
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = true))
        }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded before its last day and less than hour ago`() =
        runTest {
            val week = Week.getCurrentWeek(clock)
            val eventType = EventType.GeomagneticStorm
            val loadTime = Instant.now(clock).minus(Duration.ofMinutes(42))
            dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
            assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = false))
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType, refreshIfRecentlyLoaded = true))
        }

    @Test
    fun `Validate date range filtering when range is inside week`() = runTest {
        dataSource.cacheWeek(WEEK_1, EventType.HighSpeedStream, WEEK_1_EVENTS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_2, EventType.HighSpeedStream, WEEK_2_EVENTS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_3, EventType.HighSpeedStream, WEEK_3_EVENTS, Instant.now(clock))
        val summaries = dataSource.getEventSummariesForWeek(
            WEEK_2,
            EventType.HighSpeedStream,
            DonkiRepository.DateRange(
                firstDayInstant = instantOf(2022, 1, 18, 0, 0),
                instantAfterLastDay = instantOf(2022, 1, 22, 0, 0),
            ),
            returnCacheThatNeedsRefreshing = true
        )
        assertNotNull(summaries)
        assertEquals(
            listOf(instantOf(2022, 1, 21, 4, 2)),
            summaries.map { it.time }
        )
    }

    @Test
    fun `Validate date range filtering when range is the same as week`() = runTest {
        dataSource.cacheWeek(WEEK_1, EventType.HighSpeedStream, WEEK_1_EVENTS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_2, EventType.HighSpeedStream, WEEK_2_EVENTS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_3, EventType.HighSpeedStream, WEEK_3_EVENTS, Instant.now(clock))
        val summaries = dataSource.getEventSummariesForWeek(
            WEEK_2,
            EventType.HighSpeedStream,
            DonkiRepository.DateRange(
                firstDayInstant = WEEK_2.getFirstDayInstant(),
                instantAfterLastDay = WEEK_2.getInstantAfterLastDay(),
            ),
            returnCacheThatNeedsRefreshing = true
        )
        assertNotNull(summaries)
        assertEquals(WEEK_2_EVENTS.map { it.first.time }.toSet(), summaries.map { it.time }.toSet())
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()

        private val WEEK_1 = weekOf(2022, 1, 10)
        private val WEEK_1_EVENTS = listOf(
            instantOf(2022, 1, 14, 4, 2),
            instantOf(2022, 1, 16, 23, 59),
        ).createEvents()

        private val WEEK_2 = weekOf(2022, 1, 17)
        private val WEEK_2_EVENTS = listOf(
            instantOf(2022, 1, 17, 0, 0),
            instantOf(2022, 1, 21, 4, 2),
            instantOf(2022, 1, 23, 23, 59),
        ).createEvents()

        private val WEEK_3 = weekOf(2022, 1, 24)
        private val WEEK_3_EVENTS = listOf(
            instantOf(2022, 1, 24, 0, 0),
        ).createEvents()

        private fun List<Instant>.createEvents(): List<Pair<Event, JsonObject>> = map {
            HighSpeedStream(
                id = EventId("${DateTimeFormatter.ISO_DATE_TIME.format(it.atOffset(ZoneOffset.UTC))}-HSS-01"),
                time = it,
                link = "",
            ) to JsonObject(emptyMap())
        }
    }
}
