// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
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
        val application: Application? = this.application
        if (application != null) return application
    }
    return applicationContext.findInstanceOf()
        ?: throw IllegalStateException("Failed to retrieve Application instance from context $this")
}

private inline fun <reified T : Context> Context.findInstanceOf(): T? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is T) return context
        context = context.baseContext
    }
    return null
}
