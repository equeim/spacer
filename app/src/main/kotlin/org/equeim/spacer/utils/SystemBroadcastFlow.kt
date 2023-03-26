// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val TAG = "SystemBroadcastFlow"

fun Context.systemBroadcastFlow(intentFilter: IntentFilter): Flow<Intent> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive() called with: context = $context, intent = $intent")
            launch { send(intent) }
        }
    }
    registerReceiver(receiver, intentFilter)
    awaitClose { unregisterReceiver(receiver) }
}

fun Context.systemBroadcastFlow(action: String): Flow<Intent> = systemBroadcastFlow(IntentFilter(action))
