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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.asIntState
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
private fun MainScreenContent(navController: NavController) {
    val currentScreen = rememberSaveable { mutableStateOf(BottomNavigationScreen.Events) }

    val viewModel = viewModel<MainScreenViewModel>()
    val numberOfUnreadNotifications = viewModel.numberOfUnreadNotifications.collectAsStateWithLifecycle().asIntState()

    val scrollToTopEvents =
        remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    val bottomNavigationBarScrollBehaviour = createBottomNavigationBarScrollBehaviour()

    Surface(
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor
    ) {
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                ScrollableNavigationSuite(
                    bottomNavigationBarScrollBehaviour = bottomNavigationBarScrollBehaviour,
                ) { navigationSuiteType ->
                    NavigationItems(
                        navigationSuiteType = navigationSuiteType,
                        currentScreen = currentScreen,
                        numberOfUnreadNotifications = numberOfUnreadNotifications,
                        scrollToTop = { scrollToTopEvents.tryEmit(Unit) }
                    )
                }
            }
        ) {
            AnimatedContent(
                targetState = currentScreen.value,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) }
            ) { screen ->
                when (screen) {
                    BottomNavigationScreen.Events -> DonkiEventsScreen(
                        navController,
                        bottomNavigationBarScrollBehaviour,
                        scrollToTopEvents
                    )

                    BottomNavigationScreen.Notifications -> DonkiNotificationsScreen(
                        navController,
                        bottomNavigationBarScrollBehaviour,
                        scrollToTopEvents
                    )
                }
            }
        }
    }
}

@Composable
private fun createBottomNavigationBarScrollBehaviour(): BottomAppBarScrollBehavior {
    val delegate = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    return remember(delegate) {
        object : BottomAppBarScrollBehavior by delegate {
            override val isPinned: Boolean get() = true
        }
    }
}

@Composable
private fun ScrollableNavigationSuite(
    bottomNavigationBarScrollBehaviour: BottomAppBarScrollBehavior,
    content: @Composable (NavigationSuiteType) -> Unit,
) {
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

    val suite: @Composable () -> Unit = {
        NavigationSuite(
            navigationSuiteType = navigationSuiteType,
            content = { content(navigationSuiteType) },
        )
    }
    val movableSuite = remember(suite) { movableContentOf(suite) }
    when (navigationSuiteType) {
        NavigationSuiteType.ShortNavigationBarCompact,
        NavigationSuiteType.ShortNavigationBarMedium,
        NavigationSuiteType.NavigationBar -> BottomAppBar(
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            tonalElevation = 0.dp,
            contentPadding = PaddingValues(),
            windowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
            scrollBehavior = bottomNavigationBarScrollBehaviour,
        ) {
            movableSuite()
        }

        else -> movableSuite()
    }
}

@Composable
private fun NavigationItems(
    navigationSuiteType: NavigationSuiteType,
    currentScreen: MutableState<BottomNavigationScreen>,
    numberOfUnreadNotifications: IntState,
    scrollToTop: () -> Unit,
) {
    for (screen in BottomNavigationScreen.entries) {
        NavigationSuiteItem(
            navigationSuiteType = navigationSuiteType,
            selected = screen == currentScreen.value,
            onClick = {
                if (currentScreen.value != screen) {
                    currentScreen.value = screen
                } else {
                    scrollToTop()
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
            badge = if (screen == BottomNavigationScreen.Notifications && numberOfUnreadNotifications.intValue > 0) {
                { Badge { Text(numberOfUnreadNotifications.intValue.toString()) } }
            } else {
                null
            }
        )
    }
}

private enum class BottomNavigationScreen {
    Events,
    Notifications
}
