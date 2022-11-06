// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.*
import org.equeim.spacer.donki.data.model.units.Angle
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.LocalNavController
import org.equeim.spacer.ui.components.Card
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.Public
import org.equeim.spacer.ui.theme.SatelliteAlt
import org.equeim.spacer.ui.utils.addBottomInsetUnless
import org.equeim.spacer.ui.utils.formatInteger
import org.equeim.spacer.ui.utils.hasBottomPadding
import org.equeim.spacer.ui.utils.plus
import java.text.DecimalFormat
import java.time.Instant
import kotlin.math.abs

@Parcelize
data class DonkiEventDetailsScreen(val eventId: EventId) : Destination {
    @Composable
    override fun Content() = ScreenContent(eventId)
}

@Composable
private fun ScreenContent(eventId: EventId) {
    val model = viewModel {
        DonkiEventDetailsScreenViewModel(
            eventId,
            checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
        )
    }
    ScreenContent(model)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ScreenContent(
    model: DonkiEventDetailsScreenViewModel
) {
    Scaffold(topBar = { SubScreenTopAppBar(stringResource(R.string.event_details)) },
        floatingActionButton = {
            val state = model.contentState.collectAsState()
            val eventLink by remember(model) {
                derivedStateOf {
                    (state.value as? DonkiEventDetailsScreenViewModel.ContentState.EventData)?.event?.link
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
                    onClick = { uriHandler.openUri(link) },
                    modifier = Modifier.padding(
                        bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }) { contentPadding ->
        val showRefreshIndicator by model.showRefreshIndicator.collectAsState()
        val pullRefreshState = rememberPullRefreshState(
            refreshing = showRefreshIndicator,
            onRefresh = model::refresh
        )
        Box(
            Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
            ) {
                val contentState by model.contentState.collectAsState()
                when (val state = contentState) {
                    is DonkiEventDetailsScreenViewModel.ContentState.LoadingPlaceholder -> ScreenContentLoadingPlaceholder()
                    is DonkiEventDetailsScreenViewModel.ContentState.ErrorPlaceholder -> ScreenContentErrorPlaceholder()
                    is DonkiEventDetailsScreenViewModel.ContentState.EventData -> {
                        ScreenContentEventData(
                            state,
                            contentPadding.hasBottomPadding
                        ) { model.formatTime(it) }
                    }
                }
            }
            PullRefreshIndicator(
                showRefreshIndicator,
                pullRefreshState,
                Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun BoxScope.ScreenContentLoadingPlaceholder() {
    Text(
        text = stringResource(R.string.loading),
        style = MaterialTheme.typography.h6,
        modifier = Modifier.align(Alignment.Center)
    )
}

@Composable
private fun BoxScope.ScreenContentErrorPlaceholder() {
    Text(
        text = stringResource(R.string.error),
        modifier = Modifier.align(Alignment.Center),
        style = MaterialTheme.typography.h6
    )
}

@Composable
private fun ScreenContentEventData(
    state: DonkiEventDetailsScreenViewModel.ContentState.EventData,
    screenHasBottomPadding: Boolean,
    formatTime: @Composable (Instant) -> String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                PaddingValues(Dimens.ScreenContentPadding)
                    .plus(
                        PaddingValues(
                            bottom = 64.dp
                        )
                    )
                    .addBottomInsetUnless(screenHasBottomPadding)
            ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Text(
            state.type,
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.h5
        )
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                state.dateTime,
                style = MaterialTheme.typography.h6
            )
        }
        Spacer(Modifier.height(Dimens.SpacingMedium - Dimens.SpacingSmall))
        SpecificEventDetails(state.event, formatTime)
        if (state.linkedEvents.isNotEmpty()) {
            SectionHeader(stringResource(R.string.linked_events))
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBetweenCards)
            ) {
                val navController = LocalNavController.current
                state.linkedEvents.forEach { linkedEvent ->
                    Card(
                        { navController.navigate(DonkiEventDetailsScreen(linkedEvent.id)) },
                        Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(text = linkedEvent.dateTime)
                            Text(
                                text = linkedEvent.type,
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(top = Dimens.SpacingSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecificEventDetails(event: Event, formatTime: @Composable (Instant) -> String) {
    when (event) {
        is CoronalMassEjection -> CoronalMassEjectionDetails(event, formatTime)
        is GeomagneticStorm -> GeomagneticStormDetails(event, formatTime)
        is HighSpeedStream -> HighSpeedStreamDetails(event)
        is InterplanetaryShock -> InterplanetaryShockDetails(event)
        is MagnetopauseCrossing -> MagnetopauseCrossingDetails(event)
        is RadiationBeltEnhancement -> RadiationBeltEnhancementDetails(event)
        is SolarEnergeticParticle -> SolarEnergeticParticleDetails(event)
        is SolarFlare -> SolarFlareDetails(event, formatTime)
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
            color = MaterialTheme.colors.secondary,
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

@Composable
fun formatCoordinates(latitude: Angle, longitude: Angle): String {
    return buildString {
        append(formatCoordinate(latitude, if (latitude.degrees >= 0.0f) "N" else "S"))
        append(' ')
        append(formatCoordinate(longitude, if (longitude.degrees >= 0.0f) "E" else "W"))
    }
}

@Composable
private fun formatCoordinate(coordinate: Angle, hemisphere: String): String {
    val absDegrees = abs(coordinate.degrees)
    val degrees = absDegrees.toInt()
    val minutesFloat = (absDegrees - degrees) * 60.0f
    val minutes = minutesFloat.toInt()
    val seconds = (minutesFloat - minutes) * 60.0f
    val secondsFormat = remember(LocalDefaultLocale.current) { DecimalFormat("00.###") }
    return buildString {
        append(formatInteger(degrees))
        append("°")
        append(formatInteger(minutes))
        append("′")
        append(secondsFormat.format(seconds))
        append("″")
        append(hemisphere)
    }
}
