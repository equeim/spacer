// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
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
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.components.Dialog
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.collectAsStateWhenStarted
import org.equeim.spacer.ui.utils.isUTC
import org.equeim.spacer.ui.utils.plus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun shouldShowFiltersAsDialog(): State<Boolean> {
    val windowSizeClass = rememberUpdatedState(Dimens.calculateWindowSizeClass())
    return remember {
        derivedStateOf {
            windowSizeClass.value.widthSizeClass == WindowWidthSizeClass.Compact
        }
    }
}

@Composable
fun HandleFiltersDialogVisibility(
    shouldShowFiltersAsDialog: State<Boolean>,
    dialogNavController: NavController<Destination>,
) {
    val shouldShowFiltersAsDialogFlow = remember { snapshotFlow { shouldShowFiltersAsDialog.value } }
    val showingFiltersDialog = remember(dialogNavController) {
        snapshotFlow { dialogNavController.backstack.entries.find { it.destination is DonkiEventFiltersDialog } != null }
    }
    val showingDateRangeDialog = remember(dialogNavController) {
        snapshotFlow { dialogNavController.backstack.entries.find { it.destination is DateRangePickerDialog } != null }
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
                        add(navEntry<Destination>(DonkiEventFiltersDialog))
                        addAll(dialogNavController.backstack.entries)
                    }
                } else {
                    Log.d(TAG, "Removing filters dialog from back stack")
                    dialogNavController.backstack.entries.filterNot { it.destination is DonkiEventFiltersDialog }
                }
            )
        }
    }
}

@Parcelize
object DonkiEventFiltersDialog : Destination {
    @Composable
    override fun Content(navController: NavController<Destination>, parentNavHostEntry: NavHostEntry<Destination>?) {
        val model: DonkiEventsScreenViewModel = viewModel(viewModelStoreOwner = checkNotNull(parentNavHostEntry))
        val filters = model.filters.collectAsStateWhenStarted()
        val eventsTimeZone = model.eventsTimeZone.collectAsStateWhenStarted()
        DonkiEventFiltersDialogContent(
            filters = filters,
            updateFilters = model::updateFilters,
            eventsTimeZone = eventsTimeZone,
            hideDialog = navController::pop,
            showDateRangeDialog = { navController.navigate(DateRangePickerDialog) },
        )
    }
}

@Composable
private fun DonkiEventFiltersDialogContent(
    filters: State<DonkiEventsScreenViewModel.Filters>,
    updateFilters: (DonkiEventsScreenViewModel.Filters) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    hideDialog: () -> Unit,
    showDateRangeDialog: () -> Unit,
) {
    Dialog(
        title = stringResource(R.string.filters),
        onDismissRequest = hideDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DonkiEventFilters(
            contentPadding = PaddingValues(horizontal = Dimens.DialogContentPadding),
            filters = filters,
            updateFilters = updateFilters,
            eventsTimeZone = eventsTimeZone,
            showDateRangeDialog = showDateRangeDialog,
        )
    }
}

