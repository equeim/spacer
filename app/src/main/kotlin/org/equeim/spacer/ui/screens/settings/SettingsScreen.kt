// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import dev.olshevski.navigation.reimagined.rememberNavController
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.AppSettings
import org.equeim.spacer.R
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.components.RadioButtonListItem
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.DialogDestinationNavHost
import org.equeim.spacer.ui.screens.current
import org.equeim.spacer.ui.theme.Dimens

@Parcelize
object SettingsScreen : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) =
        SettingsScreen(navController, navHostEntries = { navHostEntries })
}

@Composable
private fun SettingsScreen(navController: NavController<Destination>, navHostEntries: () -> List<NavHostEntry<Destination>>) {
    val dialogNavController =
        rememberNavController<Destination>(initialBackstack = emptyList())
    DialogDestinationNavHost(dialogNavController, navHostEntries)

    Scaffold(topBar = {
        SubScreenTopAppBar(stringResource(R.string.settings), navController::pop)
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
                .padding(vertical = Dimens.ScreenContentPaddingVertical())
                .consumeWindowInsets(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            SectionHeader(
                stringResource(R.string.appearance),
                Modifier.padding(horizontal = Dimens.ScreenContentPaddingHorizontal()),
                topPadding = 0.dp
            )

            val listItemHorizontalPadding =
                (Dimens.ScreenContentPaddingHorizontal() - LIST_ITEM_HORIZONTAL_PADDING)
                    .coerceAtLeast(0.dp)

            val darkThemeMode: AppSettings.DarkThemeMode by model.darkThemeMode.collectAsStateWithLifecycle()
            ListItem(
                headlineContent = { Text(stringResource(R.string.dark_theme)) },
                supportingContent = {
                    Text(
                        when (darkThemeMode) {
                            AppSettings.DarkThemeMode.FollowSystem -> stringResource(R.string.dark_theme_follow_system)
                            AppSettings.DarkThemeMode.On -> stringResource(R.string.preference_on)
                            AppSettings.DarkThemeMode.Off -> stringResource(R.string.preference_off)
                        }
                    )
                },
                modifier = Modifier
                    .clickable {
                        if (dialogNavController.backstack.entries.lastOrNull()?.destination !is DarkThemeDialog) {
                            dialogNavController.navigate(DarkThemeDialog(darkThemeMode))
                        }
                    }
                    .padding(horizontal = listItemHorizontalPadding)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val useSystemColors: Boolean by model.useSystemColors.collectAsStateWithLifecycle()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.use_system_colors)) },
                    trailingContent = { Switch(useSystemColors, onCheckedChange = null) },
                    modifier = Modifier
                        .clickable {
                            model.settings.useSystemColors.set(!useSystemColors)
                        }
                        .padding(horizontal = listItemHorizontalPadding)
                )
            }

            SectionHeader(
                stringResource(R.string.behaviour),
                Modifier.padding(horizontal = Dimens.ScreenContentPaddingHorizontal())
            )

            val displayEventsTimeInUTC: Boolean by model.displayEventsTimeInUTC.collectAsStateWithLifecycle()
            ListItem(
                headlineContent = { Text(stringResource(R.string.display_events_in_utc)) },
                trailingContent = { Switch(displayEventsTimeInUTC, onCheckedChange = null) },
                modifier = Modifier
                    .clickable {
                        model.settings.displayEventsTimeInUTC.set(!displayEventsTimeInUTC)
                    }
                    .padding(horizontal = listItemHorizontalPadding)
            )

            SectionHeader(
                stringResource(R.string.connection),
                Modifier.padding(horizontal = Dimens.ScreenContentPaddingHorizontal())
            )

            val useCustomApiKey: Boolean by model.useCustomApiKey.collectAsStateWithLifecycle()
            ListItem(
                headlineContent = { Text(stringResource(R.string.custom_nasa_api_key)) },
                trailingContent = { Switch(useCustomApiKey, onCheckedChange = null) },
                modifier = Modifier
                    .clickable {
                        model.settings.useCustomNasaApiKey.set(!useCustomApiKey)
                    }
                    .padding(horizontal = listItemHorizontalPadding)
            )

            val apiKeyIsBlank: Boolean by remember { derivedStateOf { model.apiKeyTextFieldContent.isBlank() } }
            OutlinedTextField(
                value = model.apiKeyTextFieldContent,
                onValueChange = model::setNasaApiKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenContentPaddingHorizontal()),
                enabled = useCustomApiKey,
                label = { Text(stringResource(R.string.nasa_api_key)) },
                isError = apiKeyIsBlank
            )

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri(GENERATE_NASA_API_KEY_URL) },
                modifier = Modifier.padding(horizontal = Dimens.ScreenContentPaddingHorizontal()),
                enabled = useCustomApiKey
            ) {
                Text(stringResource(R.string.generate_nasa_api_key))
            }

            HorizontalDivider()

            Text(
                text = model.rateLimit?.let { stringResource(R.string.rate_limit, it) }
                    ?: stringResource(R.string.rate_limit_unknown),
                modifier = Modifier
                    .padding(horizontal = Dimens.ScreenContentPaddingHorizontal())
                    .padding(top = Dimens.SpacingSmall)
            )

            Text(
                text = model.remainingRequests?.let {
                    stringResource(
                        R.string.remaining_requests,
                        it
                    )
                }
                    ?: stringResource(R.string.remaining_requests_unknown),
                modifier = Modifier.padding(horizontal = Dimens.ScreenContentPaddingHorizontal())
            )
        }
    }
}

