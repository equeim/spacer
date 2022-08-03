package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.CoronalMassEjection
import org.equeim.spacer.ui.components.ExpandableCard
import org.equeim.spacer.ui.utils.formatInteger
import java.time.Duration
import java.time.Instant

@Composable
fun CoronalMassEjectionDetails(event: CoronalMassEjection, formatTime: @Composable (Instant) -> String) =
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
        InstrumentsSection(event.instruments)

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
private fun EnlilModelCard(simulation: CoronalMassEjection.EnlilSimulation, formatTime: @Composable (Instant) -> String) {
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
