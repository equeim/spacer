package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.HighSpeedStream
import org.equeim.spacer.ui.screens.donki.details.InstrumentsSection

@Composable
fun HighSpeedStreamDetails(event: HighSpeedStream) =
    InstrumentsSection(event.instruments)
