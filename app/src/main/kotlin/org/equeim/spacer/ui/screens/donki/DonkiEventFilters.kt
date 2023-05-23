// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMaterial3Api::class)

package org.equeim.spacer.ui.screens.donki

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens

@Parcelize
object DonkiEventFilters : Destination {
    @Composable
    override fun Content(navController: NavController<Destination>, parentNavHostEntry: NavHostEntry<Destination>?) {
        val model = viewModel<DonkiEventsScreenViewModel>(checkNotNull(parentNavHostEntry))
        val filters by model.filters.collectAsState()
        DonkiEventFiltersDialog(
            { filters },
            model.filters::value::set,
            navController::pop
        )
    }
}

@Composable
private fun DonkiEventFiltersDialog(
    filters: () -> DonkiRepository.EventFilters,
    updateFilters: (DonkiRepository.EventFilters) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(title = stringResource(R.string.filters), onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DonkiEventFilters(
            showTitle = false,
            modifier = Modifier.padding(horizontal = Dimens.DialogContentPadding),
            filters,
            updateFilters
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DonkiEventFilters(
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    filters: () -> DonkiRepository.EventFilters,
    updateFilters: (DonkiRepository.EventFilters) -> Unit,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        if (showTitle) {
            Text(
                stringResource(R.string.filters),
                Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Text(
            stringResource(R.string.event_types),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allTypesSelected by remember {
                derivedStateOf {
                    filters().types.containsAll(EventType.All)
                }
            }
            EventTypeChip(R.string.all_event_types, allTypesSelected) {
                val newTypes = if (allTypesSelected) {
                    emptySet()
                } else {
                    EventType.All.toSet()
                }
                updateFilters(filters().copy(types = newTypes))
            }
            for (type in EventType.All) {
                val typeSelected by remember { derivedStateOf { filters().types.contains(type) } }
                EventTypeChip(type.displayStringResId, typeSelected) {
                    updateFilters(filters().run {
                        val newTypes = if (typeSelected) types - type else types + type
                        copy(types = newTypes)
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTypeChip(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        colors = FilterChipDefaults.filterChipColors(
            iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Preview
@Composable
private fun DonkiEventFiltersPreview() {
    DonkiEventFilters(
        showTitle = true,
        filters = { DonkiRepository.EventFilters(types = EventType.All.toSet() - EventType.GeomagneticStorm) },
        updateFilters = {}
    )
}

@Preview
@Composable
private fun DonkiEventFiltersDialogPreview() {
    DonkiEventFiltersDialog(
        filters = { DonkiRepository.EventFilters(types = EventType.All.toSet() - EventType.GeomagneticStorm) },
        updateFilters = {},
        onDismissRequest = {}
    )
}
