// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.events.CURRENT_INSTANT
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.donki.instantOf
import org.equeim.spacer.donki.timeZoneParameters
import org.equeim.spacer.donki.weekOf
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NotificationsDataSourceCacheTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
    private val db =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NotificationsDatabase::class.java).build()
    private lateinit var dataSource: NotificationsDataSourceCache

    override fun before() {
        super.before()
        dataSource = NotificationsDataSourceCache(ApplicationProvider.getApplicationContext(), db, coroutineDispatchers, clock)
    }

    override fun after() {
        dataSource.close()
        super.after()
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns false when week is not cached`() = runTest {
        val week = Week.getCurrentWeek(clock)
        assertFalse(
            dataSource.isWeekCachedAndNeedsRefresh(
                week,
                refreshIfRecentlyLoaded = false
            )
        )
        assertFalse(
            dataSource.isWeekCachedAndNeedsRefresh(
                week,
                refreshIfRecentlyLoaded = true
            )
        )
    }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded after its last day`() = runTest {
        val week = Week(LocalDate.MIN)
        val loadTime = week.getInstantAfterLastDay()
        dataSource.cacheWeek(week, emptyList(), loadTime)
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = false))
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = true))
    }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded before its end and more than hour ago`() =
        runTest {
            val week = assertNotNull(Week.getCurrentWeek(clock))
            val loadTime = week.getFirstDayInstant().plus(Duration.ofDays(3))
            dataSource.cacheWeek(week, emptyList(), loadTime)
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = false))
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = true))
        }

    @Test
    fun `Validate isWeekCachedAndNeedsRefresh() when week was loaded before its end and less than hour ago`() =
        runTest {
            val week = assertNotNull(Week.getCurrentWeek(clock))
            val loadTime = Instant.now(clock).minus(Duration.ofMinutes(42))
            dataSource.cacheWeek(week, emptyList(), loadTime)
            assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = false))
            assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, refreshIfRecentlyLoaded = true))
        }

    @Test
    fun `Validate date range filtering when range is inside week`() = runTest {
        dataSource.cacheWeek(WEEK_1, WEEK_1_NOTIFICATIONS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_2, WEEK_2_NOTIFICATIONS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_3, WEEK_3_NOTIFICATIONS, Instant.now(clock))
        val summaries = dataSource.getNotificationSummariesForWeek(
            WEEK_2,
            NotificationType.entries,
            DateRange(
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
        dataSource.cacheWeek(WEEK_1, WEEK_1_NOTIFICATIONS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_2, WEEK_2_NOTIFICATIONS, Instant.now(clock))
        dataSource.cacheWeek(WEEK_3, WEEK_3_NOTIFICATIONS, Instant.now(clock))
        val summaries = dataSource.getNotificationSummariesForWeek(
            WEEK_2,
            NotificationType.entries,
            DateRange(
                firstDayInstant = WEEK_2.getFirstDayInstant(),
                instantAfterLastDay = WEEK_2.getInstantAfterLastDay(),
            ),
            returnCacheThatNeedsRefreshing = true
        )
        assertNotNull(summaries)
        assertEquals(WEEK_2_NOTIFICATIONS.map { it.time }.toSet(), summaries.map { it.time }.toSet())
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()

        private val WEEK_1 = weekOf(2022, 1, 10)
        private val WEEK_1_NOTIFICATIONS = listOf(
            instantOf(2022, 1, 14, 4, 2),
            instantOf(2022, 1, 16, 23, 59),
        ).createNotifications()

        private val WEEK_2 = weekOf(2022, 1, 17)
        private val WEEK_2_NOTIFICATIONS = listOf(
            instantOf(2022, 1, 17, 0, 0),
            instantOf(2022, 1, 21, 4, 2),
            instantOf(2022, 1, 23, 23, 59),
        ).createNotifications()

        private val WEEK_3 = weekOf(2022, 1, 24)
        private val WEEK_3_NOTIFICATIONS = listOf(
            instantOf(2022, 1, 24, 0, 0),
        ).createNotifications()

        private fun List<Instant>.createNotifications(): List<CachedNotification> = map {
            CachedNotification(
                id = NotificationId("${DateTimeFormatter.ISO_DATE_TIME.format(it.atOffset(ZoneOffset.UTC))}-AL-01"),
                type = NotificationType.HighSpeedStream,
                time = it,
                title = "",
                body = "",
                link = "",
                read = false
            )
        }
    }
}
