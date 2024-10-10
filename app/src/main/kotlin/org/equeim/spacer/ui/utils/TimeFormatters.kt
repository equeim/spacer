// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

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
