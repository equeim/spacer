// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconButtonWithTooltip(icon: ImageVector, @StringRes textId: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BaseButtonWithTooltip(textId, modifier) { text ->
        IconButton(onClick) {
            Icon(icon, text)
        }
    }
}
