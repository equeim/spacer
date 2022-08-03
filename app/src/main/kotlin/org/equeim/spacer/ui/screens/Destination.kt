package org.equeim.spacer.ui.screens

import android.os.Parcelable
import androidx.compose.runtime.Composable

interface Destination : Parcelable {
    @Composable
    fun Content()
}
