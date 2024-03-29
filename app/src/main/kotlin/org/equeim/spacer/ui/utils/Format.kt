// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.equeim.spacer.donki.data.model.units.Angle
import org.equeim.spacer.ui.LocalDefaultLocale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun rememberIntegerFormatter(): NumberFormat {
    val locale = LocalDefaultLocale.current
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

@Composable
fun rememberFloatFormatter(): DecimalFormat {
    val locale = LocalDefaultLocale.current
    return remember(locale) { DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)) }
}

fun createEventDateTimeFormatter(locale: Locale, zone: ZoneId): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
        .withLocale(locale)
        .withZone(zone)

fun createEventTimeFormatter(locale: Locale, zone: ZoneId): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)

fun createEventDateFormatter(locale: Locale, zone: ZoneId): DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendLocalized(FormatStyle.LONG, null)
        .appendLiteral(' ')
        .appendZoneText(TextStyle.SHORT)
        .toFormatter(locale)
        .withZone(zone)

val ZoneId.isUTC: Boolean get() = id == "UTC"

fun determineEventTimeZone(defaultZone: ZoneId, displayEventsTimeInUTC: Boolean): ZoneId = if (displayEventsTimeInUTC) {
    ZoneId.of("UTC")
} else {
    defaultZone
}

class CoordinatesFormatter(locale: Locale) {
    private val integerFormatter: NumberFormat = NumberFormat.getIntegerInstance(locale)
    private val secondsFormatter = DecimalFormat("00.###", DecimalFormatSymbols.getInstance(locale))

    fun format(latitude: Angle, longitude: Angle): String = buildString {
        append(formatCoordinate(latitude, if (latitude.degrees >= 0.0f) "N" else "S"))
        append(' ')
        append(formatCoordinate(longitude, if (longitude.degrees >= 0.0f) "E" else "W"))
    }

    private fun formatCoordinate(coordinate: Angle, hemisphere: String): String {
        val absDegrees = abs(coordinate.degrees)
        val degrees = absDegrees.toInt()
        val minutesFloat = (absDegrees - degrees) * 60.0f
        val minutes = minutesFloat.toInt()
        val seconds = (minutesFloat - minutes) * 60.0f
        return buildString {
            append(integerFormatter.format(degrees))
            append("°")
            append(integerFormatter.format(minutes))
            append("′")
            append(secondsFormatter.format(seconds))
            append("″")
            append(hemisphere)
        }
    }
}

@Composable
fun rememberCoordinatesFormatter(): CoordinatesFormatter {
    val locale = LocalDefaultLocale.current
    return remember(locale) { CoordinatesFormatter(locale) }
}
