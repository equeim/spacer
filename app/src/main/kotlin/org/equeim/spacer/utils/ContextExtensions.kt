// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper

fun Context.getApplicationOrThrow(): Application {
    if (this is Application) return this
    if (this is Activity) {
        val application = runCatching { this.application }.getOrNull()
        if (application != null) return application
    }
    var applicationContext: Context? = this.applicationContext
    while (applicationContext is ContextWrapper) {
        if (applicationContext is Application) return applicationContext
        applicationContext = applicationContext.baseContext
    }
    throw IllegalStateException("Failed to retrieve Application instance from context $this")
}
