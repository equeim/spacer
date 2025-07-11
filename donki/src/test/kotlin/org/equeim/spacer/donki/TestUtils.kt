// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.asExecutor
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okio.Buffer
import org.equeim.spacer.donki.data.common.Week
import java.io.InputStream
import java.net.URL
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertNotNull

internal val TEST_WEEK: Week = Week(LocalDate.of(2022, 1, 17))
internal val TEST_WEEK_NEAREST_PAST: Week = Week(LocalDate.of(2022, 1, 10))
internal val TEST_WEEK_NEAREST_FUTURE: Week = Week(LocalDate.of(2022, 1, 24))

internal val TEST_INSTANT_INSIDE_TEST_WEEK: Instant =
    TEST_WEEK.getFirstDayInstant() + Duration.ofDays(5)

internal fun instantOf(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    hour: Int,
    minute: Int,
    zoneOffset: ZoneOffset = ZoneOffset.UTC
): Instant =
    LocalDateTime.of(year, month, dayOfMonth, hour, minute).toInstant(zoneOffset)

internal fun timeZoneParameters(): List<ZoneId> = setOf(
    ZoneId.ofOffset("UTC", ZoneOffset.UTC),
    ZoneId.systemDefault(),
    ZoneId.of("Asia/Yakutsk"),
    ZoneId.of("America/Los_Angeles")
).toList()

internal fun weekOf(year: Int, month: Int, dayOfMonthAtStartOfWeek: Int): Week {
    return Week(LocalDate.of(year, month, dayOfMonthAtStartOfWeek))
}

internal val HttpUrl.apiKey: String
    get() = assertNotNull(queryParameter("api_key"))

internal val RecordedRequest.apiKey: String
    get() = assertNotNull(url).apiKey

internal fun Class<*>.readTestResourceToBuffer(path: String): Buffer =
    getTestResourceInputStream(path).use { Buffer().apply { readFrom(it) } }

internal fun Class<*>.getTestResourceInputStream(path: String): InputStream =
    assertNotNull(getResourceAsStream(getTestResourceFullPath(path)))

internal fun Class<*>.getTestResourceURL(path: String): URL =
    assertNotNull(getResource(path))

private fun Class<*>.getTestResourceFullPath(path: String): String =
    "/${packageName.replace('.', '/')}/$path"

class FakeClock(
    var instant: Instant,
    private val zone: ZoneId
) : Clock() {
    override fun instant(): Instant = instant
    override fun withZone(zone: ZoneId): Clock = FakeClock(instant, zone)
    override fun getZone(): ZoneId = zone
}

internal fun Throwable.allExceptions(): Sequence<Throwable> = sequence {
    yield(this@allExceptions)
    cause?.let { yieldAll(it.allExceptions()) }
    suppressed.forEach { yieldAll(it.allExceptions()) }
}

internal inline fun <reified T : RoomDatabase> createInMemoryTestDatabase(
    coroutineDispatchers: CoroutineDispatchers
): T {
    val executor = coroutineDispatchers.Default.asExecutor()
    return Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), T::class.java
    ).setQueryExecutor(executor).setTransactionExecutor(executor).allowMainThreadQueries().build()
}
