package org.equeim.spacer.ui.screen.donki

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.MagnetopauseCrossing

@Composable
fun MagnetopauseCrossingDetails(event: MagnetopauseCrossing) =
    InstrumentsSection(event.instruments)
