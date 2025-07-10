// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.getSelectedEndDate
import androidx.compose.material3.getSelectedStartDate
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
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
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.notifications.NotificationType
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.components.SwitchWithText
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.notifications.DonkiNotificationsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.theme.ComponentPreview
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.isUTC
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
fun <EventType : Enum<EventType>> EventFiltersSideSheet(
    contentPadding: PaddingValues,
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    allEventTypes: List<EventType>,
    eventTypeDisplayStringId: (EventType) -> Int,
    eventsTimeZone: State<ZoneId?>,
    showDateRangeDialog: () -> Unit
) {
    EventFilters(
        contentPadding = contentPadding,
        filtersUiState = filtersUiState,
        updateFilters = updateFilters,
        allEventTypes = allEventTypes,
        eventTypeDisplayStringId = eventTypeDisplayStringId,
        eventsTimeZone = eventsTimeZone,
        showDateRangeDialog = showDateRangeDialog,
        modifier = Modifier
            .fillMaxHeight()
            .width(256.dp)
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <EventType : Enum<EventType>> EventFiltersBottomSheet(
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    allEventTypes: List<EventType>,
    eventTypeDisplayStringId: (EventType) -> Int,
    eventsTimeZone: State<ZoneId?>,
    onDismissRequest: () -> Unit,
    showDateRangeDialog: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = state) {
        EventFilters(
            contentPadding = PaddingValues(),
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
fun shouldShowFiltersAsBottomSheet(): State<Boolean> {
    val windowSizeClass = rememberUpdatedState(currentWindowAdaptiveInfo().windowSizeClass)
    return remember {
        derivedStateOf {
            !windowSizeClass.value.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <EventType : Enum<EventType>> EventFilters(
    contentPadding: PaddingValues,
    filtersUiState: State<FiltersUiState<EventType>>,
    updateFilters: (FiltersUiState<EventType>) -> Unit,
    allEventTypes: List<EventType>,
    eventTypeDisplayStringId: (EventType) -> Int,
    eventsTimeZone: State<ZoneId?>,
    showDateRangeDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(bottom = Dimens.SpacingLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        val horizontalPadding = Dimens.ScreenContentPaddingHorizontal()

        Text(
            text = stringResource(R.string.filters),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = horizontalPadding)
        )

        Text(
            stringResource(R.string.filter_types),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = horizontalPadding)
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
        val dateRangeEnabled: Boolean by remember { derivedStateOf { filtersUiState.value.dateRangeEnabled } }

        SwitchWithText(
            checked = dateRangeEnabled,
            onCheckedChange = { updateFilters(filtersUiState.value.copy(dateRangeEnabled = it)) },
            text = R.string.date_range_filter,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )

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
                        .padding(horizontal = horizontalPadding),
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

@Preview
@Composable
private fun EventFiltersPreview() = ComponentPreview {
    EventFilters(
        contentPadding = PaddingValues(),
        filtersUiState = remember {
            mutableStateOf(
                FiltersUiState(
                    types = EventType.entries - EventType.MagnetopauseCrossing,
                    dateRange = null,
                    dateRangeEnabled = true
                )
            )
        },
        updateFilters = {},
        allEventTypes = EventType.entries,
        eventTypeDisplayStringId = { it.displayStringResId },
        eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
        showDateRangeDialog = {}
    )
}

@Preview
@Composable
private fun NotificationFiltersPreview() = ComponentPreview {
    EventFilters(
        contentPadding = PaddingValues(),
        filtersUiState = remember {
            mutableStateOf(
                FiltersUiState(
                    types = NotificationType.entries - NotificationType.MagnetopauseCrossing,
                    dateRange = null,
                    dateRangeEnabled = true
                )
            )
        },
        updateFilters = {},
        allEventTypes = NotificationType.entries,
        eventTypeDisplayStringId = { it.displayStringResId },
        eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
        showDateRangeDialog = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialDateRange: DateRange?,
    updateDateRange: (DateRange) -> Unit,
    eventsTimeZone: ZoneId,
    onDismissRequest: () -> Unit,
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
        initialSelectedStartDate = initialDateRange?.firstDayInstant?.atZone(eventsTimeZone)?.toLocalDate(),
        initialSelectedEndDate = initialDateRange?.lastDayInstant?.atZone(eventsTimeZone)?.toLocalDate(),
        yearRange = DatePickerDefaults.YearRange.first..LocalDate.now(eventsTimeZone).year,
        selectableDates = DateRangePickerSelectableDates(eventsTimeZone),
        initialDisplayMode = initialDisplayMode
    )
    val confirmButtonEnabled by remember { derivedStateOf { state.selectedStartDateMillis != null && state.selectedEndDateMillis != null } }
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val firstDayInstant =
                        state.getSelectedStartDate()?.atStartOfDay(eventsTimeZone)?.toInstant()
                    val instantAfterLastDay =
                        state.getSelectedEndDate()?.plusDays(1)?.atStartOfDay(eventsTimeZone)?.toInstant()
                    if (firstDayInstant != null && instantAfterLastDay != null) {
                        updateDateRange(DateRange(firstDayInstant, instantAfterLastDay))
                        onDismissRequest()
                    }
                },
                enabled = confirmButtonEnabled
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
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


@Preview
@Composable
private fun DateRangePickerDialogPreview() {
    ComponentPreview {
        DateRangePickerDialog(
            initialDateRange = null,
            updateDateRange = {},
            eventsTimeZone = ZoneId.systemDefault(),
            onDismissRequest = {}
        )
    }
}
