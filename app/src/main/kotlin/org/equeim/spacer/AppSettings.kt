// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "Settings"

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {
    enum class DarkThemeMode {
        FollowSystem,
        On,
        Off;

        companion object {
            val isFollowSystemSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val Default = if (isFollowSystemSupported) {
                FollowSystem
            } else {
                On
            }

            val enumToString = mapOf(
                FollowSystem to "followSystem",
                On to "on",
                Off to "off"
            )
            val stringToEnum = enumToString.entries.associate { (k, v) -> v to k }
        }
    }

    val darkThemeMode: Preference<DarkThemeMode> =
        preference(stringPreferencesKey("darkTheme")) { DarkThemeMode.enumToString[DarkThemeMode.Default]!! }
            .map(
                fromOriginalToMapped = { DarkThemeMode.stringToEnum[it] ?: DarkThemeMode.Default },
                fromMappedToOriginal = { DarkThemeMode.enumToString[it]!! }
            )

    val useSystemColors: Preference<Boolean> =
        preference(booleanPreferencesKey("useSystemColors")) { false }

    val displayEventsTimeInUTC: Preference<Boolean> = preference(booleanPreferencesKey("displayEventsTimeInUTC")) { false }

    interface Preference<T : Any> {
        suspend fun get(): T
        fun set(value: T)
        fun flow(): Flow<T>
    }

    private fun <T : Any> preference(key: Preferences.Key<T>, defaultValueProducer: () -> T): Preference<T> =
        PreferenceImpl(context.dataStore, key, defaultValueProducer)
}

private class PreferenceImpl<T : Any>(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    private val defaultValueProducer: () -> T
) : AppSettings.Preference<T> {
    override suspend fun get(): T {
        return dataStore.data.first()[key] ?: defaultValueProducer()
    }


    @OptIn(DelicateCoroutinesApi::class)
    override fun set(value: T) {
        GlobalScope.launch {
            dataStore.edit {
                it[key] = value
            }
        }
    }

    override fun flow(): Flow<T> {
        return dataStore.data
            .map { prefs ->
                prefs[key] ?: defaultValueProducer()
            }.distinctUntilChanged()
    }
}

private class MappedPreferenceImpl<Original : Any, Mapped : Any>(
    private val original: AppSettings.Preference<Original>,
    private val fromOriginalToMapped: (Original) -> Mapped,
    private val fromMappedToOriginal: (Mapped) -> Original
) : AppSettings.Preference<Mapped> {
    override suspend fun get(): Mapped = fromOriginalToMapped(original.get())
    override fun flow(): Flow<Mapped> = original.flow().map(fromOriginalToMapped)
    override fun set(value: Mapped) = original.set(fromMappedToOriginal(value))
}

private fun <Original : Any, Mapped : Any> AppSettings.Preference<Original>.map(
    fromOriginalToMapped: (Original) -> Mapped,
    fromMappedToOriginal: (Mapped) -> Original
): AppSettings.Preference<Mapped> = MappedPreferenceImpl(this, fromOriginalToMapped, fromMappedToOriginal)
