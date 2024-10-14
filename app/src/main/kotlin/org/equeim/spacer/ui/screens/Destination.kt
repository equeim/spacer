// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.DialogNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry

interface Destination : Parcelable {
    @Composable
    fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    )
}

@Composable
fun ScreenDestinationNavHost(navController: NavController<Destination>, modifier: Modifier = Modifier) {
    NavBackHandler(navController)
    AnimatedNavHost(navController, modifier) {
        it.Content(navController, hostEntries, parentNavHostEntries = null)
    }
}

@Composable
fun DialogDestinationNavHost(navController: NavController<Destination>, parentNavHostEntries: () -> List<NavHostEntry<Destination>>) {
    DialogNavHost(navController) {
        it.Content(navController, hostEntries, parentNavHostEntries())
    }
}

val List<NavHostEntry<Destination>>.current: NavHostEntry<Destination> get() = last()
val List<NavHostEntry<Destination>>.previous: NavHostEntry<Destination> get() = get(lastIndex - 1)
