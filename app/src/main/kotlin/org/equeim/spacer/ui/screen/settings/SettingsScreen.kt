package org.equeim.spacer.ui.screen.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.DialogNavHost
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.AppSettings
import org.equeim.spacer.LocalAppSettings
import org.equeim.spacer.LocalNavController
import org.equeim.spacer.R
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.components.RadioButtonListItem
import org.equeim.spacer.ui.screen.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.SubScreenTopAppBar
import org.equeim.spacer.ui.utils.collectAsStateWhenStarted
import org.equeim.spacer.ui.utils.plus

@Parcelize
object SettingsScreen : Destination {
    @Composable
    override fun Content() = SettingsScreen()
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
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
    }) {
        val model = viewModel<SettingsScreenViewModel>()
        if (!model.loaded) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    it + PaddingValues(
                        bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                    )
                )
        ) {
            Text(
                stringResource(R.string.appearance),
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(
                    horizontal = Dimens.ScreenContentPadding,
                    vertical = 16.dp
                )
            )
            val darkThemeMode by model.darkThemeMode.collectAsStateWhenStarted()
            ListItem(
                text = { Text(stringResource(R.string.dark_theme)) },
                secondaryText = {
                    Text(
                        when (darkThemeMode) {
                            AppSettings.DarkThemeMode.FollowSystem -> stringResource(R.string.dark_theme_follow_system)
                            AppSettings.DarkThemeMode.On -> stringResource(R.string.preference_on)
                            AppSettings.DarkThemeMode.Off -> stringResource(R.string.preference_off)
                        }
                    )
                },
                modifier = Modifier.clickable {
                    dialogNavController.navigate(DarkThemeDialog(darkThemeMode))
                }
            )

            Text(
                stringResource(R.string.behaviour),
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(
                    horizontal = Dimens.ScreenContentPadding,
                    vertical = 16.dp
                )
            )
            val displayEventsTimeInUTC by model.displayEventsTimeInUTC.collectAsStateWhenStarted()
            ListItem(
                text = { Text(stringResource(R.string.display_events_in_utc)) },
                trailing = { Switch(displayEventsTimeInUTC, onCheckedChange = null) },
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
    Dialog(
        title = stringResource(R.string.dark_theme),
        addHorizontalPadding = false
    ) {
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
        text = stringResource(when (darkThemeMode) {
            AppSettings.DarkThemeMode.FollowSystem -> R.string.dark_theme_follow_system
            AppSettings.DarkThemeMode.On -> R.string.preference_on
            AppSettings.DarkThemeMode.Off -> R.string.preference_off
        }),
        selected = initialDarkThemeMode == darkThemeMode,
        onClick = {
            settings.darkThemeMode.set(darkThemeMode)
            navController.pop()
        },
        Modifier.padding(horizontal = Dimens.DialogContentPadding - Dimens.ScreenContentPadding)
    )
}
