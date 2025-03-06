// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.rememberNavController
import dev.olshevski.navigation.reimagined.replaceAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.ScreenDestinationNavHost
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreen
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreen
import org.equeim.spacer.ui.screens.donki.notifications.details.NotificationDetailsScreen
import org.equeim.spacer.ui.theme.ApplicationTheme
import org.equeim.spacer.ui.utils.defaultLocale
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import org.equeim.spacer.utils.getApplicationOrThrow
import java.util.Locale

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private val notificationsDeepLinks = Channel<NotificationId>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = $savedInstanceState")
        Log.d(TAG, "onCreate: intent = $intent")
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }
        setContent {
            MainActivityScreen(this, viewModel, notificationsDeepLinks)
        }

        viewModel.init()
        lifecycleScope.launch { lifecycle.currentStateFlow.collect(viewModel.activityLifecycleState) }
    }

    override fun onNewIntent(intent: Intent) {
        Log.d(TAG, "onNewIntent() called with: intent = $intent")
        super.onNewIntent(intent)
        handleDeepLink(intent)

    }

    private fun handleDeepLink(intent: Intent) {
        intent.notificationId?.let {
            Log.d(TAG, "handleDeepLink: received deep link $it")
            notificationsDeepLinks.trySend(it)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged() called with: newConfig = $newConfig")
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        private const val NOTIFICATION_ID_EXTRA = "notificationId"

        private val Intent.notificationId: NotificationId?
            get() = IntentCompat.getParcelableExtra(this, NOTIFICATION_ID_EXTRA, NotificationId::class.java)

        fun createIntentForNotification(notificationId: NotificationId, context: Context): Intent =
            Intent(context, MainActivity::class.java)
                //.setAction(Intent.ACTION_VIEW)
                .putExtra(NOTIFICATION_ID_EXTRA, notificationId)
    }
}

val LocalDefaultLocale = compositionLocalOf<Locale> { throw IllegalStateException() }
val LocalAppSettings = staticCompositionLocalOf<AppSettings> { throw IllegalStateException() }

@Composable
private fun MainActivityScreen(
    activity: MainActivity,
    viewModel: MainActivityViewModel,
    notificationsDeepLinks: Channel<NotificationId>
) {

    val isDarkTheme by isDarkTheme(activity)
    LaunchedEffect(activity) {
        snapshotFlow { isDarkTheme }
            .collect {
                val window = activity.window
                setDarkThemeWindowProperties(window, window.decorView, it)
            }
    }

    val defaultLocale by remember(activity) {
        activity.defaultLocaleFlow().onEach {
            Log.d(TAG, "Default locale is $it")
        }
    }.collectAsState(activity.defaultLocale)
    val settings = remember { AppSettings(activity.getApplicationOrThrow()) }

    CompositionLocalProvider(
        LocalDefaultLocale provides defaultLocale,
        LocalAppSettings provides settings
    ) {
        ApplicationTheme(isDarkTheme) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val navController = rememberNavController<Destination>(DonkiEventsScreen)
                LaunchedEffect(navController) {
                    for (notificationId in notificationsDeepLinks) {
                        navController.replaceAll(notificationId.createBackStack())
                    }
                }
                LaunchedEffect(navController) {
                    snapshotFlow { navController.isOnDonkiNotificationsScreen }
                        .collect(viewModel.isOnDonkiNotificationsScreen)
                }
                ScreenDestinationNavHost(navController)
            }
        }
    }
}

private fun NotificationId.createBackStack(): List<Destination> =
    listOf(DonkiEventsScreen, DonkiNotificationsScreen, NotificationDetailsScreen(this))

private val NavController<Destination>.isOnDonkiNotificationsScreen: Boolean
    get() = backstack.entries.lastOrNull()?.destination is DonkiNotificationsScreen

private fun setDarkThemeWindowProperties(window: Window, view: View, isDarkTheme: Boolean) {
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !isDarkTheme
        isAppearanceLightNavigationBars = !isDarkTheme
    }
    if (Build.VERSION.SDK_INT < 29) {
        @Suppress("DEPRECATION")
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
