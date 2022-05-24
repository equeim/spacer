package org.equeim.spacer.donki

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

fun instantOf(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, zoneOffset: ZoneOffset = ZoneOffset.UTC): Instant =
    LocalDateTime.of(year, month, dayOfMonth, hour, minute).toInstant(zoneOffset)

fun instantOf(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, zoneId: ZoneId): Instant =
    LocalDateTime.of(year, month, dayOfMonth, hour, minute).atZone(zoneId).toInstant()
