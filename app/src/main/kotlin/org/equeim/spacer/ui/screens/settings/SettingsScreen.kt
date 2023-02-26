// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import android.os.Build
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.DialogNavHost
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.AppSettings
import org.equeim.spacer.R
import org.equeim.spacer.ui.LocalAppSettings
import org.equeim.spacer.ui.LocalNavController
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.components.RadioButtonListItem
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.collectAsStateWhenStarted

@Parcelize
object SettingsScreen : Destination {
    @Composable
    override fun Content() = SettingsScreen()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
private fun SettingsScreen() {
    val dialogNavController =
        rememberNavController<Destination>(initialBackstack = emptyList())
    CompositionLocalProvider(LocalNavController provides dialogNavController) {
        DialogNavHost(dialogNavController) {
            it.Content()
        }
    }

    Scaffold(topBar = {
        SubScreenTopAppBar(stringResource(R.string.settings))
    }) { contentPadding ->
        val model = viewModel<SettingsScreenViewModel>()
        if (!model.loaded) {
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(vertical = Dimens.ScreenContentPadding)
                .consumeWindowInsets(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            SectionHeader(
                stringResource(R.string.appearance),
                Modifier.padding(horizontal = Dimens.ScreenContentPadding),
                topPadding = Dimens.SpacingSmall
            )

            val darkThemeMode by model.darkThemeMode.collectAsStateWhenStarted()
            ListItem(
                headlineText = { Text(stringResource(R.string.dark_theme)) },
                supportingText = {
                    Text(
                        when (darkThemeMode) {
                            AppSettings.DarkThemeMode.FollowSystem -> stringResource(R.string.dark_theme_follow_system)
                            AppSettings.DarkThemeMode.On -> stringResource(R.string.preference_on)
                            AppSettings.DarkThemeMode.Off -> stringResource(R.string.preference_off)
                        }
                    )
                },
                modifier = Modifier.clickable {
                    if (dialogNavController.backstack.entries.lastOrNull()?.destination !is DarkThemeDialog) {
                        dialogNavController.navigate(DarkThemeDialog(darkThemeMode))
                    }
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val useSystemColors by model.useSystemColors.collectAsStateWhenStarted()
                ListItem(
                    headlineText = { Text(stringResource(R.string.use_system_colors)) },
                    trailingContent = { Switch(useSystemColors, onCheckedChange = null) },
                    modifier = Modifier.clickable {
                        model.settings.useSystemColors.set(!useSystemColors)
                    }
                )
            }

            SectionHeader(
                stringResource(R.string.behaviour),
                Modifier.padding(horizontal = Dimens.ScreenContentPadding)
            )

            val displayEventsTimeInUTC by model.displayEventsTimeInUTC.collectAsStateWhenStarted()
            ListItem(
                headlineText = { Text(stringResource(R.string.display_events_in_utc)) },
                trailingContent = { Switch(displayEventsTimeInUTC, onCheckedChange = null) },
                modifier = Modifier.clickable {
                    model.settings.displayEventsTimeInUTC.set(!displayEventsTimeInUTC)
                }
            )
        }
    }
}

@Parcelize
private data class DarkThemeDialog(val darkThemeMode: AppSettings.DarkThemeMode) : Destination {
    @Composable
    override fun Content() = DarkThemeDialogContent(darkThemeMode)
}

@Composable
private fun DarkThemeDialogContent(darkThemeMode: AppSettings.DarkThemeMode) {
    Dialog(title = stringResource(R.string.dark_theme)) {
        Column {
            if (AppSettings.DarkThemeMode.isFollowSystemSupported) {
                DarkThemeModeChoice(AppSettings.DarkThemeMode.FollowSystem, darkThemeMode)
            }
            DarkThemeModeChoice(AppSettings.DarkThemeMode.On, darkThemeMode)
            DarkThemeModeChoice(AppSettings.DarkThemeMode.Off, darkThemeMode)
        }
    }
}

@Composable
private fun DarkThemeModeChoice(
    darkThemeMode: AppSettings.DarkThemeMode,
    initialDarkThemeMode: AppSettings.DarkThemeMode
) {
    val settings = LocalAppSettings.current
    val navController = LocalNavController.current
    RadioButtonListItem(
        text = stringResource(
            when (darkThemeMode) {
                AppSettings.DarkThemeMode.FollowSystem -> R.string.dark_theme_follow_system
                AppSettings.DarkThemeMode.On -> R.string.preference_on
                AppSettings.DarkThemeMode.Off -> R.string.preference_off
            }
        ),
        selected = initialDarkThemeMode == darkThemeMode,
        onClick = {
            settings.darkThemeMode.set(darkThemeMode)
            navController.pop()
        },
        Modifier.padding(horizontal = Dimens.DialogContentPadding - Dimens.ScreenContentPadding)
    )
}
