// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens

import android.os.Parcelable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.DialogNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.NavHostScope
import dev.olshevski.navigation.reimagined.currentHostEntry

interface Destination : Parcelable {
    @Composable
    fun Content(navController: NavController<Destination>, parentNavHostEntry: NavHostEntry<Destination>?)
}

val LocalNavController =
    staticCompositionLocalOf<NavController<Destination>> { throw IllegalStateException() }
private val LocalNavHostEntry = staticCompositionLocalOf<NavHostEntry<Destination>?> { null }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScreenDestinationNavHost(navController: NavController<Destination>, modifier: Modifier = Modifier) {
    val parentNavHostEntry = LocalNavHostEntry.current
    NavBackHandler(navController)
    AnimatedNavHost(navController, modifier) {
        NavHostContentSelector(navController, it, parentNavHostEntry)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DialogDestinationNavHost(navController: NavController<Destination>) {
    val parentNavHostEntry = LocalNavHostEntry.current
    DialogNavHost(navController) {
        NavHostContentSelector(navController, it, parentNavHostEntry)
    }
}

@Composable
private fun NavHostScope<Destination>.NavHostContentSelector(
    navController: NavController<Destination>,
    destination: Destination,
    parentNavHostEntry: NavHostEntry<Destination>?,
) {
    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalNavHostEntry provides currentHostEntry
    ) {
        destination.Content(navController, parentNavHostEntry)
    }
}
