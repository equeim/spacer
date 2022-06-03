package org.equeim.spacer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.MainThread
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.res.getBooleanOrThrow
import androidx.core.content.res.use
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.equeim.spacer.ui.screen.Destination
import org.equeim.spacer.ui.screen.donki.DonkiEventsScreen
import org.equeim.spacer.ui.theme.ApplicationTheme
import org.equeim.spacer.ui.utils.getApplicationOrThrow
import org.equeim.spacer.ui.utils.isNightModeActive
import org.equeim.spacer.ui.utils.nightMode

private const val TAG = "MainActivity"

/**
 * Singleton [AppSettings.DarkThemeMode] provider that blocks main thread when value is retrieved
 * for the first time, to not recreate [MainActivity] immediately after creation
 */
private object DarkThemeModeProvider {
    private val collectingScope = CoroutineScope(Dispatchers.Unconfined)
    private var darkThemeModeModeFlow: StateFlow<AppSettings.DarkThemeMode>? = null

    @MainThread
    fun getDarkThemeMode(context: Context): AppSettings.DarkThemeMode {
        val darkThemeModeModeFlow = this.darkThemeModeModeFlow ?: runBlocking {
            val settings = AppSettings(context.getApplicationOrThrow())
            settings.darkThemeMode.flow().onEach {
                Log.d(TAG, "DarkThemeModeProvider: darkThemeMode = $it")
            }.stateIn(collectingScope).also {
                darkThemeModeModeFlow = it
            }
        }
        return darkThemeModeModeFlow.value
    }

    fun darkThemeModeChanges(initialDarkThemeMode: AppSettings.DarkThemeMode): Flow<AppSettings.DarkThemeMode> {
        return checkNotNull(darkThemeModeModeFlow).dropWhile { it == initialDarkThemeMode }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var initialDarkThemeMode: AppSettings.DarkThemeMode

    override fun attachBaseContext(newBase: Context?) {
        Log.d(TAG, "attachBaseContext() called with: newBase = $newBase")
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(Configuration())
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration) {
        Log.d(
            TAG,
            "applyOverrideConfiguration() called with: overrideConfiguration = $overrideConfiguration"
        )
        initialDarkThemeMode = DarkThemeModeProvider.getDarkThemeMode(baseContext)
        Log.d(TAG, "applyOverrideConfiguration: initialDarkThemeMode = $initialDarkThemeMode")
        val nightMode = initialDarkThemeMode.toNightMode(baseContext)
        overrideConfiguration.uiMode =
            (baseContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    @SuppressLint("ResourceType")
    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = $savedInstanceState")
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: isNightModeActive = $isNightModeActive")
        setContent {
            MainActivityScreen()
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val (windowLightStatusBar, windowLightNavigationBar) = obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowLightNavigationBar
            )
        ).use {
            it.getBooleanOrThrow(0) to it.getBooleanOrThrow(1)
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // On some Android versions these flags may not be set correctly from theme
            // when recreating Activity and changing night mode. Set them ourselves in code.
            isAppearanceLightStatusBars = windowLightStatusBar
            isAppearanceLightNavigationBars = windowLightNavigationBar
        }
        lifecycleScope.launch {
            DarkThemeModeProvider.darkThemeModeChanges(initialDarkThemeMode).collect {
                if (it.toNightMode(baseContext) != nightMode) {
                    Log.d(TAG, "applyOverrideConfiguration: recreating activity")
                    recreate()
                }
            }
        }
    }
}

private fun AppSettings.DarkThemeMode.toNightMode(context: Context): Int {
    return when (this) {
        AppSettings.DarkThemeMode.FollowSystem -> context.nightMode
        AppSettings.DarkThemeMode.On -> Configuration.UI_MODE_NIGHT_YES
        AppSettings.DarkThemeMode.Off -> Configuration.UI_MODE_NIGHT_NO
    }
}

val LocalNavController =
    staticCompositionLocalOf<NavController<Destination>> { throw IllegalStateException() }
val LocalAppSettings = staticCompositionLocalOf<AppSettings> { throw IllegalStateException() }

@Composable
private fun MainActivityScreen() {
    ApplicationTheme {
        val insetsPadding = WindowInsets.systemBars.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        Surface(
            modifier = Modifier.fillMaxSize().padding(
                start = insetsPadding.calculateStartPadding(layoutDirection),
                end = insetsPadding.calculateEndPadding(layoutDirection)
            ),
            color = MaterialTheme.colors.background
        ) {
            val navController = rememberNavController<Destination>(DonkiEventsScreen)
            val context = LocalContext.current
            val settings = remember(context) { AppSettings(context.getApplicationOrThrow()) }
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
