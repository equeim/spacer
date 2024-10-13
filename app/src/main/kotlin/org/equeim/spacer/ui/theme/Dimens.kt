// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import org.equeim.spacer.utils.getActivityOrThrow

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
        val windowSizeClass = calculateWindowSizeClass()
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
    fun ScreenContentPaddingHorizontal(): Dp = ScreenContentPaddingHorizontal(calculateWindowSizeClass())

    @SuppressLint("ComposableNaming")
    @Composable
    fun ScreenContentPaddingVertical(): Dp = ScreenContentPaddingVertical(calculateWindowSizeClass())

    private fun ScreenContentPaddingHorizontal(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
            SmallScreenContentPadding
        } else {
            BigScreenContentPadding
        }

    private fun ScreenContentPaddingVertical(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact) {
            SmallScreenContentPadding
        } else {
            BigScreenContentPadding
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

    @Composable
    fun listItemHorizontalPadding(padding: PaddingValues): PaddingValues {
        val direction = LocalLayoutDirection.current
        val start = padding.calculateStartPadding(direction)
        val end = padding.calculateEndPadding(direction)
        return PaddingValues(start = listItemHorizontalPadding(start), end = listItemHorizontalPadding(end))
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    fun calculateWindowSizeClass(): WindowSizeClass {
        return if (LocalInspectionMode.current) {
            val config = LocalConfiguration.current
            return WindowSizeClass.calculateFromSize(DpSize(config.screenWidthDp.dp, config.screenHeightDp.dp))
        } else {
            calculateWindowSizeClass(LocalContext.current.getActivityOrThrow())
        }
    }
}
