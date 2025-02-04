// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.equeim.spacer.AppSettings

/**
 * Singleton that blocks current thread while [AppSettings.darkThemeMode] and [AppSettings.useSystemColors]
 * are retrieved for the first time, to not recompose everything immediately after creation
 */
object ColorsSettingsProvider {
    private val collectingScope = CoroutineScope(Dispatchers.Unconfined)

    lateinit var darkThemeModeMode: StateFlow<AppSettings.DarkThemeMode>
        private set

    lateinit var useSystemColors: StateFlow<Boolean>
        private set

    fun init(application: Application): ColorsSettingsProvider {
        if (::darkThemeModeMode.isInitialized) return this
        runBlocking {
            val settings = AppSettings(application)
            darkThemeModeMode = settings.darkThemeMode.flow().stateIn(collectingScope)
            useSystemColors = settings.useSystemColors.flow().stateIn(collectingScope)
        }
        return this
    }
}
