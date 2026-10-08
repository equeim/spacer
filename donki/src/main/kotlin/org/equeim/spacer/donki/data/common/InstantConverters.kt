// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import androidx.room3.ColumnTypeConverter
import java.time.Instant

internal object InstantConverters {
    @ColumnTypeConverter
    fun instantToEpoch(instant: Instant): Long = instant.epochSecond

    @ColumnTypeConverter
    fun instantFromEpoch(long: Long): Instant = Instant.ofEpochSecond(long)
}
