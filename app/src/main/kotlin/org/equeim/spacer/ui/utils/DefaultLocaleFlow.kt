// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

fun Context.defaultLocaleFlow(): Flow<Locale> = callbackFlow {
    val callback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            launch { send(newConfig.locales[0]) }
        }
        override fun onLowMemory() = Unit
    }
    registerComponentCallbacks(callback)
    awaitClose { unregisterComponentCallbacks(callback) }
}.onStart { emit(defaultLocale) }.conflate().distinctUntilChanged()

val Context.defaultLocale: Locale
    get() = resources.configuration.locales[0]
