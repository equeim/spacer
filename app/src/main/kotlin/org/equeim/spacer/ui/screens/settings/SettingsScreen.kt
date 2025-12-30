// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import android.os.Build
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.AppSettings.DarkThemeMode
import org.equeim.spacer.R
import org.equeim.spacer.ui.components.ComboBox
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.components.SwitchWithText
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.utils.safeOpenUri

@Parcelize
object SettingsScreen : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) = SettingsScreen(navController::pop)
}

@Composable
private fun SettingsScreen(popBackStack: () -> Unit) {
    Scaffold(topBar = {
        SubScreenTopAppBar(stringResource(R.string.settings), popBackStack)
    }) { contentPadding ->
        val model = viewModel<SettingsScreenViewModel>()
        val notificationsSettingsScreenViewModel = viewModel<DonkiNotificationsSettingsScreenViewModel>()
        LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) { notificationsSettingsScreenViewModel.onActivityResumed() }
        if (!model.loaded || !notificationsSettingsScreenViewModel.loadedSettings.value) {
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
            val horizontalPadding = Dimens.ScreenContentPaddingHorizontal()

            SectionHeader(
                stringResource(R.string.appearance),
                Modifier.padding(horizontal = horizontalPadding),
                topPadding = 0.dp
            )

            val darkThemeMode: DarkThemeMode by model.darkThemeMode.collectAsStateWithLifecycle()
            ComboBox(
                currentItem = { darkThemeMode },
                updateCurrentItem = { model.settings.darkThemeMode.set(it) },
                items = if (DarkThemeMode.isFollowSystemSupported) {
                    DarkThemeMode.entries
                } else {
                    DarkThemeMode.entries - DarkThemeMode.FollowSystem
                },
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            DarkThemeMode.FollowSystem -> R.string.dark_theme_follow_system
                            DarkThemeMode.On -> R.string.preference_on
                            DarkThemeMode.Off -> R.string.preference_off
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                label = R.string.dark_theme
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val useSystemColors: Boolean by model.useSystemColors.collectAsStateWithLifecycle()
                SwitchWithText(
                    checked = useSystemColors,
                    onCheckedChange = { model.settings.useSystemColors.set(!useSystemColors) },
                    text = R.string.use_system_colors,
                    horizontalContentPadding = horizontalPadding,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionHeader(
                stringResource(R.string.behaviour),
                Modifier.padding(horizontal = horizontalPadding)
            )

            val displayEventsTimeInUTC: Boolean by model.displayEventsTimeInUTC.collectAsStateWithLifecycle()
            SwitchWithText(
                checked = displayEventsTimeInUTC,
                onCheckedChange = { model.settings.displayEventsTimeInUTC.set(!displayEventsTimeInUTC) },
                text = R.string.display_events_in_utc,
                horizontalContentPadding = horizontalPadding,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(
                stringResource(R.string.connection),
                Modifier.padding(horizontal = horizontalPadding)
            )

            val useCustomApiKey: Boolean by model.useCustomApiKey.collectAsStateWithLifecycle()
            SwitchWithText(
                checked = useCustomApiKey,
                onCheckedChange = { model.settings.useCustomNasaApiKey.set(!useCustomApiKey) },
                text = R.string.custom_nasa_api_key,
                horizontalContentPadding = horizontalPadding,
                modifier = Modifier.fillMaxWidth()
            )

            val apiKeyIsBlank: Boolean by remember { derivedStateOf { model.apiKeyTextFieldContent.isBlank() } }
            OutlinedTextField(
                value = model.apiKeyTextFieldContent,
                onValueChange = model::setNasaApiKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                enabled = useCustomApiKey,
                label = { Text(stringResource(R.string.nasa_api_key)) },
                isError = apiKeyIsBlank
            )

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.safeOpenUri(GENERATE_NASA_API_KEY_URL) },
                modifier = Modifier.padding(horizontal = horizontalPadding),
                enabled = useCustomApiKey
            ) {
                Text(stringResource(R.string.generate_nasa_api_key))
            }

            SectionHeader(
                stringResource(R.string.notifications),
                Modifier.padding(horizontal = horizontalPadding)
            )

            DonkiNotificationsSettings(
                backgroundNotificationsEnabledTypes = notificationsSettingsScreenViewModel.backgroundNotificationsEnabledTypes.collectAsStateWithLifecycle(),
                setBackgroundNotificationsEnabledTypes = notificationsSettingsScreenViewModel::setBackgroundNotificationsEnabledTypes,
                backgroundNotificationsUpdateInterval = notificationsSettingsScreenViewModel.backgroundNotificationsUpdateInterval.collectAsStateWithLifecycle(),
                setBackgroundNotificationsUpdateInterval = notificationsSettingsScreenViewModel::setBackgroundNotificationsUpdateInterval,
                issues = notificationsSettingsScreenViewModel.issues,
            )

            HorizontalDivider()

            Text(
                text = model.rateLimit?.let { stringResource(R.string.rate_limit, it) }
                    ?: stringResource(R.string.rate_limit_unknown),
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
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
                modifier = Modifier.padding(horizontal = horizontalPadding)
            )
        }
    }
}

private const val GENERATE_NASA_API_KEY_URL = "https://api.nasa.gov/#signUp"
