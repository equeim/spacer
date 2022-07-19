package org.equeim.spacer.donki.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.BaseCoroutineTest
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.EventType
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DonkiDataSourceCacheTest : BaseCoroutineTest() {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonkiDatabase::class.java).build()
    private lateinit var dataSource: DonkiDataSourceCache

    override fun before() {
        super.before()
        dataSource = DonkiDataSourceCache(ApplicationProvider.getApplicationContext(), db, coroutineDispatchers)
    }

    override fun after() {
        dataSource.close()
        super.after()
    }

    @Test
    fun `isWeekCachedAndNeedsRefresh() returns false when week is not cached`() = runTest {
        assertFalse(dataSource.isWeekCachedAndNeedsRefresh(Week(LocalDate.MIN), EventType.GeomagneticStorm))
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
    fun `isWeekCachedAndNeedsRefresh() returns false when week was loaded less than a week after last day`() = runTest {
        val week = Week(LocalDate.MIN)
        val eventType = EventType.GeomagneticStorm
        val loadTime = week.getInstantAfterLastDay().plus(Duration.ofDays(3))
        dataSource.cacheWeek(week, eventType, emptyList(), loadTime)
        assertTrue(dataSource.isWeekCachedAndNeedsRefresh(week, eventType))
    }
}
