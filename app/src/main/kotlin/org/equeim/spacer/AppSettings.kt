// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataMigration
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

private val Context.dataStore by preferencesDataStore(name = "settings", produceMigrations = {
    listOf(
        object : DataMigration<Preferences> {
            override suspend fun cleanUp() {
                Log.d(TAG, "cleanUp() called")
            }

            override suspend fun migrate(currentData: Preferences): Preferences {
                Log.d(TAG, "migrate() called with: currentData = $currentData")
                return currentData
            }

            override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                Log.d(TAG, "shouldMigrate() called with: currentData = $currentData")
                return true
            }

        }
    )
})

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
                transformFromBase = { DarkThemeMode.stringToEnum[it] ?: DarkThemeMode.Default },
                transformToBase = { DarkThemeMode.enumToString[it]!! }
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
        PreferenceImpl(key, defaultValueProducer)

    private inner class PreferenceImpl<T : Any>(
        private val key: Preferences.Key<T>,
        private val defaultValueProducer: () -> T
    ) : Preference<T> {
        override suspend fun get(): T {
            return context.dataStore.data.first()[key] ?: defaultValueProducer()
        }

        @OptIn(DelicateCoroutinesApi::class)
        override fun set(value: T) {
            GlobalScope.launch {
                context.dataStore.edit {
                    it[key] = value
                }
            }
        }

        override fun flow(): Flow<T> {
            return context.dataStore.data.map { it[key] ?: defaultValueProducer() }.distinctUntilChanged()
        }
    }
}

private fun <T : Any, V : Any> AppSettings.Preference<T>.map(
    transformFromBase: (T) -> V,
    transformToBase: (V) -> T
): AppSettings.Preference<V> {
    return object : AppSettings.Preference<V> {
        override suspend fun get(): V = transformFromBase(this@map.get())
        override fun set(value: V) = this@map.set(transformToBase(value))
        override fun flow(): Flow<V> = this@map.flow().map(transformFromBase)
    }
}
