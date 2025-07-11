// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

object Dimens {
    private val SmallScreenContentPadding = 16.dp
    private val BigScreenContentPadding = 24.dp

    @SuppressLint("ComposableNaming")
    @Composable
    fun ScreenContentPadding(
        start: Boolean = true,
        top: Boolean = true,
        end: Boolean = true,
        bottom: Boolean = true,
    ): PaddingValues {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val horizontal = ScreenContentPaddingHorizontal(windowSizeClass)
        val vertical = ScreenContentPaddingVertical(windowSizeClass)
        return PaddingValues(
            start = if (start) horizontal else 0.dp,
            top = if (top) vertical else 0.dp,
            end = if (end) horizontal else 0.dp,
            bottom = if (bottom) vertical else 0.dp
        )
    }

    @SuppressLint("ComposableNaming")
    @Composable
    fun ScreenContentPaddingHorizontal(): Dp = ScreenContentPaddingHorizontal(currentWindowAdaptiveInfo().windowSizeClass)

    @SuppressLint("ComposableNaming")
    @Composable
    fun ScreenContentPaddingVertical(): Dp = ScreenContentPaddingVertical(currentWindowAdaptiveInfo().windowSizeClass)

    private fun ScreenContentPaddingHorizontal(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            BigScreenContentPadding
        } else {
            SmallScreenContentPadding
        }

    private fun ScreenContentPaddingVertical(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
            BigScreenContentPadding
        } else {
            SmallScreenContentPadding
        }


    val DialogContentPadding = 24.dp

    val SpacingSmall = 8.dp
    val SpacingMedium = 12.dp
    val SpacingLarge = 16.dp

    val SpacingBetweenCards = 16.dp

    /**
     * Padding that needs to be applied to the content of scrollable view so that it's not obscured by FAB when scrolled to the bottom
     */
    val FloatingActionButtonPadding = 96.dp

    fun listItemHorizontalPadding(horizontalPadding: Dp): Dp {
        // 16dp is ListItem's own hardcoded padding
        return (horizontalPadding - 16.dp).coerceAtLeast(0.dp)
    }
}
