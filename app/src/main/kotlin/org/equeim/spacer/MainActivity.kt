package org.equeim.spacer

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = $savedInstanceState")
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: isNightModeActive = $isNightModeActive")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            DarkThemeModeProvider.darkThemeModeChanges(initialDarkThemeMode).collect {
                if (it.toNightMode(baseContext) != nightMode) {
                    Log.d(TAG, "applyOverrideConfiguration: recreating activity")
                    recreate()
                }
            }
        }
        setContent {
            MainActivityScreen()
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
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalAppSettings provides AppSettings(LocalContext.current.getApplicationOrThrow())
            ) {
                NavBackHandler(navController)
                @OptIn(ExperimentalAnimationApi::class)
                AnimatedNavHost(navController) { it.Content() }
            }
        }
    }
}
