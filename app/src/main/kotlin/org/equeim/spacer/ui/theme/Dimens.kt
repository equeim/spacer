// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.equeim.spacer.utils.getActivityOrThrow

object Dimens {
    private val SmallScreenContentPadding = 16.dp
    private val BigScreenContentPadding = 24.dp

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @SuppressLint("ComposableNaming")
    @Composable
    fun ScreenContentPadding(
        start: Boolean = true,
        top: Boolean = true,
        end: Boolean = true,
        bottom: Boolean = true,
    ): PaddingValues {
        val windowSizeClass = calculateWindowSizeClass(LocalContext.current.getActivityOrThrow())
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
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    fun ScreenContentPaddingHorizontal(): Dp = ScreenContentPaddingHorizontal(calculateWindowSizeClass(LocalContext.current.getActivityOrThrow()))

    @SuppressLint("ComposableNaming")
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    fun ScreenContentPaddingVertical(): Dp = ScreenContentPaddingVertical(calculateWindowSizeClass(LocalContext.current.getActivityOrThrow()))

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
}
