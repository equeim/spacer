package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.SolarFlare
import java.time.Instant

@Composable
fun SolarFlareDetails(event: SolarFlare, formatTime: @Composable (Instant) -> String) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelFieldPair(
            R.string.flr_peak_time,
            formatTime(event.peakTime)
        )
        event.endTime?.let {
            LabelFieldPair(
                R.string.flr_end_time,
                formatTime(it)
            )
        }
        LabelFieldPair(R.string.flr_class, event.classType)
        event.sourceLocation?.let {
            LabelFieldPair(R.string.flr_source_location, formatCoordinates(it.latitude, it.longitude))
        }
        InstrumentsSection(event.instruments)
    }
