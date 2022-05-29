package org.equeim.spacer.ui.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration

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

val Context.nightMode: Int
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

val Context.isNightModeActive: Boolean
    get() = nightMode == Configuration.UI_MODE_NIGHT_YES
