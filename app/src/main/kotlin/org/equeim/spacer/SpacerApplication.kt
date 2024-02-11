// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer

import android.app.AppOpsManager
import android.app.Application
import android.app.AsyncNotedAppOp
import android.app.SyncNotedAppOp
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.core.content.getSystemService

private const val TAG = "SpacerApplication"

class SpacerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build()
        )
        if (Build.VERSION.SDK_INT >= 30) {
            val appOpsManager = getSystemService<AppOpsManager>()
            appOpsManager?.setOnOpNotedCallback(mainExecutor, object : AppOpsManager.OnOpNotedCallback() {
                override fun onNoted(op: SyncNotedAppOp) {
                    Log.d(TAG, "onNoted() called with: op = $op")
                }

                override fun onSelfNoted(op: SyncNotedAppOp) {
                    Log.d(TAG, "onSelfNoted() called with: op = $op")
                }

                override fun onAsyncNoted(asyncOp: AsyncNotedAppOp) {
                    Log.d(TAG, "onAsyncNoted() called with: asyncOp = $asyncOp")
                }
            })
        }
    }
}