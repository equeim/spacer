// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.common.DonkiNetworkStats
import org.equeim.spacer.donki.data.notifications.NotificationType
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class SettingsScreenViewModel(application: Application) : AndroidViewModel(application) {
    val settings = AppSettings(application)

    var loaded by mutableStateOf(false)
        private set
    private val loadJobs = mutableListOf<Job>()

    val darkThemeMode: StateFlow<AppSettings.DarkThemeMode> by PreferenceStateFlow(settings.darkThemeMode)
    val useSystemColors: StateFlow<Boolean> by PreferenceStateFlow(settings.useSystemColors)
    val displayEventsTimeInUTC: StateFlow<Boolean> by PreferenceStateFlow(settings.displayEventsTimeInUTC)

    val useCustomApiKey: StateFlow<Boolean> by PreferenceStateFlow(settings.useCustomNasaApiKey)
    private val _apiKeyTextFieldContent = mutableStateOf("")
    val apiKeyTextFieldContent: String by _apiKeyTextFieldContent

    val enabledDonkiNotifications: StateFlow<Set<NotificationType>> by PreferenceStateFlow(settings.backgroundNotificationsEnabledTypes)

    val rateLimit: Int? = DonkiNetworkStats.rateLimit
    val remainingRequests: Int? = DonkiNetworkStats.remainingRequests

    init {
        loadJobs.add(viewModelScope.launch {
            _apiKeyTextFieldContent.value = settings.customNasaApiKey.get()
        })

        viewModelScope.launch {
            loadJobs.apply {
                joinAll()
                clear()
            }
            loaded = true
        }
    }

    fun setNasaApiKey(apiKey: String) {
        _apiKeyTextFieldContent.value = apiKey
        settings.customNasaApiKey.set(apiKey)
    }

    private inner class PreferenceStateFlow<T : Any>(flow: Flow<T>) :
        ReadOnlyProperty<Any, StateFlow<T>> {
        constructor(preference: AppSettings.Preference<T>) : this(preference.flow())

        private lateinit var stateFlow: StateFlow<T>

        init {
            loadJobs.add(viewModelScope.launch {
                stateFlow = flow.stateIn(viewModelScope)
            })
        }

        override fun getValue(thisRef: Any, property: KProperty<*>): StateFlow<T> = stateFlow
    }
}
