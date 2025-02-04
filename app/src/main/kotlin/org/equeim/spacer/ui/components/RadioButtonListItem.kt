// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun RadioButtonListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ListItemDefaults.containerColor,
) {
    ListItem(
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null
            )
        },
        headlineContent = { Text(text = text) },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.clickable(onClick = onClick).then(modifier)
    )
}
