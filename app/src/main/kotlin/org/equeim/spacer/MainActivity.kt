package org.equeim.spacer

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import org.equeim.spacer.ui.screen.DonkiEventsScreen
import org.equeim.spacer.ui.theme.ApplicationTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
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
        val nightMode = Configuration.UI_MODE_NIGHT_YES
        overrideConfiguration.uiMode =
            (baseContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = $savedInstanceState")
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: isNightModeActive = ${isNightModeActive()}")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
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
                    DonkiEventsScreen()
                }
            }
        }
    }

    private fun isNightModeActive() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
