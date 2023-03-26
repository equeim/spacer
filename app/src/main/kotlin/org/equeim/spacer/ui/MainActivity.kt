// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import dev.olshevski.navigation.reimagined.*
import kotlinx.coroutines.flow.onEach
import org.equeim.spacer.AppSettings
import org.equeim.spacer.R
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.DonkiEventsScreen
import org.equeim.spacer.ui.theme.ApplicationTheme
import org.equeim.spacer.ui.utils.defaultLocale
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import org.equeim.spacer.utils.getApplicationOrThrow
import java.util.*

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = $savedInstanceState")
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MainActivityScreen(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged() called with: newConfig = $newConfig")
        super.onConfigurationChanged(newConfig)
    }
}

val LocalDefaultLocale = compositionLocalOf<Locale> { throw IllegalStateException() }
val LocalAppSettings = staticCompositionLocalOf<AppSettings> { throw IllegalStateException() }
val LocalNavController =
    staticCompositionLocalOf<NavController<Destination>> { throw IllegalStateException() }

@Composable
private fun MainActivityScreen(activity: MainActivity) {
    val context = LocalContext.current

    val isDarkTheme by isDarkTheme(activity)
    LaunchedEffect(activity) {
        snapshotFlow { isDarkTheme }
            .collect {
                val window = activity.window
                setDarkThemeWindowProperties(window, window.decorView, it)
            }
    }

    val defaultLocale by remember(context) {
        context.defaultLocaleFlow().onEach {
            Log.d(TAG, "Default locale is $it")
        }
    }.collectAsState(context.defaultLocale)
    val settings = remember { AppSettings(context.getApplicationOrThrow()) }
    val navController = rememberNavController<Destination>(DonkiEventsScreen)

    CompositionLocalProvider(
        LocalDefaultLocale provides defaultLocale,
        LocalAppSettings provides settings,
        LocalNavController provides navController
    ) {
        ApplicationTheme(isDarkTheme) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)) {
                NavBackHandler(navController)
                @OptIn(ExperimentalAnimationApi::class)
                AnimatedNavHost(navController, Modifier.fillMaxSize()) { it.Content() }
            }
        }
    }
}

private fun setDarkThemeWindowProperties(window: Window, view: View, isDarkTheme: Boolean) {
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !isDarkTheme
        isAppearanceLightNavigationBars = !isDarkTheme
    }
    if (Build.VERSION.SDK_INT < 29) {
        window.navigationBarColor = view.context.getColor(
            if (isDarkTheme) R.color.navigation_bar_color_dark_theme else R.color.navigation_bar_color_light_theme
        )
    }
}

@Composable
private fun isDarkTheme(activity: MainActivity): State<Boolean> {
    val colorsSettingsProvider = remember {
        ColorsSettingsProvider.init(activity.getApplicationOrThrow())
    }
    val darkThemeMode by remember {
        colorsSettingsProvider.darkThemeModeMode
    }.collectAsState()
    LaunchedEffect(null) {
        snapshotFlow { darkThemeMode }
            .collect { Log.d(TAG, "darkThemeMode is $it") }
    }

    val isSystemInDarkTheme by rememberUpdatedState(isSystemInDarkTheme())
    LaunchedEffect(null) {
        snapshotFlow { isSystemInDarkTheme }
            .collect { Log.d(TAG, "isSystemInDarkTheme is $it") }
    }

    val isDarkTheme = remember {
        derivedStateOf {
            when (darkThemeMode) {
                AppSettings.DarkThemeMode.FollowSystem -> isSystemInDarkTheme
                AppSettings.DarkThemeMode.On -> true
                AppSettings.DarkThemeMode.Off -> false
            }
        }
    }
    LaunchedEffect(isDarkTheme) {
        snapshotFlow { isDarkTheme.value }
            .collect { Log.d(TAG, "isDarkTheme is $it") }
    }
    return isDarkTheme
}
