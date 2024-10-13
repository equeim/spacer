// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStorm
import org.equeim.spacer.donki.data.events.network.json.HighSpeedStream
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShock
import org.equeim.spacer.donki.data.events.network.json.MagnetopauseCrossing
import org.equeim.spacer.donki.data.events.network.json.RadiationBeltEnhancement
import org.equeim.spacer.donki.data.events.network.json.SolarEnergeticParticle
import org.equeim.spacer.donki.data.events.network.json.SolarFlare
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.LinkedEventsList
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreenViewModel.ContentState.Empty
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreenViewModel.ContentState.ErrorPlaceholder
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreenViewModel.ContentState.EventData
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreenViewModel.ContentState.LoadingPlaceholder
import org.equeim.spacer.ui.screens.donki.events.details.cme.CmeAnalysisScreen
import org.equeim.spacer.ui.screens.donki.events.details.cme.CoronalMassEjectionDetails
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.Public
import org.equeim.spacer.ui.theme.SatelliteAlt
import java.time.format.DateTimeFormatter

@Parcelize
data class DonkiEventDetailsScreen(val eventId: EventId) : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) =
        ScreenContent(eventId, navController)
}

@Composable
private fun ScreenContent(eventId: EventId, navController: NavController<Destination>) {
    val model = viewModel {
        DonkiEventDetailsScreenViewModel(
            eventId,
            checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
        )
    }
    ScreenContent(model, navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(
    model: DonkiEventDetailsScreenViewModel,
    navController: NavController<Destination>,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = model::onActivityResumed)

    val snackbarHostState = remember { SnackbarHostState() }
    ShowSnackbarError(model, snackbarHostState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SubScreenTopAppBar(stringResource(R.string.event_details), navController::pop) },
        floatingActionButton = {
            val state = model.contentState.collectAsState()
            val eventLink by remember(model) {
                derivedStateOf {
                    (state.value as? EventData)?.event?.link
                }
            }
            eventLink?.let { link ->
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
        val pullToRefreshState = rememberPullToRefreshState()
        val showRefreshIndicator: Boolean by model.showRefreshIndicator.collectAsStateWithLifecycle()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .pullToRefresh(
                    isRefreshing = showRefreshIndicator,
                    state = pullToRefreshState,
                    onRefresh = model::refreshIfNotAlreadyLoading
                )
        )  {
            val contentState by model.contentState.collectAsState()
            Crossfade(contentState, label = "Content state crossfade") { state ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .consumeWindowInsets(contentPadding)
                        .padding(Dimens.ScreenContentPadding())
                ) {
                    when (state) {
                        is Empty -> Unit
                        is LoadingPlaceholder -> ScreenContentLoadingPlaceholder()
                        is ErrorPlaceholder -> ScreenContentErrorPlaceholder(state.error)
                        is EventData -> {
                            ScreenContentEventData(
                                state = state,
                                showEventDetailsScreen = {
                                    navController.navigate(DonkiEventDetailsScreen(it))
                                },
                                navigateToCmeAnalysisScreen = {
                                    navController.navigate(CmeAnalysisScreen(state.event.id, it.link))
                                }
                            )
                        }
                    }
                }
            }
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = showRefreshIndicator,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(contentPadding)
            )
        }
    }
}

@Composable
private fun ShowSnackbarError(
    model: DonkiEventDetailsScreenViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val snackbarError: String? by model.snackbarError.collectAsStateWithLifecycle()
    snackbarError?.let { error ->
        val context = LocalContext.current
        LaunchedEffect(snackbarHostState) {
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = context.getString(R.string.retry),
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                model.refreshIfNotAlreadyLoading()
            }
        }
    }
}

@Composable
private fun BoxScope.ScreenContentLoadingPlaceholder() {
    Text(
        text = stringResource(R.string.loading),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.align(Alignment.Center)
    )
}

@Composable
private fun BoxScope.ScreenContentErrorPlaceholder(error: String) {
    Text(
        text = error,
        modifier = Modifier.align(Alignment.Center),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun ScreenContentEventData(state: EventData, showEventDetailsScreen: (EventId) -> Unit, navigateToCmeAnalysisScreen: (CoronalMassEjection.Analysis) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.FloatingActionButtonPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Text(
            state.type,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
                state.dateTime,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(Dimens.SpacingMedium - Dimens.SpacingSmall))
        SpecificEventDetails(state.event, state::eventTimeFormatter, state::eventDateTimeFormatter, navigateToCmeAnalysisScreen)
        if (state.linkedEvents.isNotEmpty()) {
            LinkedEventsList(state.linkedEvents, showEventDetailsScreen)
        }
    }
}

@Composable
private fun SpecificEventDetails(
    event: Event,
    eventTimeFormatter: () -> DateTimeFormatter,
    eventDateTimeFormatter: () -> DateTimeFormatter,
    navigateToCmeAnalysisScreen: (CoronalMassEjection.Analysis) -> Unit
) {
    when (event) {
        is CoronalMassEjection -> CoronalMassEjectionDetails(event, eventDateTimeFormatter, navigateToCmeAnalysisScreen)
        is GeomagneticStorm -> GeomagneticStormDetails(event, eventTimeFormatter)
        is HighSpeedStream -> HighSpeedStreamDetails(event)
        is InterplanetaryShock -> InterplanetaryShockDetails(event)
        is MagnetopauseCrossing -> MagnetopauseCrossingDetails(event)
        is RadiationBeltEnhancement -> RadiationBeltEnhancementDetails(event)
        is SolarEnergeticParticle -> SolarEnergeticParticleDetails(event)
        is SolarFlare -> SolarFlareDetails(event, eventTimeFormatter())
    }
}

@Composable
fun InstrumentsSection(instruments: List<String>) {
    if (instruments.isNotEmpty()) {
        SectionHeader(stringResource(R.string.instruments))
        SelectionContainer {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                instruments.forEach { instrument ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SatelliteAlt,
                            contentDescription = stringResource(R.string.instruments)
                        )
                        Text(instrument, modifier = Modifier.padding(start = Dimens.SpacingLarge))
                    }
                }
            }
        }
    }
}

@Composable
fun LabelFieldPair(@StringRes labelResId: Int, field: String) {
    LabelFieldPair(stringResource(labelResId), field)
}

@Composable
fun LabelFieldPair(label: String, field: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.requiredWidth(150.dp),
            color = MaterialTheme.colorScheme.tertiary,
        )
        SelectionContainer(
            Modifier
                .padding(start = Dimens.SpacingSmall)
                .weight(1.0f)
        ) {
            Text(field)
        }
    }
}
