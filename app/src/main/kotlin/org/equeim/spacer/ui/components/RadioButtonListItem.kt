// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioButtonListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null
            )
        },
        headlineText = { Text(text = text) },
        modifier = Modifier.clickable(onClick = onClick).then(modifier)
    )
}
