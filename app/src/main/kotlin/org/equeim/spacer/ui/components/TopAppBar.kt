package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.olshevski.navigation.reimagined.pop
import org.equeim.spacer.LocalNavController
import org.equeim.spacer.R
import org.equeim.spacer.ui.utils.plus

@Composable
fun RootScreenTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    elevation: Dp = AppBarDefaults.TopAppBarElevation,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    TopAppBarImpl(modifier, elevation) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.high
            ) {
                Text(
                    title,
                    Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.h6
                )
            }
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Row(
                    Modifier.fillMaxHeight().align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun SubScreenTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    elevation: Dp = AppBarDefaults.TopAppBarElevation,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    TopAppBarImpl(modifier, elevation) {
        CompositionLocalProvider(
            LocalContentAlpha provides ContentAlpha.high
        ) {
            val navController = LocalNavController.current
            IconButton(navController::pop) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.navigate_up))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.h6
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

@Composable
private fun TopAppBarImpl(
    modifier: Modifier,
    elevation: Dp,
    content: @Composable RowScope.() -> Unit
) {
    val contentPadding = AppBarDefaults.ContentPadding + PaddingValues(
        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    )
    TopAppBar(
        modifier = modifier,
        backgroundColor = MaterialTheme.colors.surface,
        elevation = elevation,
        contentPadding = contentPadding,
        content = content
    )
}
