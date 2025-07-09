// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.os.Parcelable
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.getSelectedEndDate
import androidx.compose.material3.getSelectedStartDate
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.navEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.isUTC
import org.equeim.spacer.ui.utils.plus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Parcelize
data class FiltersUiState<EventType : Enum<EventType>>(
    val types: List<EventType>,
    val dateRange: DateRange?,
    val dateRangeEnabled: Boolean,
) : Parcelable

@Composable
fun <EventType : Enum<EventType>> BaseEventFiltersSideSheet(
    contentPadding: PaddingValues = PaddingValues(),
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    allEventTypes: List<EventType>,
    eventTypeDisplayStringId: (EventType) -> Int,
    eventsTimeZone: State<ZoneId?>,
    showDateRangeDialog: () -> Unit
) {
    BaseEventFilters(
        Modifier
            .fillMaxHeight()
            .width(256.dp),
        contentPadding = contentPadding + Dimens.ScreenContentPadding(start = false),
        title = {
            Text(
                stringResource(R.string.filters),
                Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge
            )
        },
        filtersUiState = filtersUiState,
        updateFilters = updateFilters,
        allEventTypes = allEventTypes,
        eventTypeDisplayStringId = eventTypeDisplayStringId,
        eventsTimeZone = eventsTimeZone,
        showDateRangeDialog = showDateRangeDialog
    )
}

@Composable
fun <EventType : Enum<EventType>> BaseEventFiltersDialogContent(
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    allEventTypes: List<EventType>,
    eventTypeDisplayStringId: (EventType) -> Int,
    eventsTimeZone: State<ZoneId?>,
    closeDialog: () -> Unit,
    showDateRangeDialog: () -> Unit,
) {
    Dialog(
        title = stringResource(R.string.filters),
        onDismissRequest = closeDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BaseEventFilters(
            contentPadding = PaddingValues(horizontal = Dimens.DialogContentPadding),
            filtersUiState = filtersUiState,
            updateFilters = updateFilters,
            allEventTypes = allEventTypes,
            eventTypeDisplayStringId = eventTypeDisplayStringId,
            eventsTimeZone = eventsTimeZone,
            showDateRangeDialog = showDateRangeDialog,
        )
    }
}

@Composable
fun shouldShowFiltersAsDialog(): State<Boolean> {
    val windowSizeClass = rememberUpdatedState(currentWindowAdaptiveInfo().windowSizeClass)
    return remember {
        derivedStateOf {
            !windowSizeClass.value.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        }
    }
}

@Composable
fun HandleFiltersDialogVisibility(
    shouldShowFiltersAsDialog: State<Boolean>,
    dialogNavController: NavController<Destination>,
    filtersDialogDestination: Destination,
    dateRangePickerDialogDestination: Destination,
) {
    val shouldShowFiltersAsDialogFlow = remember { snapshotFlow { shouldShowFiltersAsDialog.value } }
    val showingFiltersDialog = remember(dialogNavController) {
        snapshotFlow { dialogNavController.backstack.entries.find { it.destination == filtersDialogDestination } != null }
    }
    val showingDateRangeDialog = remember(dialogNavController) {
        snapshotFlow { dialogNavController.backstack.entries.find { it.destination == dateRangePickerDialogDestination } != null }
    }
    LaunchedEffect(dialogNavController) {
        combineTransform(
            shouldShowFiltersAsDialogFlow,
            showingFiltersDialog,
            showingDateRangeDialog
        ) { shouldShowFiltersAsDialog, showingFiltersDialog, showingDateRangeDialog ->
            if (shouldShowFiltersAsDialog) {
                if (showingDateRangeDialog && !showingFiltersDialog) {
                    emit(true)
                }
            } else if (showingFiltersDialog) {
                emit(false)
            }
        }.collect { addFiltersDialogToBackStack ->
            dialogNavController.setNewBackstack(
                if (addFiltersDialogToBackStack) {
                    Log.d(TAG, "Adding filters dialog to back stack")
                    buildList {
                        add(navEntry(filtersDialogDestination))
                        addAll(dialogNavController.backstack.entries)
                    }
                } else {
                    Log.d(TAG, "Removing filters dialog from back stack")
                    dialogNavController.backstack.entries.filterNot { it.destination == filtersDialogDestination }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <EventType : Enum<EventType>> BaseEventFilters(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    title: @Composable ColumnScope.() -> Unit = {},
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    allEventTypes: List<EventType>,
    eventTypeDisplayStringId: (EventType) -> Int,
    eventsTimeZone: State<ZoneId?>,
    showDateRangeDialog: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier
            .verticalScroll(scrollState)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        val contentPaddingWithoutBottom: PaddingValues
        val horizontalContentPadding: PaddingValues
        LocalLayoutDirection.current.let {
            horizontalContentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(it),
                end = contentPadding.calculateEndPadding(it)
            )
            contentPaddingWithoutBottom =
                horizontalContentPadding + PaddingValues(top = contentPadding.calculateTopPadding())
        }

        Column(
            Modifier.padding(contentPaddingWithoutBottom),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            title()

            Text(
                stringResource(R.string.filter_types),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                verticalArrangement = Arrangement.Center
            ) {
                val allTypesSelected: Boolean by remember {
                    derivedStateOf {
                        filtersUiState.value.types.size == allEventTypes.size
                    }
                }
                EventTypeChip(R.string.all_event_types, allTypesSelected) {
                    val newTypes = if (allTypesSelected) {
                        emptyList()
                    } else {
                        allEventTypes
                    }
                    updateFilters(filtersUiState.value.copy(types = newTypes))
                }
                for (type in allEventTypes) {
                    val typeSelected: Boolean by remember { derivedStateOf { filtersUiState.value.types.contains(type) } }
                    EventTypeChip(eventTypeDisplayStringId(type), typeSelected) {
                        updateFilters(filtersUiState.value.run {
                            val newTypes = if (typeSelected) types - type else types + type
                            copy(types = newTypes)
                        })
                    }
                }
            }
        }
        val dateRangeEnabled: Boolean by remember { derivedStateOf { filtersUiState.value.dateRangeEnabled } }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalContentPadding),
            shape = CircleShape,
            color = Color.Transparent,
            onClick = {
                updateFilters(filtersUiState.value.run {
                    copy(dateRangeEnabled = !dateRangeEnabled)
                })
            }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(dateRangeEnabled, onCheckedChange = null)
                Text(stringResource(R.string.date_range_filter))
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            val maxScrollValue = snapshotFlow { scrollState.maxValue }.stateIn(
                this,
                SharingStarted.Eagerly,
                scrollState.maxValue
            )

            snapshotFlow { dateRangeEnabled }
                .drop(1)
                .filter { it }
                .produceIn(this)
                .receiveAsFlow()
                .onEach {
                    val currentMaxScrollValue = maxScrollValue.value
                    maxScrollValue.first { it != currentMaxScrollValue }
                }
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest {
                    scrollState.apply { animateScrollTo(maxValue) }
                }
        }

        if (dateRangeEnabled) {
            eventsTimeZone.value?.let { zone ->
                val dateRange: DateRange? by remember { derivedStateOf { filtersUiState.value.dateRange } }
                OutlinedButton(
                    onClick = showDateRangeDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontalContentPadding),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dateRange == null) MaterialTheme.colorScheme.error else Color.Unspecified)
                ) {
                    Text(dateRange?.let { range ->
                        val locale = LocalDefaultLocale.current
                        val formatter = remember(locale, zone) {
                            DateTimeFormatterBuilder()
                                .appendLocalized(FormatStyle.LONG, null)
                                .toFormatter(locale)
                                .withZone(zone)
                        }
                        stringResource(
                            R.string.date_range_string,
                            formatter.format(range.firstDayInstant),
                            formatter.format(range.lastDayInstant),
                            zone.getDisplayName(TextStyle.NARROW, locale)
                        )
                    } ?: stringResource(R.string.select_date_range))
                }
            }
        }
    }
}

