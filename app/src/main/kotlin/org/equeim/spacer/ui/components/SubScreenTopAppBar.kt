package org.equeim.spacer.ui.utils

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olshevski.navigation.reimagined.pop
import org.equeim.spacer.LocalNavController
import org.equeim.spacer.R

@Composable
fun SubScreenTopAppBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val navController = LocalNavController.current
    val contentPadding = AppBarDefaults.ContentPadding + PaddingValues(top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding())
    TopAppBar(
        backgroundColor = MaterialTheme.colors.surface,
        contentPadding = contentPadding
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.high
            ) {
                IconButton(
                    onClick = {
                        navController.pop()
                    },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.navigate_up))
                }
                Spacer(Modifier.width(32.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Row(
                    Modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}