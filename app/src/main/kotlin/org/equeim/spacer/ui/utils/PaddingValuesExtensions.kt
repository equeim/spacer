// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
infix operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + other.calculateBottomPadding()
    )
}

val PaddingValues.hasBottomPadding: Boolean
    get() = calculateBottomPadding().value != 0.0f

@Composable
fun PaddingValues.addBottomInsetUnless(condition: Boolean): PaddingValues {
    return if (condition) this else addBottomInset()
}

@Composable
private fun PaddingValues.addBottomInset(): PaddingValues {
    val bottom = with(LocalDensity.current) { WindowInsets.systemBars.getBottom(this).toDp() }
    return this + PaddingValues(bottom = bottom)
}
