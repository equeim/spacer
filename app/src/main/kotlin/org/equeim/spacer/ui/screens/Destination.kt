// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens

import android.os.Parcelable
import androidx.compose.runtime.Composable

interface Destination : Parcelable {
    @Composable
    fun Content()
}
