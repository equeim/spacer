package org.equeim.spacer.ui.utils

import android.content.Context
import android.content.res.Configuration

val Context.nightMode: Int
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

val Context.isNightModeActive: Boolean
    get() = nightMode == Configuration.UI_MODE_NIGHT_YES
