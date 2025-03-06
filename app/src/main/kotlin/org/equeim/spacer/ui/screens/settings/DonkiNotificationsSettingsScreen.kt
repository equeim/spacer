// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.DonkiSystemNotificationsWorker
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.utils.safeStartActivity
import java.time.Duration

@Parcelize
object DonkiNotificationsSettingsScreen : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) =
        DonkiNotificationsSettingsScreen(navController)
}

@Composable
private fun DonkiNotificationsSettingsScreen(navController: NavController<Destination>) {
    val model = viewModel<DonkiNotificationsSettingsScreenViewModel>()
    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) { model.onActivityResumed() }
    if (model.loadedSettings.value) {
        DonkiNotificationsSettingsScreen(
            backgroundNotificationsEnabledTypes = model.backgroundNotificationsEnabledTypes.collectAsStateWithLifecycle(),
            setBackgroundNotificationsEnabledTypes = model::setBackgroundNotificationsEnabledTypes,
            backgroundNotificationsUpdateInterval = model.backgroundNotificationsUpdateInterval.collectAsStateWithLifecycle(),
            setBackgroundNotificationsUpdateInterval = model::setBackgroundNotificationsUpdateInterval,
            issues = model.issues,
            popBackStack = navController::pop
        )
    }
}

@Composable
private fun DonkiNotificationsSettingsScreen(
    backgroundNotificationsEnabledTypes: State<Set<NotificationType>>,
    setBackgroundNotificationsEnabledTypes: (Set<NotificationType>) -> Unit,
    backgroundNotificationsUpdateInterval: State<Duration>,
    setBackgroundNotificationsUpdateInterval: (Duration) -> Unit,
    issues: State<List<DonkiNotificationsSettingsScreenViewModel.Issue>>,
    popBackStack: () -> Unit
) {

    Scaffold(topBar = {
        SubScreenTopAppBar(stringResource(R.string.notifications_settings), popBackStack)
    }) { contentPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(vertical = Dimens.ScreenContentPaddingVertical())
                .consumeWindowInsets(contentPadding),
        ) {
            DonkiNotificationsSettings(
                backgroundNotificationsEnabledTypes,
                setBackgroundNotificationsEnabledTypes,
                backgroundNotificationsUpdateInterval,
                setBackgroundNotificationsUpdateInterval,
                issues
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DonkiNotificationsSettings(
    backgroundNotificationsEnabledTypes: State<Set<NotificationType>>,
    setBackgroundNotificationsEnabledTypes: (Set<NotificationType>) -> Unit,
    backgroundNotificationsUpdateInterval: State<Duration>,
    setBackgroundNotificationsUpdateInterval: (Duration) -> Unit,
    issues: State<List<DonkiNotificationsSettingsScreenViewModel.Issue>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenContentPaddingHorizontal()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
    ) {
        Column(Modifier
            .animateContentSize()
            .fillMaxWidth()) {
            val shownIssues = remember(backgroundNotificationsEnabledTypes, issues) {
                derivedStateOf {
                    if (backgroundNotificationsEnabledTypes.value.isNotEmpty()) issues.value else emptyList()
                }
            }
            for (issue in shownIssues.value) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.SpacingMedium),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SpacingLarge),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(stringResource(issue.text), style = MaterialTheme.typography.bodyMedium)
                        }
                        val context = LocalContext.current
                        Button(
                            onClick = { context.safeStartActivity(issue.actionIntent) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(issue.actionText))
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.show_system_notifications_types))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
            verticalArrangement = Arrangement.Center,
        ) {
            val allTypesSelected: Boolean by remember {
                derivedStateOf {
                    backgroundNotificationsEnabledTypes.value.size == NotificationType.entries.size
                }
            }
            FilterChip(
                selected = allTypesSelected,
                onClick = {
                    val newTypes = if (allTypesSelected) {
                        emptySet()
                    } else {
                        NotificationType.entries.toSet()
                    }
                    setBackgroundNotificationsEnabledTypes(newTypes)
                },
                label = {
                    Text(stringResource(R.string.all_event_types))
                }
            )
            for (type in NotificationType.entries) {
                val typeSelected: Boolean by remember {
                    derivedStateOf { backgroundNotificationsEnabledTypes.value.contains(type) }
                }
                FilterChip(
                    selected = typeSelected,
                    onClick = {
                        val newTypes = backgroundNotificationsEnabledTypes.value.toMutableSet()
                        if (typeSelected) newTypes.remove(type) else newTypes.add(type)
                        setBackgroundNotificationsEnabledTypes(newTypes)
                    },
                    label = {
                        Text(stringResource(type.displayStringResId))
                    }
                )
            }
        }

        val expanded = remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded.value,
            onExpandedChange = {
                expanded.value = it
            },
        ) {
            val currentValue = remember { mutableStateOf(backgroundNotificationsUpdateInterval.value) }
            val enabled = remember { derivedStateOf { backgroundNotificationsEnabledTypes.value.isNotEmpty() } }
            TextField(
                value = updateIntervalToString(currentValue.value),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.notifications_background_update_interval)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded.value) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled.value),
                enabled = enabled.value,
            )
            ExposedDropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false }
            ) {
                for (interval in DonkiSystemNotificationsWorker.INTERVALS) {
                    DropdownMenuItem(
                        text = { Text(updateIntervalToString(interval)) },
                        onClick = {
                            currentValue.value = interval
                            expanded.value = false
                            setBackgroundNotificationsUpdateInterval(interval)
                        },
                        leadingIcon = { RadioButton(selected = (interval == currentValue.value), onClick = null) }
                    )
                }
            }
        }

        Text(stringResource(R.string.update_interval_warning), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Composable
private fun NotificationsSettingsPreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        DonkiNotificationsSettings(
            backgroundNotificationsEnabledTypes = remember { mutableStateOf(setOf(NotificationType.GeomagneticStorm)) },
            setBackgroundNotificationsEnabledTypes = {},
            backgroundNotificationsUpdateInterval = remember { mutableStateOf(DonkiSystemNotificationsWorker.DEFAULT_INTERVAL) },
            setBackgroundNotificationsUpdateInterval = {},
            issues = remember { mutableStateOf(emptyList()) },
        )
    }
}

@Preview
@Composable
private fun NotificationsSettingsScreenPreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        DonkiNotificationsSettingsScreen(
            backgroundNotificationsEnabledTypes = remember { mutableStateOf(setOf(NotificationType.GeomagneticStorm)) },
            setBackgroundNotificationsEnabledTypes = {},
            backgroundNotificationsUpdateInterval = remember { mutableStateOf(DonkiSystemNotificationsWorker.DEFAULT_INTERVAL) },
            setBackgroundNotificationsUpdateInterval = {},
            issues = remember { mutableStateOf(emptyList()) },
            popBackStack = {}
        )
    }
}

@Composable
private fun updateIntervalToString(interval: Duration): String {
    interval.toHours().takeIf { it > 0 }?.let { hours ->
        return pluralStringResource(R.plurals.duration_hours, hours.toInt(), hours)
    }
    val minutes = interval.toMinutes()
    return pluralStringResource(R.plurals.duration_minutes, minutes.toInt(), minutes)
}
