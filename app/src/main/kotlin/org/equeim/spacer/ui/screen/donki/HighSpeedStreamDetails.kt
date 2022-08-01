package org.equeim.spacer.ui.screen.donki

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.HighSpeedStream

@Composable
fun HighSpeedStreamDetails(event: HighSpeedStream) =
    InstrumentsSection(event.instruments)