@Composable
private fun EventTypeChip(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <EventType : Enum<EventType>> DateRangePickerDialogContent(
    initialFilters: FiltersUiState<EventType>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    eventsTimeZone: ZoneId,
    closeDialog: () -> Unit,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val initialDisplayMode: DisplayMode by remember {
        derivedStateOf {
            if (windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
                DisplayMode.Picker
            } else {
                DisplayMode.Input
            }
        }
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDate = initialFilters.dateRange?.firstDayInstant?.atZone(eventsTimeZone)?.toLocalDate(),
        initialSelectedEndDate = initialFilters.dateRange?.lastDayInstant?.atZone(eventsTimeZone)?.toLocalDate(),
        yearRange = DatePickerDefaults.YearRange.first..LocalDate.now(eventsTimeZone).year,
        selectableDates = DateRangePickerSelectableDates(eventsTimeZone),
        initialDisplayMode = initialDisplayMode
    )
    val confirmButtonEnabled by remember { derivedStateOf { state.selectedStartDateMillis != null && state.selectedEndDateMillis != null } }
    DatePickerDialog(
        onDismissRequest = closeDialog,
        confirmButton = {
            TextButton(
                onClick = {
                    val firstDayInstant =
                        state.getSelectedStartDate()?.atStartOfDay(eventsTimeZone)?.toInstant()
                    val instantAfterLastDay =
                        state.getSelectedEndDate()?.plusDays(1)?.atStartOfDay(eventsTimeZone)?.toInstant()
                    if (firstDayInstant != null && instantAfterLastDay != null) {
                        closeDialog()
                        updateFilters(initialFilters.copy(dateRange = DateRange(firstDayInstant, instantAfterLastDay)))
                    }
                },
                enabled = confirmButtonEnabled
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = closeDialog) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        DateRangePicker(
            state = state,
            headline = {
                Text(
                    stringResource(
                        R.string.date_range_picker_headline,
                        eventsTimeZone.getDisplayName(TextStyle.NARROW, LocalDefaultLocale.current)
                    ), Modifier.padding(start = 64.dp, end = 12.dp)
                )
            },
            modifier = Modifier
                .heightIn(max = 460.dp)
                .padding(top = Dimens.DialogContentPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class DateRangePickerSelectableDates(private val eventsTimeZone: ZoneId) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        // utcTimeMillis is not an actual time but a way to represent a date as a number
        // I.e. it's an epoch time at the start of day as-if it was in the UTC timezone
        // To get an actual time in the desired timezone we need to subtract its offset
        val actualUtcTimeMillis = if (eventsTimeZone.isUTC) {
            utcTimeMillis
        } else {
            val offsetSeconds = eventsTimeZone.rules.getOffset(Instant.ofEpochMilli(utcTimeMillis)).totalSeconds
            utcTimeMillis - (offsetSeconds * 1000)
        }
        return actualUtcTimeMillis < System.currentTimeMillis()
    }

    override fun isSelectableYear(year: Int): Boolean {
        return year <= LocalDate.now(eventsTimeZone).year
    }
}

private const val TAG = "BaseEventFilters"
