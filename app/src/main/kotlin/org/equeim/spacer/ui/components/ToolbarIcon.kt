// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarIcon(icon: ImageVector, @StringRes textId: Int, onClick: () -> Unit) {
    val text = stringResource(textId)
    PlainTooltipBox(tooltip = { Text(text) }) {
        IconButton(onClick, Modifier.tooltipAnchor()) {
            Icon(icon, text)
        }
    }
}
