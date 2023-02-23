// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.olshevski.navigation.reimagined.pop
import org.equeim.spacer.ui.LocalNavController
import org.equeim.spacer.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dialog(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val navController = LocalNavController.current
    AlertDialog(onDismissRequest = navController::pop, modifier = modifier) {
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
