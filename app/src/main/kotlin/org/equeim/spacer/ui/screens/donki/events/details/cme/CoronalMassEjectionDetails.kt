// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details.cme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.ui.components.OutlinedCardWithPadding
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SectionPlaceholder
import org.equeim.spacer.ui.screens.donki.events.details.InstrumentsSection
import org.equeim.spacer.ui.screens.donki.events.details.LabelFieldPair
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.rememberCoordinatesFormatter
import java.time.format.DateTimeFormatter

@Composable
fun CoronalMassEjectionDetails(
    event: CoronalMassEjection,
    eventDateTimeFormatter: () -> DateTimeFormatter,
    navigateToCmeAnalysisScreen: (CoronalMassEjection.Analysis) -> Unit
) =
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        val coordinatesFormatter = rememberCoordinatesFormatter()

        if (event.note.isNotEmpty()) {
            SelectionContainer {
                Text(event.note)
            }
        }
        event.sourceLocation?.let {
            LabelFieldPair(
                R.string.cme_source_location,
                coordinatesFormatter.format(it.latitude, it.longitude)
            )
        }
        InstrumentsSection(event.instruments)

        if (event.cmeAnalyses.isNotEmpty()) {
            SectionHeader(stringResource(R.string.cme_analyses))
            for (analysis in event.cmeAnalyses) {
                OutlinedCardWithPadding(onClick = {
                    navigateToCmeAnalysisScreen(analysis)
                }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                        Text(eventDateTimeFormatter().format(analysis.submissionTime))
                    }
                }
            }
        } else {
            SectionPlaceholder(stringResource(R.string.cme_no_analyses))
        }
    }

