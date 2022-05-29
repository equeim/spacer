package org.equeim.spacer.ui.screen

import android.os.Parcelable
import androidx.compose.runtime.Composable

interface Destination : Parcelable {
    @Composable
    fun Content()
}
