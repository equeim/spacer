// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olshevski.navigation.reimagined.pop
import org.equeim.spacer.R
import org.equeim.spacer.ui.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreenTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    CenterAlignedTopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    TopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        navigationIcon = {
            val navController = LocalNavController.current
            IconButton(onClick = navController::pop) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.navigate_up))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    )
}
