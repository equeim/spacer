package org.equeim.spacer.ui.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.equeim.spacer.utils.systemBroadcastFlow
import java.util.*

private const val TAG = "DefaultLocaleFlow"

fun Context.defaultLocaleFlow(): Flow<Locale> = systemBroadcastFlow(Intent.ACTION_LOCALE_CHANGED)
    .map { Locale.getDefault() }
    .onStart { emit(Locale.getDefault()) }
    .conflate()
    .distinctUntilChanged()
