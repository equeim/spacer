// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.equeim.spacer.ui.ColorsSettingsProvider
import org.equeim.spacer.utils.getApplicationOrThrow

private val lightScheme = lightColorScheme(
    primary = Colors.primaryLight,
    onPrimary = Colors.onPrimaryLight,
    primaryContainer = Colors.primaryContainerLight,
    onPrimaryContainer = Colors.onPrimaryContainerLight,
    secondary = Colors.secondaryLight,
    onSecondary = Colors.onSecondaryLight,
    secondaryContainer = Colors.secondaryContainerLight,
    onSecondaryContainer = Colors.onSecondaryContainerLight,
    tertiary = Colors.tertiaryLight,
    onTertiary = Colors.onTertiaryLight,
    tertiaryContainer = Colors.tertiaryContainerLight,
    onTertiaryContainer = Colors.onTertiaryContainerLight,
    error = Colors.errorLight,
    onError = Colors.onErrorLight,
    errorContainer = Colors.errorContainerLight,
    onErrorContainer = Colors.onErrorContainerLight,
    background = Colors.backgroundLight,
    onBackground = Colors.onBackgroundLight,
    surface = Colors.surfaceLight,
    onSurface = Colors.onSurfaceLight,
    surfaceVariant = Colors.surfaceVariantLight,
    onSurfaceVariant = Colors.onSurfaceVariantLight,
    outline = Colors.outlineLight,
    outlineVariant = Colors.outlineVariantLight,
    scrim = Colors.scrimLight,
    inverseSurface = Colors.inverseSurfaceLight,
    inverseOnSurface = Colors.inverseOnSurfaceLight,
    inversePrimary = Colors.inversePrimaryLight,
    surfaceDim = Colors.surfaceDimLight,
    surfaceBright = Colors.surfaceBrightLight,
    surfaceContainerLowest = Colors.surfaceContainerLowestLight,
    surfaceContainerLow = Colors.surfaceContainerLowLight,
    surfaceContainer = Colors.surfaceContainerLight,
    surfaceContainerHigh = Colors.surfaceContainerHighLight,
    surfaceContainerHighest = Colors.surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = Colors.primaryDark,
    onPrimary = Colors.onPrimaryDark,
    primaryContainer = Colors.primaryContainerDark,
    onPrimaryContainer = Colors.onPrimaryContainerDark,
    secondary = Colors.secondaryDark,
    onSecondary = Colors.onSecondaryDark,
    secondaryContainer = Colors.secondaryContainerDark,
    onSecondaryContainer = Colors.onSecondaryContainerDark,
    tertiary = Colors.tertiaryDark,
    onTertiary = Colors.onTertiaryDark,
    tertiaryContainer = Colors.tertiaryContainerDark,
    onTertiaryContainer = Colors.onTertiaryContainerDark,
    error = Colors.errorDark,
    onError = Colors.onErrorDark,
    errorContainer = Colors.errorContainerDark,
    onErrorContainer = Colors.onErrorContainerDark,
    background = Colors.backgroundDark,
    onBackground = Colors.onBackgroundDark,
    surface = Colors.surfaceDark,
    onSurface = Colors.onSurfaceDark,
    surfaceVariant = Colors.surfaceVariantDark,
    onSurfaceVariant = Colors.onSurfaceVariantDark,
    outline = Colors.outlineDark,
    outlineVariant = Colors.outlineVariantDark,
    scrim = Colors.scrimDark,
    inverseSurface = Colors.inverseSurfaceDark,
    inverseOnSurface = Colors.inverseOnSurfaceDark,
    inversePrimary = Colors.inversePrimaryDark,
    surfaceDim = Colors.surfaceDimDark,
    surfaceBright = Colors.surfaceBrightDark,
    surfaceContainerLowest = Colors.surfaceContainerLowestDark,
    surfaceContainerLow = Colors.surfaceContainerLowDark,
    surfaceContainer = Colors.surfaceContainerDark,
    surfaceContainerHigh = Colors.surfaceContainerHighDark,
    surfaceContainerHighest = Colors.surfaceContainerHighestDark,
)

@Composable
fun ApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useSystemColors by remember {
        ColorsSettingsProvider.init(context.getApplicationOrThrow()).useSystemColors
    }.collectAsState()
    val colors = if (darkTheme) {
        if (useSystemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            darkScheme
        }
    } else {
        if (useSystemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            lightScheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
