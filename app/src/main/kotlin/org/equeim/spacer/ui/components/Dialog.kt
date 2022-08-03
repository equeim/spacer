package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.olshevski.navigation.reimagined.pop
import org.equeim.spacer.ui.LocalNavController
import org.equeim.spacer.ui.theme.Dimens

@Composable
fun Dialog(
    title: String?,
    addHorizontalPadding: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val navController = LocalNavController.current
    Dialog(
        onDismissRequest = { navController.pop() }
    ) {
        Surface(shape = MaterialTheme.shapes.medium, elevation = 24.dp) {
            Column(Modifier.padding(vertical = Dimens.DialogContentPadding)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.h5,
                        modifier = Modifier.padding(
                            bottom = 16.dp,
                            start = Dimens.DialogContentPadding,
                            end = Dimens.DialogContentPadding
                        )
                    )
                }
                Box(
                    modifier = if (addHorizontalPadding) {
                        Modifier.padding(horizontal = Dimens.DialogContentPadding)
                            .then(modifier)
                    } else {
                        modifier
                    }
                ) {
                    content()
                }
            }
        }
    }
}
