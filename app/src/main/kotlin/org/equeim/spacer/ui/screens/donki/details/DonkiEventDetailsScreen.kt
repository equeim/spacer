package org.equeim.spacer.ui.screens.donki.details

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.ui.LocalDefaultLocale
import org.equeim.spacer.ui.LocalNavController
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.*
import org.equeim.spacer.donki.data.model.units.Angle
import org.equeim.spacer.ui.components.Card
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.Public
import org.equeim.spacer.ui.theme.SatelliteAlt
import org.equeim.spacer.ui.utils.addBottomInsetUnless
import org.equeim.spacer.ui.utils.formatInteger
import org.equeim.spacer.ui.utils.hasBottomPadding
import org.equeim.spacer.ui.utils.plus
import org.equeim.spacer.utils.getApplicationOrThrow
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
    val context = LocalContext.current
    @Suppress("UNCHECKED_CAST")
    val model =
        viewModel<DonkiEventDetailsScreenViewModel>(factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                DonkiEventDetailsScreenViewModel(eventId, context.getApplicationOrThrow()) as T
        })
    ScreenContent(model)
}

@Composable
private fun ScreenContent(
    model: DonkiEventDetailsScreenViewModel
) {
    Scaffold(topBar = { SubScreenTopAppBar(stringResource(R.string.event_details)) },
        floatingActionButton = {
            val contentUiState = model.contentUiState
            if (contentUiState is DonkiEventDetailsScreenViewModel.ContentUiState.Loaded) {
                val uriHandler = LocalUriHandler.current
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.go_to_donki_website)) },
                    icon = {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = stringResource(R.string.go_to_donki_website)
                        )
                    },
                    onClick = { uriHandler.openUri(contentUiState.event.link) },
                    modifier = Modifier.padding(
                        bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }) { contentPadding ->
        val showRefreshIndicator by remember(model) {
            derivedStateOf {
                model.contentUiState is DonkiEventDetailsScreenViewModel.ContentUiState.Loading ||
                        model.refreshing
            }
        }
        SwipeRefresh(
            rememberSwipeRefreshState(showRefreshIndicator),
            onRefresh = { model.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
            ) {
                when (val contentUiState = model.contentUiState) {
                    is DonkiEventDetailsScreenViewModel.ContentUiState.Loading -> ScreenContentLoadingPlaceholder()
                    is DonkiEventDetailsScreenViewModel.ContentUiState.Error -> ScreenContentErrorPlaceholder()
                    is DonkiEventDetailsScreenViewModel.ContentUiState.Loaded -> {
                        ScreenContentLoaded(contentUiState, contentPadding.hasBottomPadding) { model.formatTime(it) }
                    }
                }
            }
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
private fun ScreenContentLoaded(
    state: DonkiEventDetailsScreenViewModel.ContentUiState.Loaded,
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
        SpecificEventDetails(state.event, formatTime)
        if (state.linkedEvents.isNotEmpty()) {
            SectionHeader(stringResource(R.string.linked_events))
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
                            modifier = Modifier.padding(top = 8.dp)
                        )
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
fun SectionHeader(title: String, style: TextStyle = MaterialTheme.typography.h6) {
    Text(
        title,
        color = MaterialTheme.colors.primary,
        style = style,
        modifier = Modifier.padding(top = 8.dp)
    )
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
                        Text(instrument, modifier = Modifier.padding(start = 16.dp))
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
                .padding(start = 8.dp)
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
