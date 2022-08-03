package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.GeomagneticStorm
import org.equeim.spacer.ui.components.Card
import org.equeim.spacer.ui.utils.formatInteger
import java.time.Instant

@Composable
fun GeomagneticStormDetails(event: GeomagneticStorm, formatTime: @Composable (Instant) -> String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.kpIndexes.isNotEmpty()) {
            SectionHeader(stringResource(R.string.gst_kp_indexes))
            event.kpIndexes.forEach { kpIndex ->
                Card(onClick = {}, Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(formatInteger(kpIndex.kpIndex), style = MaterialTheme.typography.h6)
                        Column(Modifier.weight(1.0f)) {
                            Text(formatTime(kpIndex.observedTime))
                            Text(kpIndex.source)
                        }
                    }
                }
            }
        } else {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                SectionHeader(stringResource(R.string.gst_no_kp_indexes))
            }
        }
    }
}
