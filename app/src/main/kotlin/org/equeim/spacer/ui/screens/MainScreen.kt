// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.DefaultShortNavigationBarOverride
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalShortNavigationBarOverride
import androidx.compose.material3.ShortNavigationBarOverride
import androidx.compose.material3.ShortNavigationBarOverrideScope
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreen
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreen
import org.equeim.spacer.ui.theme.Timeline

@Parcelize
object MainScreen : Destination {
    @Composable
    override fun Content(navController: NavController) = MainScreenContent(navController)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ComponentOverrideApi::class)
@Composable
private fun MainScreenContent(navController: NavController) {
    var currentScreen: BottomNavigationScreen by rememberSaveable { mutableStateOf(BottomNavigationScreen.Events) }

    val viewModel = viewModel<MainScreenViewModel>()
    val numberOfUnreadNotifications = viewModel.numberOfUnreadNotifications.collectAsStateWithLifecycle()

    val bottomAppBarScrollBehavior = NavigationBarOverride.scrollBehavior()
    val navigationBarOverride =
        remember(bottomAppBarScrollBehavior) { NavigationBarOverride(bottomAppBarScrollBehavior) }

    val scrollToTopEvents =
        remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    CompositionLocalProvider(LocalShortNavigationBarOverride provides navigationBarOverride) {
        NavigationSuiteScaffold(
            navigationItems = {
                for (screen in BottomNavigationScreen.entries) {
                    NavigationSuiteItem(
                        navigationSuiteType = navigationSuiteType,
                        selected = screen == currentScreen,
                        onClick = {
                            if (currentScreen != screen) {
                                currentScreen = screen
                            } else {
                                scrollToTopEvents.tryEmit(Unit)
                            }
                        },
                        icon = {
                            when (screen) {
                                BottomNavigationScreen.Events ->
                                    Icon(Icons.Filled.Timeline, stringResource(R.string.events))

                                BottomNavigationScreen.Notifications ->
                                    Icon(Icons.Filled.Notifications, stringResource(R.string.notifications))
                            }
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (screen) {
                                        BottomNavigationScreen.Events -> R.string.events
                                        BottomNavigationScreen.Notifications -> R.string.notifications
                                    }
                                )
                            )
                        },
                        badge = if (screen == BottomNavigationScreen.Notifications && numberOfUnreadNotifications.value > 0) {
                            { Badge { Text(numberOfUnreadNotifications.value.toString()) } }
                        } else {
                            null
                        }
                    )
                }
            }
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) }
            ) { screen ->
                when (screen) {
                    BottomNavigationScreen.Events -> DonkiEventsScreen(
                        navController,
                        bottomAppBarScrollBehavior,
                        scrollToTopEvents
                    )

                    BottomNavigationScreen.Notifications -> DonkiNotificationsScreen(
                        navController,
                        bottomAppBarScrollBehavior,
                        scrollToTopEvents
                    )
                }
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ComponentOverrideApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
private class NavigationBarOverride(
    private val scrollBehavior: BottomAppBarScrollBehavior
) : ShortNavigationBarOverride {
    @Composable
    override fun ShortNavigationBarOverrideScope.ShortNavigationBar() {
        // We use BottomAppBar as just a container for ShortNavigationBar, to handle scrollBehavior
        BottomAppBar(
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            tonalElevation = 0.dp,
            contentPadding = PaddingValues(),
            windowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
            scrollBehavior = scrollBehavior,
        ) {
            with(DefaultShortNavigationBarOverride) {
                ShortNavigationBar()
            }
        }
    }

    companion object {
        @Composable
        fun scrollBehavior(): BottomAppBarScrollBehavior {
            val delegate = BottomAppBarDefaults.exitAlwaysScrollBehavior()
            return object : BottomAppBarScrollBehavior by delegate {
                override val isPinned: Boolean get() = true
            }
        }
    }
}

private enum class BottomNavigationScreen {
    Events,
    Notifications
}
