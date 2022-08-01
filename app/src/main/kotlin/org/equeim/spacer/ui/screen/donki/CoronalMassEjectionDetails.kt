package org.equeim.spacer.ui.screen.donki

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.CoronalMassEjection
import org.equeim.spacer.donki.data.model.units.Angle
import org.equeim.spacer.ui.components.ExpandableCard
import org.equeim.spacer.ui.theme.SatelliteAlt
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.abs

@Composable
fun CoronalMassEjectionDetails(event: CoronalMassEjection, formatTime: (Instant) -> String) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.note.isNotEmpty()) {
            SelectionContainer {
                Text(event.note)
            }
        }
        event.sourceLocation?.let {
            LabelFieldPair(
                R.string.cme_source_location,
                formatCoordinates(it.latitude, it.longitude)
            )
        }
        if (event.instruments.isNotEmpty()) {
            SectionHeader(stringResource(R.string.instruments))
            SelectionContainer {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    event.instruments.forEach { instrument ->
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

        val analysis = remember(event) { event.cmeAnalyses.find { it.isMostAccurate } }
        if (analysis != null) {
            SectionHeader(stringResource(R.string.cme_analysis))
            if (analysis.note.isNotEmpty()) {
                SelectionContainer {
                    Text(analysis.note)
                }
            }
            LabelFieldPair(R.string.cme_data_level, formatInteger(analysis.levelOfData))
            analysis.speed?.let {
                LabelFieldPair(R.string.cme_speed, stringResource(R.string.cme_speed_value, it.toKilometersPerSecond()))
            }
            LabelFieldPair(R.string.cme_type, analysis.type)
            if (analysis.latitude != null && analysis.longitude != null) {
                LabelFieldPair(
                    R.string.cme_direction,
                    formatCoordinates(analysis.latitude!!, analysis.longitude!!)
                )
            }
            analysis.halfAngle?.let {
                LabelFieldPair(
                    R.string.cme_half_angular_width,
                    stringResource(R.string.cme_half_angular_width_value, it.degrees)
                )
            }
            analysis.time215?.let {
                LabelFieldPair(R.string.cme_time215, formatTime(it))
            }
            val uriHandler = LocalUriHandler.current
            OutlinedButton(
                { uriHandler.openUri(analysis.link) }
            ) {
                Text(stringResource(R.string.cme_website))
            }
            if (analysis.enlilSimulations.isNotEmpty()) {
                SectionHeader(stringResource(R.string.enlil_models))
                analysis.enlilSimulations.forEach { simulation ->
                    EnlilModelCard(simulation, formatTime)
                }
            } else {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        stringResource(R.string.enlil_no_models),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.h6
                    )
                }
            }
        } else {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    stringResource(R.string.cme_no_analysis),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.h6
                )
            }
        }
    }

@Composable
private fun EnlilModelCard(simulation: CoronalMassEjection.EnlilSimulation, formatTime: (Instant) -> String) {
    ExpandableCard(
        Modifier.fillMaxWidth(),
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.enlil_description, formatTime(simulation.modelCompletionTime), simulation.au))
            }
        },
        expandedContent = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (simulation.estimatedShockArrivalTime == null && simulation.estimatedDuration == null) {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Text(stringResource(R.string.enlil_earth_no_impact))
                    }
                } else {
                    SectionHeader(stringResource(R.string.enlil_earth_impact), MaterialTheme.typography.body1)
                    simulation.estimatedShockArrivalTime?.let {
                        LabelFieldPair(R.string.enlil_earth_shock_arrival_time, formatTime(it))
                    }
                    simulation.estimatedDuration?.let {
                        LabelFieldPair(R.string.enlil_earth_duration, stringResource(R.string.enlil_earth_duration_value, it.seconds.toFloat() / Duration.ofHours(1).seconds.toFloat()))
                    }
                }
                simulation.kp90?.let {
                    LabelFieldPair(R.string.enlil_kp_90, formatInteger(it))
                }
                simulation.kp135?.let {
                    LabelFieldPair(R.string.enlil_kp_135, formatInteger(it))
                }
                simulation.kp180?.let {
                    LabelFieldPair(R.string.enlil_kp_180, formatInteger(it))
                }
                if (simulation.impacts.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.enlil_other_impacts), MaterialTheme.typography.body1)
                    simulation.impacts.forEach { impact ->
                        LabelFieldPair(impact.location, formatTime(impact.arrivalTime))
                    }
                } else {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Text(stringResource(R.string.enlil_no_other_impacts))
                    }
                }
                val uriHandler = LocalUriHandler.current
                OutlinedButton({ uriHandler.openUri(simulation.link) }) {
                    Text(stringResource(R.string.enlil_website))
                }
            }
        }
    )
}

@Composable
private fun LabelFieldPair(@StringRes labelResId: Int, field: String) {
    LabelFieldPair(stringResource(labelResId), field)
}

@Composable
private fun LabelFieldPair(label: String, field: String) {
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
private fun formatCoordinates(latitude: Angle, longitude: Angle): String {
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
    val secondsFormat = remember(Locale.getDefault()) { DecimalFormat("00.###") }
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

@Composable
private fun formatInteger(integer: Int): String {
    val numberFormat = remember(Locale.getDefault()) { NumberFormat.getIntegerInstance() }
    return numberFormat.format(integer)
}
