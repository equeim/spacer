package org.equeim.spacer

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.equeim.spacer.ui.screen.Destination
import org.equeim.spacer.ui.screen.donki.DonkiEventsScreen
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
    val defaultLocale by remember(context) {
        context.defaultLocaleFlow().onEach {
            Log.d(TAG, "Default locale is $it")
        }
    }.collectAsState(context.defaultLocale)
    val settings = remember { AppSettings(context.getApplicationOrThrow()) }

    CompositionLocalProvider(
        LocalDefaultLocale provides defaultLocale,
        LocalAppSettings provides settings
    ) {
        val isDarkTheme by isDarkTheme(activity)
        val window = remember(activity) { activity.window }
        LaunchedEffect(window) {
            snapshotFlow { isDarkTheme }
                .collect { setDarkThemeWindowProperties(window, window.decorView, it) }
        }

        ApplicationTheme(isDarkTheme) {
            val insetsPadding = WindowInsets.systemBars.asPaddingValues()
            val layoutDirection = LocalLayoutDirection.current
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = insetsPadding.calculateStartPadding(layoutDirection),
                        end = insetsPadding.calculateEndPadding(layoutDirection)
                    ),
                color = MaterialTheme.colors.background
            ) {
                val navController = rememberNavController<Destination>(DonkiEventsScreen)
                CompositionLocalProvider(
                    LocalNavController provides navController,
                    LocalAppSettings provides settings
                ) {
                    NavBackHandler(navController)
                    @OptIn(ExperimentalAnimationApi::class)
                    AnimatedNavHost(navController) { it.Content() }
                }
            }
        }
    }
}

/**
 * Singleton [AppSettings.DarkThemeMode] provider that blocks current thread when value is retrieved
 * for the first time, to not recompose everything immediately after creation
 */
private object DarkThemeModeProvider {
    private val collectingScope = CoroutineScope(Dispatchers.Unconfined)

    @Volatile
    private var darkThemeModeModeFlow: StateFlow<AppSettings.DarkThemeMode>? = null

    fun darkThemeMode(application: Application): StateFlow<AppSettings.DarkThemeMode> {
        return this.darkThemeModeModeFlow ?: runBlocking {
            val settings = AppSettings(application)
            settings.darkThemeMode.flow().stateIn(collectingScope).also {
                darkThemeModeModeFlow = it
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
    val darkThemeMode by remember {
        DarkThemeModeProvider.darkThemeMode(activity.getApplicationOrThrow())
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
