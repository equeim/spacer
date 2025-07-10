// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.equeim.spacer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ComboBox(
    currentItem: () -> T,
    updateCurrentItem: (T) -> Unit,
    items: List<T>,
    itemDisplayString: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes label: Int = 0
) {
    var expanded: Boolean by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.width(IntrinsicSize.Min)
    ) {
        OutlinedTextField(
            value = itemDisplayString(currentItem()),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            enabled = enabled,
            readOnly = true,
            label = label.takeIf { it != 0 }?.let { { Text(stringResource(label)) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (item in items) {
                val selected = (item == currentItem())
                DropdownMenuItem(
                    text = { Text(itemDisplayString(item)) },
                    onClick = {
                        updateCurrentItem(item)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    leadingIcon = if (selected) {
                        {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected))
                        }
                    } else {
                        null
                    },
                    colors = if (selected) {
                        val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        MenuDefaults.itemColors(
                            textColor = contentColor,
                            leadingIconColor = contentColor,
                            trailingIconColor = contentColor
                        )
                    } else {
                        MenuDefaults.itemColors()
                    },
                    modifier = if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}
