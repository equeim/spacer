package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.InterplanetaryShock

@Composable
fun InterplanetaryShockDetails(event: InterplanetaryShock) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelFieldPair(
            R.string.ips_location,
            event.location
        )
        InstrumentsSection(event.instruments)
    }
