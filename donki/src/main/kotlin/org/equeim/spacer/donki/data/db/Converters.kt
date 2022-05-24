package org.equeim.spacer.donki.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal object Converters {
    @TypeConverter
    fun instantToEpoch(instant: Instant): Long = instant.epochSecond

    @TypeConverter
    fun epochToInstant(long: Long): Instant = Instant.ofEpochSecond(long)

    @TypeConverter
    fun localDateToEpoch(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
}
