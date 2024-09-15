// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import androidx.room.TypeConverter
import java.time.Instant

internal object InstantConverters {
    @TypeConverter
    fun instantToEpoch(instant: Instant): Long = instant.epochSecond

    @TypeConverter
    fun instantFromEpoch(long: Long): Instant = Instant.ofEpochSecond(long)
}