@Parcelize
private data class DarkThemeDialog(val darkThemeMode: AppSettings.DarkThemeMode) : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) {
        val model = viewModel<SettingsScreenViewModel>(viewModelStoreOwner = parentNavHostEntries!!.current)
        DarkThemeDialogContent(
            darkThemeMode,
            onDismissRequest = navController::pop,
            setDarkThemeMode = {
                model.settings.darkThemeMode.set(it)
                navController.pop()
            }
        )
    }
}

@Composable
private fun DarkThemeDialogContent(
    darkThemeMode: AppSettings.DarkThemeMode,
    onDismissRequest: () -> Unit,
    setDarkThemeMode: (AppSettings.DarkThemeMode) -> Unit,
) {
    Dialog(title = stringResource(R.string.dark_theme), onDismissRequest = onDismissRequest) {
        Column {
            if (AppSettings.DarkThemeMode.isFollowSystemSupported) {
                DarkThemeModeChoice(
                    AppSettings.DarkThemeMode.FollowSystem,
                    darkThemeMode,
                    setDarkThemeMode
                )
            }
            DarkThemeModeChoice(AppSettings.DarkThemeMode.On, darkThemeMode, setDarkThemeMode)
            DarkThemeModeChoice(AppSettings.DarkThemeMode.Off, darkThemeMode, setDarkThemeMode)
        }
    }
}

@Composable
private fun DarkThemeModeChoice(
    darkThemeMode: AppSettings.DarkThemeMode,
    initialDarkThemeMode: AppSettings.DarkThemeMode,
    setDarkThemeMode: (AppSettings.DarkThemeMode) -> Unit,
) {
    RadioButtonListItem(
        text = stringResource(
            when (darkThemeMode) {
                AppSettings.DarkThemeMode.FollowSystem -> R.string.dark_theme_follow_system
                AppSettings.DarkThemeMode.On -> R.string.preference_on
                AppSettings.DarkThemeMode.Off -> R.string.preference_off
            }
        ),
        selected = initialDarkThemeMode == darkThemeMode,
        onClick = { setDarkThemeMode(darkThemeMode) },
        modifier = Modifier.padding(horizontal = Dimens.listItemHorizontalPadding(Dimens.DialogContentPadding)),
        containerColor = Color.Transparent
    )
}

// Horizontal padding that ListItem hardcodes
private val LIST_ITEM_HORIZONTAL_PADDING = 16.dp

private const val GENERATE_NASA_API_KEY_URL = "https://api.nasa.gov/#signUp"
