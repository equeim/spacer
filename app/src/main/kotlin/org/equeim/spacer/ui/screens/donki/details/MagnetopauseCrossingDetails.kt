package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.MagnetopauseCrossing

@Composable
fun MagnetopauseCrossingDetails(event: MagnetopauseCrossing) =
    InstrumentsSection(event.instruments)