@Composable
fun DonkiEventFiltersSideSheet(
    contentPadding: PaddingValues = PaddingValues(),
    filters: State<DonkiEventsScreenViewModel.Filters>,
    updateFilters: (DonkiEventsScreenViewModel.Filters) -> Unit,
    eventsTimeZone: State<ZoneId?>,
    dialogNavController: () -> NavController<Destination>,
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
        filters = filters,
        updateFilters = updateFilters,
        eventsTimeZone = eventsTimeZone,
        showDateRangeDialog = { dialogNavController().navigate(DateRangePickerDialog) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DonkiEventFilters(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    title: @Composable ColumnScope.() -> Unit = {},
    filters: State<DonkiEventsScreenViewModel.Filters>,
    updateFilters: (DonkiEventsScreenViewModel.Filters) -> Unit,
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
                        filters.value.types.containsAll(EventType.entries)
                    }
                }
                EventTypeChip(R.string.all_event_types, allTypesSelected) {
                    val newTypes = if (allTypesSelected) {
                        emptySet()
                    } else {
                        EventType.entries.toSet()
                    }
                    updateFilters(filters.value.copy(types = newTypes))
                }
                for (type in EventType.entries) {
                    val typeSelected: Boolean by remember { derivedStateOf { filters.value.types.contains(type) } }
                    EventTypeChip(type.displayStringResId, typeSelected) {
                        updateFilters(filters.value.run {
                            val newTypes = if (typeSelected) types - type else types + type
                            copy(types = newTypes)
                        })
                    }
                }
            }
        }
        val dateRangeEnabled: Boolean by remember { derivedStateOf { filters.value.dateRangeEnabled } }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalContentPadding),
            shape = CircleShape,
            onClick = {
                updateFilters(filters.value.run {
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
                val dateRange: DonkiRepository.DateRange? by remember { derivedStateOf { filters.value.dateRange } }
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

@Parcelize
private object DateRangePickerDialog : Destination {
    @Composable
    override fun Content(navController: NavController<Destination>, parentNavHostEntry: NavHostEntry<Destination>?) {
        val model: DonkiEventsScreenViewModel = viewModel(viewModelStoreOwner = checkNotNull(parentNavHostEntry))
        val filters: DonkiEventsScreenViewModel.Filters by model.filters.collectAsStateWhenStarted()
        val eventsTimeZone: ZoneId? by model.eventsTimeZone.collectAsStateWhenStarted()
        eventsTimeZone?.let { zone ->
            DateRangePickerDialogContent(
                initialDateRange = filters.dateRange,
                eventsTimeZone = zone,
                hideDialog = {
                    navController.pop()
                    /*if (navController.backstack.entries.isEmpty()) {
                        navController.navigate()
                    }*/
                },
                onAccepted = { model.updateFilters(model.filters.value.copy(dateRange = it)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialogContent(
    initialDateRange: DonkiRepository.DateRange?,
    eventsTimeZone: ZoneId,
    hideDialog: () -> Unit,
    onAccepted: (DonkiRepository.DateRange) -> Unit,
) {
    val heightSizeClass = Dimens.calculateWindowSizeClass().heightSizeClass
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
    val confirmButtonEnabled by remember { derivedStateOf { state.selectedStartDateMillis != null && state.selectedEndDateMillis != null } }
    DatePickerDialog(
        onDismissRequest = hideDialog,
        confirmButton = {
            TextButton(
                onClick = {
                    val firstDayInstant =
                        state.selectedStartDateMillis?.let { instantFromPickerDate(it, eventsTimeZone) }
                    val instantAfterLastDay =
                        state.selectedEndDateMillis?.let { instantFromPickerDate(it, eventsTimeZone) }
                            ?.plus(Duration.ofDays(1))
                    if (firstDayInstant != null && instantAfterLastDay != null) {
                        hideDialog()
                        onAccepted(DonkiRepository.DateRange(firstDayInstant, instantAfterLastDay))
                    }
                },
                enabled = confirmButtonEnabled
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = hideDialog) {
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
private fun DonkiEventFiltersDateRangePickerDialogPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        DateRangePickerDialogContent(
            initialDateRange = null,
            eventsTimeZone = ZoneId.systemDefault(),
            hideDialog = {},
            onAccepted = {},
        )
    }
}

@Preview
@Composable
private fun DonkiEventFiltersSideSheetPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        DonkiEventFiltersSideSheet(
            filters = remember {
                mutableStateOf(
                    DonkiEventsScreenViewModel.Filters(
                        types = EventType.entries.toSet() - EventType.GeomagneticStorm,
                        dateRangeEnabled = true
                    )
                )
            },
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            dialogNavController = { throw Error() }
        )
    }
}

@Preview
@Composable
private fun DonkiEventFiltersDialogPreview() {
    CompositionLocalProvider(LocalDefaultLocale provides Locale.getDefault()) {
        DonkiEventFiltersDialogContent(
            filters = remember {
                mutableStateOf(
                    DonkiEventsScreenViewModel.Filters(
                        types = EventType.entries.toSet() - EventType.GeomagneticStorm,
                        dateRange = DonkiRepository.DateRange(
                            firstDayInstant = LocalDate.now().minusDays(5).atStartOfDay(ZoneId.systemDefault())
                                .toInstant(),
                            instantAfterLastDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        ),
                        dateRangeEnabled = true
                    )
                )
            },
            updateFilters = {},
            eventsTimeZone = remember { mutableStateOf(ZoneId.systemDefault()) },
            hideDialog = {},
            showDateRangeDialog = {},
        )
    }
}

private const val TAG = "DonkiEventFilters"
