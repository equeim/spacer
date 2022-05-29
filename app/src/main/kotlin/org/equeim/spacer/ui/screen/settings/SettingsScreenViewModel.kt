package org.equeim.spacer.ui.screen.settings

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.equeim.spacer.AppSettings

class SettingsScreenViewModel(application: Application) : AndroidViewModel(application) {
    val settings = AppSettings(application)

    private var _loaded by mutableStateOf(false)
    val loaded by ::_loaded

    lateinit var darkThemeMode: StateFlow<AppSettings.DarkThemeMode>
    lateinit var displayEventsTimeInUTC: StateFlow<Boolean>

    init {
        viewModelScope.launch {
            @Suppress("UNCHECKED_CAST")
            loadPreferencesToStates(
                settings.darkThemeMode to { darkThemeMode = it as StateFlow<AppSettings.DarkThemeMode> },
                settings.displayEventsTimeInUTC to { displayEventsTimeInUTC = it as StateFlow<Boolean> }
            )
            _loaded = true
        }
    }

    private suspend fun loadPreferencesToStates(vararg pairs: Pair<AppSettings.Preference<*>, (StateFlow<*>) -> Unit>) {
        coroutineScope {
            for ((preference, stateSetter) in pairs) {
                launch {
                    val stateFlow = preference.flow().stateIn(viewModelScope)
                    stateSetter(stateFlow)
                }
            }
        }
    }
}
