// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.UriHandler

fun Context.safeStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "safeStartActivity: failed to start activity for intent $intent", e)
    }
}

fun <T> ActivityResultLauncher<T>.safeLaunch(input: T) {
    try {
        launch(input)
    } catch (e: Exception) {
        Log.e(TAG, "safeLaunch: failed to start activity for input $input", e)
    }
}

fun UriHandler.safeOpenUri(uri: String) {
    try {
        openUri(uri)
    } catch (e: Exception) {
        Log.e(TAG, "safeOpenUri: failed to start activity for URI $uri", e)
    }
}

private const val TAG = "SafeStartActivity"
