// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class SettingsScreenViewModel(application: Application) : AndroidViewModel(application) {
    val settings = AppSettings(application)

    var loaded by mutableStateOf(false)
        private set

    private val stateInJobs = mutableListOf<Job>()
    val darkThemeMode: StateFlow<AppSettings.DarkThemeMode> by PreferenceStateFlow(settings.darkThemeMode)
    val displayEventsTimeInUTC: StateFlow<Boolean> by PreferenceStateFlow(settings.displayEventsTimeInUTC)

    init {
        viewModelScope.launch {
            stateInJobs.apply {
                joinAll()
                clear()
            }
            loaded = true
        }
    }

    private inner class PreferenceStateFlow<T : Any>(preference: AppSettings.Preference<T>) :
        ReadOnlyProperty<Any, StateFlow<T>> {
        private lateinit var stateFlow: StateFlow<T>

        init {
            stateInJobs.add(viewModelScope.launch {
                stateFlow = preference.flow().stateIn(viewModelScope)
            })
        }

        override fun getValue(thisRef: Any, property: KProperty<*>): StateFlow<T> = stateFlow
    }
}
