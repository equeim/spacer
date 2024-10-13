// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details.cme

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.ui.components.ExpandableCard
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SectionPlaceholder
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreenViewModel
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreenViewModel.ContentState
import org.equeim.spacer.ui.screens.donki.events.details.LabelFieldPair
import org.equeim.spacer.ui.screens.previous
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.Public
import org.equeim.spacer.ui.utils.rememberCoordinatesFormatter
import org.equeim.spacer.ui.utils.rememberIntegerFormatter
import java.text.NumberFormat
import java.time.Duration
import java.time.format.DateTimeFormatter

@Parcelize
data class CmeAnalysisScreen(val eventId: EventId, val cmeLink: String) : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) {
        val model: DonkiEventDetailsScreenViewModel =
            viewModel(viewModelStoreOwner = navHostEntries.previous) {
                DonkiEventDetailsScreenViewModel(
                    eventId,
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
                )
            }
        val eventState: ContentState by model.contentState.collectAsStateWithLifecycle()
        val state: State? by remember {
            derivedStateOf {
                when (val s = eventState) {
                    is ContentState.EventData -> if (s.event is CoronalMassEjection) {
                        val analysis = s.event.cmeAnalyses.find { it.link == cmeLink }
                        if (analysis != null) {
                            State.AnalysisData(
                                analysis = analysis,
                                eventTimeFormatter = s.eventTimeFormatter,
                                eventDateTimeFormatter = s.eventDateTimeFormatter
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    is ContentState.Empty, is ContentState.LoadingPlaceholder -> State.Loading
                    is ContentState.ErrorPlaceholder -> null
                }
            }
        }
        when (val s = state) {
            null -> SideEffect { navController.pop() }
            else -> ScreenContent(s, navController::pop)
        }
    }
}

private sealed interface State {
    data object Loading : State
    data class AnalysisData(
        val analysis: CoronalMassEjection.Analysis,
        val eventTimeFormatter: DateTimeFormatter,
        val eventDateTimeFormatter: DateTimeFormatter
    ) : State
}

@Composable
private fun ScreenContent(
    state: State,
    popBackStack: () -> Unit
) {
    Scaffold(
        topBar = {
            SubScreenTopAppBar(
                stringResource(R.string.cme_analysis),
                popBackStack
            )
        },
        floatingActionButton = {
            val linkOrNull: String? by remember { derivedStateOf { (state as? State.AnalysisData)?.analysis?.link } }
            linkOrNull?.let { link ->
                val uriHandler = LocalUriHandler.current
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.go_to_donki_website)) },
                    icon = {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = stringResource(R.string.go_to_donki_website)
                        )
                    },
                    onClick = { uriHandler.openUri(link) }
                )
            }
        }
    ) { contentPadding ->
        Crossfade(state, label = "Content state crossfade") { state ->
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .padding(Dimens.ScreenContentPadding())
                    .padding(bottom = Dimens.FloatingActionButtonPadding)
            ) {
                when (state) {
                    is State.AnalysisData -> ScreenContentAnalysis(
                        state.analysis,
                        state::eventTimeFormatter,
                        state::eventDateTimeFormatter
                    )

                    is State.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun ScreenContentAnalysis(
    analysis: CoronalMassEjection.Analysis,
    eventTimeFormatter: () -> DateTimeFormatter,
    eventDateTimeFormatter: () -> DateTimeFormatter
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        val coordinatesFormatter = rememberCoordinatesFormatter()

        Text(
            eventDateTimeFormatter().format(analysis.submissionTime),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (analysis.note.isNotEmpty()) {
            SelectionContainer {
                Text(
                    analysis.note,
                    Modifier.padding(vertical = Dimens.SpacingMedium - Dimens.SpacingSmall)
                )
            }
        }

        LabelFieldPair(
            R.string.cme_data_level,
            stringResource(analysis.levelOfData.displayStringResId)
        )

        LabelFieldPair(R.string.cme_measurement_technique, analysis.measurementTechnique)

        analysis.measurementType?.let {
            LabelFieldPair(R.string.cme_measurement_type, stringResource(it.displayStringResId))
        }

        LabelFieldPair(
            R.string.cme_prime,
            stringResource(if (analysis.isMostAccurate) R.string.yes else R.string.no)
        )

        analysis.imageType?.let {
            LabelFieldPair(R.string.cme_image_type, it)
        }

        analysis.type?.let {
            LabelFieldPair(R.string.cme_type, stringResource(it.displayStringResId))
        }

        analysis.speed?.let {
            LabelFieldPair(
                R.string.cme_speed,
                stringResource(R.string.cme_speed_value, it.toKilometersPerSecond())
            )
        }

        analysis.speedMeasuredAtHeight?.let {
            LabelFieldPair(
                R.string.cme_speed_measured_at_height,
                stringResource(R.string.cme_speed_measured_at_height_value, it)
            )
        }

        analysis.time215?.let {
            LabelFieldPair(R.string.cme_time215, eventTimeFormatter().format(it))
        }

        if (analysis.latitude != null && analysis.longitude != null) {
            LabelFieldPair(
                R.string.cme_direction,
                coordinatesFormatter.format(analysis.latitude!!, analysis.longitude!!)
            )
        }

        analysis.halfWidth?.let {
            LabelFieldPair(
                R.string.cme_half_angular_width,
                stringResource(R.string.degrees_value, it.degrees)
            )
        }

        analysis.minorHalfWidth?.let {
            LabelFieldPair(
                R.string.cme_minor_half_angular_width,
                stringResource(R.string.degrees_value, it.degrees)
            )
        }

        analysis.tilt?.let {
            LabelFieldPair(
                R.string.cme_tilt,
                stringResource(R.string.degrees_value, it.degrees)
            )
        }

        Spacer(Modifier.height(Dimens.SpacingMedium - Dimens.SpacingSmall))

        if (analysis.enlilSimulations.isNotEmpty()) {
            SectionHeader(stringResource(R.string.enlil_models))
            val integerFormatter = rememberIntegerFormatter()
            analysis.enlilSimulations.forEach { simulation ->
                EnlilModelCard(simulation, integerFormatter, eventDateTimeFormatter())
            }
        } else {
            SectionPlaceholder(stringResource(R.string.enlil_no_models))
        }
    }
}

@Composable
private fun EnlilModelCard(
    simulation: CoronalMassEjection.EnlilSimulation,
    integerFormatter: NumberFormat,
    eventDateTimeFormatter: DateTimeFormatter
) {
    ExpandableCard(
        Modifier.fillMaxWidth(),
        content = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                Text(
                    stringResource(
                        R.string.enlil_description,
                        eventDateTimeFormatter.format(simulation.modelCompletionTime),
                        simulation.au
                    )
                )
                if (simulation.estimatedShockArrivalTime != null) {
                    Text(
                        stringResource(
                            if (simulation.isEarthGlancingBlow == true) {
                                R.string.earch_impact_predicted_glancing
                            } else {
                                R.string.earch_impact_predicted
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        expandedContent = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                if (simulation.estimatedShockArrivalTime == null && simulation.estimatedDuration == null) {
                    SectionPlaceholder(
                        stringResource(R.string.enlil_earth_no_impact),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    SectionHeader(
                        stringResource(R.string.enlil_earth_impact),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (simulation.isEarthGlancingBlow == true) {
                        Text(
                            stringResource(R.string.glancing_blow),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    simulation.estimatedShockArrivalTime?.let {
                        LabelFieldPair(
                            R.string.enlil_earth_shock_arrival_time,
                            eventDateTimeFormatter.format(it)
                        )
                    }
                    simulation.estimatedDuration?.let {
                        LabelFieldPair(
                            R.string.enlil_earth_duration,
                            stringResource(
                                R.string.enlil_earth_duration_value,
                                it.seconds.toFloat() / Duration.ofHours(1).seconds.toFloat()
                            )
                        )
                    }
                }
                simulation.kp90?.let {
                    LabelFieldPair(R.string.enlil_kp_90, integerFormatter.format(it))
                }
                simulation.kp135?.let {
                    LabelFieldPair(R.string.enlil_kp_135, integerFormatter.format(it))
                }
                simulation.kp180?.let {
                    LabelFieldPair(R.string.enlil_kp_180, integerFormatter.format(it))
                }
                if (simulation.impacts.isNotEmpty()) {
                    SectionHeader(
                        stringResource(R.string.enlil_other_impacts),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    for (impact in simulation.impacts) {
                        val field = if (impact.isGlancingBlow) {
                            "${eventDateTimeFormatter.format(impact.arrivalTime)}\n${
                                stringResource(
                                    R.string.glancing_blow
                                )
                            }"
                        } else {
                            eventDateTimeFormatter.format(impact.arrivalTime)
                        }
                        LabelFieldPair(impact.location, field)
                    }
                } else {
                    SectionPlaceholder(
                        stringResource(R.string.enlil_no_other_impacts),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                val uriHandler = LocalUriHandler.current
                OutlinedButton({ uriHandler.openUri(simulation.link) }) {
                    Text(stringResource(R.string.enlil_website))
                }
            }
        }
    )
}
