// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import android.util.Log
import io.mockk.Answer
import io.mockk.Call
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.HttpUrl
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.equeim.spacer.donki.data.common.Week
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.io.path.inputStream
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

internal fun MockKMatcherScope.anyWeek(): Week = Week(any())

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
    get() = assertNotNull(requestUrl).apiKey

internal fun Class<*>.readTestResourceToBuffer(path: String): Buffer =
    getTestResourceInputStream(path).use { Buffer().apply { readFrom(it) } }

internal fun Class<*>.getTestResourceInputStream(path: String): InputStream =
    assertNotNull(getResourceAsStream(getTestResourceFullPath(path)))

internal fun Class<*>.getTestResourceURL(path: String): URL =
    assertNotNull(getResource(path))

private fun Class<*>.getTestResourceFullPath(path: String): String =
    "/${packageName.replace('.', '/')}/$path"

internal fun mockkLog() {
    mockkStatic(Log::class)

    every { Log.v(any(), any()) } answers LogAnswer
    every { Log.v(any(), any(), any()) } answers LogAnswer

    every { Log.d(any(), any()) } answers LogAnswer
    every { Log.d(any(), any(), any()) } answers LogAnswer

    every { Log.i(any(), any()) } answers LogAnswer
    every { Log.i(any(), any(), any()) } answers LogAnswer

    every { Log.w(any(), any<String>()) } answers LogAnswer
    every { Log.w(any(), any<Throwable>()) } answers LogAnswer
    every { Log.w(any(), any(), any()) } answers LogAnswer

    every { Log.e(any(), any()) } answers LogAnswer
    every { Log.e(any(), any(), any()) } answers LogAnswer

    every { Log.wtf(any(), any<String>()) } answers LogAnswer
    every { Log.wtf(any(), any<Throwable>()) } answers LogAnswer
    every { Log.wtf(any(), any(), any()) } answers LogAnswer

    every { Log.println(any(), any(), any()) } answers LogAnswer
}

internal fun unmockkLog() {
    unmockkStatic(Log::class)
}

private object LogAnswer : Answer<Int> {
    override fun answer(call: Call): Int {
        val args = call.invocation.args.toMutableList()

        val priority: String
        val tag: String

        when (val arg = args.removeFirst()) {
            is Int -> {
                priority = when (args[0] as Int) {
                    Log.VERBOSE -> "V"
                    Log.DEBUG -> "D"
                    Log.INFO -> "I"
                    Log.WARN -> "W"
                    Log.ERROR -> "E"
                    Log.ASSERT -> "ASSERT"
                    else -> throw IllegalArgumentException()
                }
                tag = args.removeFirst() as String
            }
            is String -> {
                priority = call.invocation.method.name.uppercase()
                tag = arg
            }
            else -> throw IllegalArgumentException()
        }

        val message: String
        val throwable: Throwable?

        when (val arg = args.removeFirst()) {
            is String -> {
                message = arg
                throwable = args.removeFirstOrNull() as Throwable?
            }
            is Throwable -> {
                message = ""
                throwable = arg
            }
            else -> throw IllegalArgumentException()
        }

        print(tag)
        print(" ")
        print(priority)
        print(": ")
        println(message)
        throwable?.printStackTrace()

        return 0
    }
}

class FakeClock(
    var instant: Instant,
    private val zone: ZoneId
) : Clock() {
    override fun instant(): Instant = instant
    override fun withZone(zone: ZoneId): Clock = FakeClock(instant, zone)
    override fun getZone(): ZoneId = zone
}
