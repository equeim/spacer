// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStorm
import org.equeim.spacer.ui.components.OutlinedCardWithPadding
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.components.SectionPlaceholder
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.rememberFloatFormatter
import java.time.format.DateTimeFormatter

@Composable
fun GeomagneticStormDetails(event: GeomagneticStorm, eventTimeFormatter: () -> DateTimeFormatter) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        if (event.kpIndexes.isNotEmpty()) {
            SectionHeader(stringResource(R.string.gst_kp_indexes))

            val floatFormatter = rememberFloatFormatter()
            val measurer = rememberTextMeasurer(1)
            val textStyle = MaterialTheme.typography.titleMedium
            val minWidthPixels = remember(measurer, floatFormatter) {
                measurer.measure(floatFormatter.format(9.99), style = textStyle).size.width
            }
            val minWidth = with(LocalDensity.current) { minWidthPixels.toDp() }

            for (kpIndex in event.kpIndexes) {
                OutlinedCardWithPadding(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            floatFormatter.format(kpIndex.kpIndex),
                            Modifier.widthIn(min = minWidth), style = textStyle
                        )
                        Text(eventTimeFormatter().format(kpIndex.observedTime), Modifier.weight(1.0f))
                        Text(kpIndex.source)
                    }
                }
            }
        } else {
            SectionPlaceholder(stringResource(R.string.gst_no_kp_indexes))
        }
    }
}
