package org.equeim.spacer.donki.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.paging.CURRENT_INSTANT
import org.equeim.spacer.donki.timeZoneParameters
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.time.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class DonkiDataSourceCacheTest(systemTimeZone: ZoneId) : BaseCoroutineTest() {
    private val clock = Clock.fixed(CURRENT_INSTANT, systemTimeZone)
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonkiDatabase::class.java).build()
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
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(Week.getCurrentWeek(clock), EventType.GeomagneticStorm))
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns false when week was loaded week after last day`() = runTest {
        val week = Week(LocalDate.MIN)
        val eventType = EventType.GeomagneticStorm
        val loadTime = week.getInstantAfterLastDay().plus(Duration.ofDays(7))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType))
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns true when week was loaded less than a week after last day and more than hour ago`() = runTest {
        val week = Week.getCurrentWeek(clock).next()
        val eventType = EventType.GeomagneticStorm
        val loadTime = week.getInstantAfterLastDay().plus(Duration.ofDays(3))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType))
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns false when week was loaded less than a week after last day and less than hour ago`() = runTest {
        val week = Week.getCurrentWeek(clock).next()
        val eventType = EventType.GeomagneticStorm
        val loadTime = Instant.now(clock).minus(Duration.ofMinutes(42))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType))
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns true when week was loaded before last day and more than hour ago`() = runTest {
        val week = Week.getCurrentWeek(clock)
        val eventType = EventType.GeomagneticStorm
        val loadTime = Instant.now(clock).minus(Duration.ofHours(2))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType))
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns false when week was loaded before last day and less than hour ago`() = runTest {
        val week = Week.getCurrentWeek(clock)
        val eventType = EventType.GeomagneticStorm
        val loadTime = Instant.now(clock).minus(Duration.ofMinutes(42))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(week, eventType))
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "systemTimeZone={0}")
        @JvmStatic
        fun parameters(): List<ZoneId> = timeZoneParameters()
    }
}
