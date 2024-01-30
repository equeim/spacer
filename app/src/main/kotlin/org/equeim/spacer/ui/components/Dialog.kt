// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import org.equeim.spacer.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dialog(
    title: String,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    BasicAlertDialog(onDismissRequest, modifier, properties) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(Modifier.padding(vertical = Dimens.DialogContentPadding)) {
                Text(
                    title,
                    modifier = Modifier.padding(horizontal = Dimens.DialogContentPadding),
                    color = AlertDialogDefaults.titleContentColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                Box(Modifier.padding(top = Dimens.SpacingLarge)) {
                    content()
                }
            }
        }
    }
}
