// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.SolarFlare
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.rememberCoordinatesFormatter
import java.time.format.DateTimeFormatter

@Composable
fun SolarFlareDetails(event: SolarFlare, eventTimeFormatter: DateTimeFormatter) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
        LabelFieldPair(
            R.string.flr_peak_time,
            eventTimeFormatter.format(event.peakTime)
        )
        event.endTime?.let {
            LabelFieldPair(
                R.string.flr_end_time,
                eventTimeFormatter.format(it)
            )
        }
        LabelFieldPair(R.string.flr_class, event.classType)
        event.sourceLocation?.let {
            val coordinatesFormatter = rememberCoordinatesFormatter()
            LabelFieldPair(R.string.flr_source_location, coordinatesFormatter.format(it.latitude, it.longitude))
        }
        InstrumentsSection(event.instruments)
    }
