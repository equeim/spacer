// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.LocalAppSettings
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.collectAsStateWhenStarted
import org.equeim.spacer.ui.utils.defaultTimeZoneFlow
import org.equeim.spacer.ui.utils.determineEventTimeZone
import org.equeim.spacer.ui.utils.isUTC
import org.equeim.spacer.ui.utils.plus
import org.equeim.spacer.utils.getActivityOrThrow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Parcelize
object DonkiEventFiltersDialog : Destination {
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
    Dialog(
        title = stringResource(R.string.filters),
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DonkiEventFilters(
            contentPadding = PaddingValues(horizontal = Dimens.DialogContentPadding),
            filters = filters,
            updateFilters = updateFilters
        )
    }
}

@Composable
fun DonkiEventFiltersSideSheet(
    contentPadding: PaddingValues = PaddingValues(),
    filters: () -> DonkiRepository.EventFilters,
    updateFilters: (DonkiRepository.EventFilters) -> Unit,
) {
    DonkiEventFilters(
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
        filters = filters, updateFilters = updateFilters
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DonkiEventFilters(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    title: @Composable ColumnScope.() -> Unit = {},
    filters: () -> DonkiRepository.EventFilters,
    updateFilters: (DonkiRepository.EventFilters) -> Unit,
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
                stringResource(R.string.event_types),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                verticalArrangement = Arrangement.Center
            ) {
                val allTypesSelected: Boolean by remember {
                    derivedStateOf {
                        filters().types.containsAll(EventType.entries)
                    }
                }
                EventTypeChip(R.string.all_event_types, allTypesSelected) {
                    val newTypes = if (allTypesSelected) {
                        emptySet()
                    } else {
                        EventType.entries.toSet()
                    }
                    updateFilters(filters().copy(types = newTypes))
                }
                for (type in EventType.entries) {
                    val typeSelected: Boolean by remember { derivedStateOf { filters().types.contains(type) } }
                    EventTypeChip(type.displayStringResId, typeSelected) {
                        updateFilters(filters().run {
                            val newTypes = if (typeSelected) types - type else types + type
                            copy(types = newTypes)
                        })
                    }
                }
            }
        }
        var dateRangeEnabled: Boolean by rememberSaveable { mutableStateOf(filters().dateRange != null) }
        ListItem(
            leadingContent = {
                Checkbox(dateRangeEnabled, onCheckedChange = null)
            },
            headlineContent = { Text(stringResource(R.string.date_range_filter)) },
            modifier = Modifier
                .clickable {
                    dateRangeEnabled = !dateRangeEnabled
                    if (!dateRangeEnabled) {
                        updateFilters(filters().copy(dateRange = null))
                    }
                }
                .padding(Dimens.listItemHorizontalPadding(contentPadding))
        )
        if (dateRangeEnabled) {
            val defaultTimeZone: ZoneId by LocalContext.current.defaultTimeZoneFlow()
                .collectAsStateWhenStarted(ZoneId.systemDefault())
            val displayEventsTimeInUTC: Boolean? by LocalAppSettings.current.displayEventsTimeInUTC.flow()
                .collectAsStateWhenStarted(null)
            val eventsTimeZone: ZoneId? by remember {
                derivedStateOf {
                    displayEventsTimeInUTC?.let { determineEventTimeZone(defaultTimeZone, it) }
                }
            }

            eventsTimeZone?.let { zone ->
                LaunchedEffect(scrollState) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                var showDateRangeDialog: Boolean by rememberSaveable { mutableStateOf(false) }
                val dateRange: DonkiRepository.DateRange? by remember { derivedStateOf { filters().dateRange } }
                OutlinedButton(
                    onClick = { showDateRangeDialog = true },
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
                if (showDateRangeDialog) {
                    val locale = LocalDefaultLocale.current
                    DateRangePickerDialog(
                        initialDateRange = dateRange,
                        eventsTimeZone = zone,
                        defaultLocale = { locale },
                        onDismissRequest = { showDateRangeDialog = false },
                        onAcceptRequest = {
                            showDateRangeDialog = false
                            updateFilters(filters().copy(dateRange = it))
                        }
                    )
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
        colors = FilterChipDefaults.filterChipColors(
            iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun DateRangePickerDialog(
    initialDateRange: DonkiRepository.DateRange?,
    eventsTimeZone: ZoneId,
    defaultLocale: () -> Locale,
    onDismissRequest: () -> Unit,
    onAcceptRequest: (DonkiRepository.DateRange) -> Unit,
) {
    val heightSizeClass = calculateWindowSizeClass(LocalContext.current.getActivityOrThrow()).heightSizeClass
    val initialDisplayMode: DisplayMode by remember {
        derivedStateOf {
            if (heightSizeClass == WindowHeightSizeClass.Compact) {
                DisplayMode.Input
            } else {
                DisplayMode.Picker
            }
        }
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialDateRange?.firstDayInstant?.let {
            instantToPickerDate(it, eventsTimeZone)
        },
        initialSelectedEndDateMillis = initialDateRange?.lastDayInstant?.let {
            instantToPickerDate(it, eventsTimeZone)
        },
        yearRange = DatePickerDefaults.YearRange.first..LocalDate.now(eventsTimeZone).year,
        selectableDates = DateRangePickerSelectableDates(eventsTimeZone),
        initialDisplayMode = initialDisplayMode
    )
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                val firstDayInstant =
                    state.selectedStartDateMillis?.let { instantFromPickerDate(it, eventsTimeZone) }
                val instantAfterLastDay =
                    state.selectedEndDateMillis?.let { instantFromPickerDate(it, eventsTimeZone) }
                        ?.plus(Duration.ofDays(1))
                if (firstDayInstant != null && instantAfterLastDay != null) {
                    onAcceptRequest(DonkiRepository.DateRange(firstDayInstant, instantAfterLastDay))
                }
            }) {
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
                        eventsTimeZone.getDisplayName(TextStyle.NARROW, defaultLocale())
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
        return instantFromPickerDate(utcTimeMillis, eventsTimeZone) < Instant.now()
    }

    override fun isSelectableYear(year: Int): Boolean {
        return year <= LocalDate.now(eventsTimeZone).year
    }
}

private fun instantFromPickerDate(pickerTimeMillis: Long, eventsTimeZone: ZoneId): Instant {
    val actualUtcTimeMillis = if (eventsTimeZone.isUTC) {
        pickerTimeMillis
    } else {
        val offsetSeconds = eventsTimeZone.rules.getOffset(Instant.ofEpochMilli(pickerTimeMillis)).totalSeconds
        pickerTimeMillis - (offsetSeconds * 1000)
    }
    return Instant.ofEpochMilli(actualUtcTimeMillis)
}

private fun instantToPickerDate(instant: Instant, eventsTimeZone: ZoneId): Long {
    return if (eventsTimeZone.isUTC) {
        instant.toEpochMilli()
    } else {
        instant.atZone(eventsTimeZone).toLocalDateTime().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}

@Preview
@Composable
private fun DateRangePickerDialogPreview() {
    DateRangePickerDialog(
        initialDateRange = null,
        eventsTimeZone = ZoneId.systemDefault(),
        defaultLocale = { Locale.getDefault() },
        onDismissRequest = {},
        onAcceptRequest = {})
}

@Preview
@Composable
private fun DonkiEventFiltersSideSheetPreview() {
    DonkiEventFiltersSideSheet(
        filters = { DonkiRepository.EventFilters(types = EventType.entries.toSet() - EventType.GeomagneticStorm) },
        updateFilters = {}
    )
}

@Preview
@Composable
private fun DonkiEventFiltersDialogPreview() {
    DonkiEventFiltersDialog(
        filters = { DonkiRepository.EventFilters(types = EventType.entries.toSet() - EventType.GeomagneticStorm) },
        updateFilters = {},
        onDismissRequest = {}
    )
}
