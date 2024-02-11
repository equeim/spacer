// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.TimeZone

fun Context.defaultTimeZoneFlow(): Flow<ZoneId> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            launch {
                val zone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    TimeZone.getTimeZone(requireNotNull(intent.getStringExtra(Intent.EXTRA_TIMEZONE))).toZoneId()
                } else {
                    ZoneId.systemDefault()
                }
                send(zone)
            }
        }
    }
    registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
    awaitClose { unregisterReceiver(receiver) }
}.onStart { emit(ZoneId.systemDefault()) }.conflate().distinctUntilChanged()
