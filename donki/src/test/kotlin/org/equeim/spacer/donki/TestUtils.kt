// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import io.mockk.MockKMatcherScope
import okhttp3.HttpUrl
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.equeim.spacer.donki.data.common.Week
import java.net.URL
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.io.path.inputStream
import kotlin.test.assertNotNull

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

internal fun Path.readToBuffer() = Buffer().apply {
    this@readToBuffer.inputStream().use { readFrom(it) }
}

internal fun URL.readToBuffer() = Buffer().apply {
    this@readToBuffer.openStream().use { readFrom(it) }
}

internal fun Class<*>.getTestResource(path: String): URL =
    assertNotNull(getResource("/${packageName.replace('.', '/')}/$path"))
